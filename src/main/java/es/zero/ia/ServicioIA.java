package es.zero.ia;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import es.zero.Config;
import es.zero.audio.VozDisponible;

/**
 * Cliente de los servicios de IA locales escritos en Python:
 * <ul>
 * <li><b>Principal</b> (servicio.py): oído con Whisper y voces Piper. Obligatorio.</li>
 * <li><b>Voz realista</b> (voz_realista.py): voces Chatterbox. Opcional.</li>
 * </ul>
 * Java los arranca con {@link ProcesoPython} y les habla por HTTP, igual que con Ollama.
 */
public class ServicioIA {

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ProcesoPython principal = new ProcesoPython("servicio de IA (oído y voz)",
            Config.SERVICIO_URL, Config.VENV_SERVICIO, "servicio.py", "servicio-ia.log");
    private final ProcesoPython vozRealista = new ProcesoPython("servicio de voz realista",
            Config.VOZ_REALISTA_URL, Config.VENV_VOZ_REALISTA, "voz_realista.py", "voz-realista.log");

    private volatile boolean hayVozRealista = false;
    private volatile String descripcionVoz = "Piper";
    private boolean avisadoRespaldo = false;

    /** Arranca los servicios. La voz realista solo si está instalada (y si falla, seguimos sin ella). */
    public void arrancar(Consumer<String> informar) throws IOException, InterruptedException {
        principal.arrancar();
        if (vozRealista.instalado()) {
            informar.accept("Cargando la voz realista (Chatterbox)...");
            try {
                vozRealista.arrancar();
                hayVozRealista = true;
                JsonObject estado = vozRealista.pedirEstado();
                descripcionVoz = "Chatterbox · " + estado.get("dispositivo").getAsString() + " + Piper";
            } catch (Exception e) {
                informar.accept("No se pudo arrancar la voz realista (" + e.getMessage() + "). Uso las voces Piper.");
            }
        }
    }

    public String descripcionOido() {
        try {
            return principal.pedirEstado().get("oido").getAsString();
        } catch (Exception e) {
            return "desconocido";
        }
    }

    public String descripcionVoz() {
        return descripcionVoz;
    }

    /** Envía el audio grabado (PCM 16 bits, 16 kHz, mono) y devuelve el texto que has dicho. */
    public String transcribir(byte[] audioPcm) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(principal.url() + "/transcribir"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(audioPcm))
                .build();
        HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(respuesta.body()).getAsJsonObject();
        if (respuesta.statusCode() != 200) {
            throw new IOException("Error del oído: " + json.get("error"));
        }
        return json.get("texto").getAsString();
    }

    /** Convierte texto en voz con el motor que toque. Devuelve un archivo WAV en memoria. */
    public byte[] sintetizar(String texto, VozDisponible voz, double velocidad) throws IOException, InterruptedException {
        if (voz.motor() == VozDisponible.Motor.CHATTERBOX) {
            if (hayVozRealista) {
                try {
                    return sintetizarRealista(texto, voz.modelo());
                } catch (IOException e) {
                    System.err.println("[!] Chatterbox ha fallado, uso Piper: " + e.getMessage());
                }
            } else if (!avisadoRespaldo) {
                avisadoRespaldo = true;
                System.err.println("[!] La voz realista no está instalada: uso " + Config.VOZ_RESPALDO.nombre());
            }
            voz = Config.VOZ_RESPALDO; // plan B: una voz Piper
        }
        return sintetizarPiper(texto, voz, velocidad);
    }

    private byte[] sintetizarPiper(String texto, VozDisponible voz, double velocidad)
            throws IOException, InterruptedException {
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("texto", texto);
        cuerpo.addProperty("modelo", voz.modelo());
        cuerpo.addProperty("hablante", voz.hablante());
        cuerpo.addProperty("velocidad", velocidad);
        return pedirAudio(principal.url() + "/sintetizar", cuerpo);
    }

    private byte[] sintetizarRealista(String texto, String referencia) throws IOException, InterruptedException {
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("texto", texto);
        cuerpo.addProperty("referencia", referencia);
        return pedirAudio(vozRealista.url() + "/sintetizar", cuerpo);
    }

    private byte[] pedirAudio(String direccion, JsonObject cuerpo) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(direccion))
                .timeout(Duration.ofSeconds(120)) // la primera vez puede descargar la voz
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))
                .build();
        HttpResponse<byte[]> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofByteArray());
        if (respuesta.statusCode() != 200) {
            throw new IOException("Error de voz: " + new String(respuesta.body()));
        }
        return respuesta.body();
    }
}
