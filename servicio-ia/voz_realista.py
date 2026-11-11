"""Servicio de voz realista para Zero (Chatterbox Multilingual).

Funciona aparte del servicio principal porque Chatterbox necesita Python 3.11 y su propia
versión de PyTorch. Java lo arranca solo si está instalado (INSTALAR_VOZ_REALISTA.bat).

  GET  /estado      -> {"listo": true, "dispositivo": "gráfica"}
  POST /sintetizar  JSON {"texto", "referencia"} -> audio WAV

"referencia" es el nombre de un .wav dentro de la carpeta voces-clonadas (o null para la voz
por defecto de Chatterbox). Con 10-15 segundos de una voz clara basta.
"""
import io
import json
import os
import sys
import threading
import time
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import numpy as np

PUERTO = 47615
BASE = os.path.dirname(os.path.abspath(__file__))
CARPETA_REFERENCIAS = os.path.join(BASE, "voces-clonadas")

IDIOMA = "es"
EXAGERACION = 0.5   # expresividad: 0.25 = tranquila, 0.5 = normal, 0.8 = muy expresiva
TEMPERATURA = 0.8

# ---------------- AJUSTES DE VELOCIDAD (prueba y mira el registro datos/voz-realista.log) ----------------
# Media precisión: calcula con números de 16 bits en vez de 32. Las RTX 50 lo hacen mucho más rápido.
# Si la voz suena rara o con ruidos, ponlo a False.
MEDIA_PRECISION = True
# CFG: cuánto se ciñe a la voz de referencia. Con CUALQUIER valor mayor que 0, Chatterbox hace el
# trabajo DOS veces (una "guiada" y otra "libre" para compararlas). Con 0 va casi el doble de rápido,
# a cambio de algo menos de parecido con la referencia. Prueba 0.5 (calidad) y 0 (velocidad).
CFG = 0.5

MODELO = None
DISPOSITIVO = ""
_condiciones_por_defecto = None
_referencia_actual = None
_candado = threading.Lock()


def cargar():
    import torch
    from chatterbox.mtl_tts import ChatterboxMultilingualTTS

    dispositivo = "cuda" if torch.cuda.is_available() else "cpu"
    try:
        modelo = ChatterboxMultilingualTTS.from_pretrained(device=dispositivo, t3_model="v3")
    except TypeError:  # versiones antiguas no tienen el parámetro t3_model
        modelo = ChatterboxMultilingualTTS.from_pretrained(device=dispositivo)
    return modelo, "gráfica" if dispositivo == "cuda" else "procesador"


def _ruta_referencia(nombre):
    if not nombre:
        return None
    nombre = os.path.basename(nombre)  # evita rutas raras como ..\..\
    ruta = os.path.join(CARPETA_REFERENCIAS, nombre)
    if not os.path.exists(ruta):
        raise FileNotFoundError(f"No existe la voz de referencia voces-clonadas/{nombre}")
    return ruta


def _preparar_voz(referencia):
    """Analiza la voz de referencia solo cuando cambia (es lo más lento).
    Devuelve argumentos extra para generate() si hay que pasar la referencia cada vez."""
    global _referencia_actual
    ruta = _ruta_referencia(referencia)
    if not hasattr(MODELO, "prepare_conditionals"):
        return {"audio_prompt_path": ruta} if ruta else {}
    if referencia != _referencia_actual:
        if ruta is None:
            MODELO.conds = _condiciones_por_defecto
        else:
            MODELO.prepare_conditionals(ruta, exaggeration=EXAGERACION)
        _referencia_actual = referencia
    return {}


def _generar(texto, extra, media_precision):
    import contextlib

    import torch

    contexto = (torch.autocast(device_type="cuda", dtype=torch.bfloat16)
                if media_precision and DISPOSITIVO == "gráfica" else contextlib.nullcontext())
    with contexto:
        try:
            return MODELO.generate(texto, language_id=IDIOMA, exaggeration=EXAGERACION,
                                   cfg_weight=CFG, temperature=TEMPERATURA, **extra)
        except TypeError:  # versión con menos parámetros
            return MODELO.generate(texto, language_id=IDIOMA, **extra)


def sintetizar(texto, referencia):
    global MEDIA_PRECISION
    import torch

    inicio = time.perf_counter()
    with _candado, torch.inference_mode():
        extra = _preparar_voz(referencia)
        try:
            onda = _generar(texto, extra, MEDIA_PRECISION)
        except RuntimeError as e:
            if not MEDIA_PRECISION:
                raise
            print(f"[!] La media precisión ha fallado ({e}). Vuelvo a precisión normal.", flush=True)
            MEDIA_PRECISION = False
            onda = _generar(texto, extra, False)
    onda = onda.float()

    # Registro de velocidad: RTF = segundos que tarda / segundos de audio. Menos de 1 = más rápido que hablar.
    tardado = time.perf_counter() - inicio
    duracion = onda.shape[-1] / MODELO.sr
    print(f"[t] {tardado:.1f}s para {duracion:.1f}s de audio (RTF {tardado / max(duracion, 0.01):.2f}) "
          f"| {len(texto)} letras | media precisión={MEDIA_PRECISION} cfg={CFG}", flush=True)

    audio = onda.squeeze().detach().cpu().numpy()
    audio = (np.clip(audio, -1.0, 1.0) * 32767).astype(np.int16)
    salida = io.BytesIO()
    with wave.open(salida, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(MODELO.sr)
        w.writeframes(audio.tobytes())
    return salida.getvalue()


class Manejador(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _responder(self, codigo, cuerpo: bytes, tipo):
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(cuerpo)))
        self.end_headers()
        self.wfile.write(cuerpo)

    def _json(self, codigo, datos):
        self._responder(codigo, json.dumps(datos, ensure_ascii=False).encode("utf-8"), "application/json")

    def do_GET(self):
        if self.path == "/estado":
            self._json(200, {"listo": True, "dispositivo": DISPOSITIVO})
        else:
            self._json(404, {"error": "no existe"})

    def do_POST(self):
        try:
            if self.path != "/sintetizar":
                self._json(404, {"error": "no existe"})
                return
            d = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            self._responder(200, sintetizar(d.get("texto", ""), d.get("referencia")), "audio/wav")
        except Exception as e:
            print(f"[!] {e}", flush=True)
            self._json(500, {"error": str(e)})


if __name__ == "__main__":
    os.makedirs(CARPETA_REFERENCIAS, exist_ok=True)
    print("[i] Cargando Chatterbox...", flush=True)
    MODELO, DISPOSITIVO = cargar()
    _condiciones_por_defecto = getattr(MODELO, "conds", None)
    print(f"[i] Chatterbox en {DISPOSITIVO}", flush=True)
    # Calentamiento: la primera síntesis siempre es lenta (la gráfica prepara sus cálculos).
    # Se hace aquí, al arrancar, para que tu primera pregunta no lo pague.
    print("[i] Calentando la voz...", flush=True)
    sintetizar("Hola, soy Zero.", None)
    # PyTorch se guarda memoria de la gráfica "por si acaso". La devolvemos para que el cerebro
    # (que se carga justo después) tenga sitio.
    import torch
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    if "--precargar" in sys.argv:
        print("[i] Voz realista lista.", flush=True)
        sys.exit(0)
    print(f"[i] Servicio de voz realista en http://127.0.0.1:{PUERTO}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PUERTO), Manejador).serve_forever()
