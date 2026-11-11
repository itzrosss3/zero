package es.zero;

import java.nio.file.Path;
import java.util.List;

import es.zero.audio.VozDisponible;

/**
 * Configuración de Zero: todas las constantes en un solo sitio.
 * <p>
 * Es una clase "de utilidad": constructor privado (no se crean objetos de ella)
 * y solo tiene atributos {@code public static final} (constantes).
 * Cambia aquí el nombre, los modelos, los tiempos o la personalidad.
 */
public final class Config {

    private Config() {
    }

    // ===================== Identidad =====================
    public static final String NOMBRE_ASISTENTE = "Zero";
    public static final String VERSION = "1.0";
    public static final String NOMBRE_USUARIO = "señor";

    /** Whisper a veces escribe "Zero" como "cero", "sero"... Zero responde a todas estas formas. */
    public static final List<String> ALIAS_ACTIVACION =
            List.of("zero", "cero", "sero", "xero", "ziro", "siro", "zeró", "ceró");

    public static final List<String> PALABRAS_SALIDA =
            List.of("apágate", "apagate", "desconéctate", "desconectate", "adiós zero", "adios zero");

    // ===================== Audio =====================
    public static final float FRECUENCIA_MUESTREO = 16000f;   // Whisper trabaja a 16 kHz
    public static final int MUESTRAS_POR_BLOQUE = 1280;       // 80 ms de audio
    public static final double SEGUNDOS_SILENCIO = 0.7;       // pausa que marca el final de tu frase
    public static final double MAX_SEGUNDOS_FRASE = 15;
    public static final double ESPERA_INICIO = 5;             // tras decir solo "Zero"
    public static final boolean MODO_CONVERSACION = true;     // seguir escuchando tras responder
    public static final double ESPERA_CONVERSACION = 4;

    // ===================== Servicio de IA (oído y voz, en Python) =====================
    public static final String SERVICIO_URL = "http://127.0.0.1:47614";
    public static final Path CARPETA_SERVICIO = Path.of("servicio-ia");
    /** El Python del servicio vive fuera del proyecto (en %LOCALAPPDATA%\Zero\venv) para no llenar Eclipse. */
    public static final Path VENV_SERVICIO = System.getenv("LOCALAPPDATA") != null
            ? Path.of(System.getenv("LOCALAPPDATA"), "Zero", "venv")
            : Path.of(System.getProperty("user.home"), ".zero", "venv");

    // ===================== Voz realista (Chatterbox, servicio aparte con Python 3.11) =====================
    public static final String VOZ_REALISTA_URL = "http://127.0.0.1:47615";
    public static final Path VENV_VOZ_REALISTA = VENV_SERVICIO.resolveSibling("venv-chatterbox");

    // ===================== Cerebro (Ollama) =====================
    public static final String OLLAMA_URL = "http://localhost:11434";
    /**
     * Con Chatterbox, la gráfica (8 GB) se reparte entre oído, voz y cerebro: un modelo de 4b cabe entero.
     * OJO: usa la versión "instruct". "qwen3:4b" a secas es la versión "thinking", que razona en voz alta
     * siempre (más lenta y te leería sus pensamientos).
     * Si usas solo voces Piper, puedes volver a "qwen3:8b" (más listo).
     */
    public static final String LLM_MODELO = "qwen3:4b-instruct";
    public static final int MAX_HISTORIAL = 16;
    public static final int CONTEXTO_LLM = 4096;

    // ===================== Voces =====================
    /** La primera de la lista es la que se usa por defecto. */
    public static final List<VozDisponible> VOCES = List.of(
            VozDisponible.chatterbox("Chatterbox · mi voz clonada (mujer.wav)", "mujer.wav"),
            VozDisponible.chatterbox("Chatterbox · voz por defecto", null),
            VozDisponible.piper("Sharvard · mujer (España)", "es/es_ES/sharvard/medium/es_ES-sharvard-medium", "F"),
            VozDisponible.piper("Sharvard · hombre (España)", "es/es_ES/sharvard/medium/es_ES-sharvard-medium", "M"),
            VozDisponible.piper("Davefx · hombre grave (España)", "es/es_ES/davefx/medium/es_ES-davefx-medium", null),
            VozDisponible.piper("Carlfm · hombre (España, ligera)", "es/es_ES/carlfm/x_low/es_ES-carlfm-x_low", null),
            VozDisponible.piper("Daniela · mujer (Argentina, HQ)", "es/es_AR/daniela/high/es_AR-daniela-high", null),
            VozDisponible.piper("Claude · (México, HQ)", "es/es_MX/claude/high/es_MX-claude-high", null),
            VozDisponible.piper("Ald · hombre (México)", "es/es_MX/ald/medium/es_MX-ald-medium", null));

    /** Voz que se usa si Chatterbox no está instalado o falla. */
    public static final VozDisponible VOZ_RESPALDO =
            VozDisponible.piper("Sharvard · mujer (España)", "es/es_ES/sharvard/medium/es_ES-sharvard-medium", "F");
    public static final String FRASE_PRUEBA_VOZ = "Hola, soy Zero. ¿Qué tal suena esta voz?";

    // ===================== Archivos =====================
    public static final Path CARPETA_DATOS = Path.of("datos");
    public static final Path BASE_DATOS = CARPETA_DATOS.resolve("zero.db");
    public static final Path ARCHIVO_AJUSTES = CARPETA_DATOS.resolve("ajustes.properties");
    public static final Path CARPETA_CONVERSACIONES = CARPETA_DATOS.resolve("conversaciones");
    public static final Path ARCHIVO_NOTAS = CARPETA_DATOS.resolve("notas.txt");
    public static final Path ARCHIVO_LOG = CARPETA_DATOS.resolve("zero.log");

    // ===================== Recordatorios =====================
    public static final int REVISAR_RECORDATORIOS_SEGUNDOS = 20;
    public static final int ESPERA_ARRANQUE_PC_SEGUNDOS = 15;

    // ===================== Personalidad =====================
    /** Text block (""" ... """): un String de varias líneas. formatted() rellena los %s. */
    public static final String PROMPT_SISTEMA = """
            Eres %s, un asistente personal de inteligencia artificial que vive en el ordenador del usuario y funciona sin internet.
            Te diriges al usuario como "%s". Eres cercano, eficiente, tranquilo y con un humor sutil.
            Respondes SIEMPRE en español de España.
            Tus respuestas se convierten en voz, así que:
            - Sé breve: normalmente una o dos frases.
            - Empieza SIEMPRE con una frase muy corta, de dos a cinco palabras ("Claro, señor." "Hecho."),
              y después da el resto. Así tu voz empieza a sonar antes.
            - No uses markdown, listas, emojis ni símbolos.
            - Escribe las horas como "las 17 y 30" o "las 9 en punto".
            Cuando el usuario pida una acción, usa las herramientas en lugar de inventarte el resultado.
            Recordatorios: si el usuario da un momento ("mañana a las 5", "el viernes"), calcula la fecha con la
            información de fecha actual y pásala en fecha_hora. "A las 5" sin más: la tarde si ya han pasado las 5 de la mañana.
            Sin momento concreto, crea el recordatorio sin fecha.
            Instalar programas: primero usa buscar_programa para encontrar el Id exacto y luego llama a instalar_programa
            con ese Id. El sistema pide confirmación al usuario automáticamente, así que no la pidas tú antes.
            Para Java busca "Temurin JDK".
            Si una herramienta falla, dilo con naturalidad.
            """.formatted(NOMBRE_ASISTENTE, NOMBRE_USUARIO);
}
