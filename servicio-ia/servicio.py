"""Servicio de IA local para Zero.

Hace las dos cosas que necesitan la tarjeta gráfica y que en Java serían muy complicadas:
  - Oído: voz -> texto con Whisper (faster-whisper)
  - Voz:  texto -> voz con Piper

Java lo arranca solo y habla con él por HTTP en http://127.0.0.1:47614
  GET  /estado       -> {"listo": true, "oido": "large-v3-turbo · gráfica"}
  POST /transcribir  cuerpo: audio PCM 16 bits, 16 kHz, mono  -> {"texto": "..."}
  POST /sintetizar   JSON {"texto", "modelo", "hablante", "velocidad"} -> audio WAV

No hace falta tocar este archivo.
"""
import glob
import io
import json
import os
import re
import sys
import threading
import urllib.request
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import numpy as np

PUERTO = 47614
BASE = os.path.dirname(os.path.abspath(__file__))
CARPETA_VOCES = os.path.join(BASE, "voces")
VOCES_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/main/"
VOZ_POR_DEFECTO = "es/es_ES/sharvard/medium/es_ES-sharvard-medium"

WHISPER_MODELO = "large-v3-turbo"   # con gráfica
WHISPER_MODELO_CPU = "small"        # si tiene que usar el procesador
IDIOMA = "es"
PROMPT_INICIAL = "Zero, recuérdame mañana a las 5 llamar al médico."


def registrar_dlls_cuda():
    """En Windows, hace visibles las DLL de CUDA instaladas con pip."""
    if sys.platform != "win32":
        return
    for base in sys.path:
        for carpeta in glob.glob(os.path.join(base, "nvidia", "*", "bin")):
            try:
                os.add_dll_directory(carpeta)
                os.environ["PATH"] = carpeta + os.pathsep + os.environ["PATH"]
            except OSError:
                pass


def cargar_whisper():
    from faster_whisper import WhisperModel

    try:
        modelo = WhisperModel(WHISPER_MODELO, device="cuda", compute_type="int8_float16")
        list(modelo.transcribe(np.zeros(16000, dtype=np.float32), language=IDIOMA)[0])
        return modelo, f"{WHISPER_MODELO} · gráfica"
    except Exception as e:
        print(f"[!] Whisper no pudo usar la gráfica ({e}). Usando el procesador.", flush=True)
        hilos = max(2, (os.cpu_count() or 4) - 2)
        return WhisperModel(WHISPER_MODELO_CPU, device="cpu", compute_type="int8", cpu_threads=hilos), \
            f"{WHISPER_MODELO_CPU} · procesador"


# ---------------- voces ----------------
_voces = {}
_candado_voces = threading.Lock()


def obtener_voz(modelo):
    if not re.fullmatch(r"[\w\-./]+", modelo or "") or ".." in modelo:
        raise ValueError(f"Modelo de voz no válido: {modelo}")
    with _candado_voces:
        if modelo in _voces:
            return _voces[modelo]
        os.makedirs(CARPETA_VOCES, exist_ok=True)
        base = os.path.basename(modelo)
        for ext in (".onnx", ".onnx.json"):
            destino = os.path.join(CARPETA_VOCES, base + ext)
            if not os.path.exists(destino):
                print(f"[i] Descargando voz {base}{ext}", flush=True)
                urllib.request.urlretrieve(VOCES_URL + modelo + ext, destino + ".part")
                os.replace(destino + ".part", destino)
        from piper import PiperVoice

        ruta = os.path.join(CARPETA_VOCES, base + ".onnx")
        voz = PiperVoice.load(ruta)
        with open(ruta + ".json", encoding="utf-8") as f:
            mapa = json.load(f).get("speaker_id_map") or {}
        _voces[modelo] = (voz, mapa, threading.Lock())
        return _voces[modelo]


def sintetizar(texto, modelo, hablante, velocidad):
    voz, mapa, candado = obtener_voz(modelo)
    speaker = mapa.get(hablante, 0) if (hablante and mapa) else None
    escala = 1.0 / max(0.5, float(velocidad or 1.0))
    with candado:
        try:  # piper-tts >= 1.3
            from piper import SynthesisConfig

            cfg = SynthesisConfig(speaker_id=speaker, length_scale=escala)
            trozos = [c.audio_int16_array for c in voz.synthesize(texto, syn_config=cfg)]
            audio = np.concatenate(trozos) if trozos else np.zeros(0, dtype=np.int16)
        except ImportError:  # piper-tts antiguo
            datos = b"".join(voz.synthesize_stream_raw(texto, speaker_id=speaker, length_scale=escala))
            audio = np.frombuffer(datos, dtype=np.int16)
    salida = io.BytesIO()
    with wave.open(salida, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(voz.config.sample_rate)
        w.writeframes(audio.astype(np.int16).tobytes())
    return salida.getvalue()


# ---------------- servidor HTTP ----------------
MODELO = None
DESCRIPCION = ""
_candado_whisper = threading.Lock()


class Manejador(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # sin una línea por petición

    def _responder(self, codigo, cuerpo: bytes, tipo):
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(cuerpo)))
        self.end_headers()
        self.wfile.write(cuerpo)

    def _json(self, codigo, datos):
        self._responder(codigo, json.dumps(datos, ensure_ascii=False).encode("utf-8"), "application/json")

    def _cuerpo(self):
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def do_GET(self):
        if self.path == "/estado":
            self._json(200, {"listo": True, "oido": DESCRIPCION})
        else:
            self._json(404, {"error": "no existe"})

    def do_POST(self):
        try:
            if self.path == "/transcribir":
                audio = np.frombuffer(self._cuerpo(), dtype=np.int16).astype(np.float32) / 32768.0
                with _candado_whisper:
                    segmentos, _ = MODELO.transcribe(
                        audio, language=IDIOMA, beam_size=1, vad_filter=True,
                        condition_on_previous_text=False, without_timestamps=True,
                        initial_prompt=PROMPT_INICIAL)
                    texto = " ".join(s.text.strip() for s in segmentos).strip()
                self._json(200, {"texto": texto})
            elif self.path == "/sintetizar":
                d = json.loads(self._cuerpo() or b"{}")
                wav = sintetizar(d.get("texto", ""), d.get("modelo"), d.get("hablante"), d.get("velocidad", 1.0))
                self._responder(200, wav, "audio/wav")
            else:
                self._json(404, {"error": "no existe"})
        except Exception as e:
            print(f"[!] {self.path}: {e}", flush=True)
            self._json(500, {"error": str(e)})


if __name__ == "__main__":
    registrar_dlls_cuda()
    print("[i] Cargando Whisper...", flush=True)
    MODELO, DESCRIPCION = cargar_whisper()
    print(f"[i] Oído: {DESCRIPCION}", flush=True)
    obtener_voz(VOZ_POR_DEFECTO)
    if "--precargar" in sys.argv:  # lo usa el instalador para descargarlo todo
        print("[i] Modelos descargados.", flush=True)
        sys.exit(0)
    print(f"[i] Servicio listo en http://127.0.0.1:{PUERTO}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PUERTO), Manejador).serve_forever()
