package es.zero.ia;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import es.zero.Config;

/**
 * Un servicio en Python que Java arranca, espera a que esté listo y cierra al salir.
 * <p>
 * Zero usa dos: el principal (oído + voces Piper) y el de la voz realista (Chatterbox).
 * Como funcionan igual, en vez de repetir el código se escribe UNA clase y se crean
 * DOS objetos con distintos datos. Eso es reutilizar con orientación a objetos.
 */
public class ProcesoPython {

    private final String nombre;
    private final String url;
    private final Path venv;
    private final String script;
    private final String archivoLog;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private Process proceso;

    public ProcesoPython(String nombre, String url, Path venv, String script, String archivoLog) {
        this.nombre = nombre;
        this.url = url;
        this.venv = venv;
        this.script = script;
        this.archivoLog = archivoLog;
    }

    private Path python() {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return venv.resolve(windows ? "Scripts/python.exe" : "bin/python");
    }

    /** ¿Está instalado su Python? */
    public boolean instalado() {
        return Files.exists(python());
    }

    public String url() {
        return url;
    }

    /** Arranca el proceso (si no estaba ya en marcha) y espera hasta que responde. */
    public void arrancar() throws IOException, InterruptedException {
        if (responde()) {
            return; // ya estaba en marcha (por ejemplo, de una ejecución anterior)
        }
        if (!instalado()) {
            throw new IllegalStateException("Falta instalar el " + nombre + ". Mira la carpeta servicio-ia.");
        }
        Files.createDirectories(Config.CARPETA_DATOS);
        Path log = Config.CARPETA_DATOS.resolve(archivoLog).toAbsolutePath();
        proceso = new ProcessBuilder(python().toAbsolutePath().toString(), script)
                .directory(Config.CARPETA_SERVICIO.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::detener)); // al cerrar Java, se cierra

        long limite = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis();
        while (System.currentTimeMillis() < limite) {
            if (!proceso.isAlive()) {
                throw new IllegalStateException("El " + nombre + " se ha cerrado. Mira datos/" + archivoLog);
            }
            if (responde()) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("El " + nombre + " tarda demasiado en arrancar. Mira datos/" + archivoLog);
    }

    public boolean responde() {
        try {
            return pedirEstado() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Lo que devuelve GET /estado, o null si no responde bien. */
    public JsonObject pedirEstado() throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url + "/estado"))
                .timeout(Duration.ofSeconds(3)).GET().build();
        HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        return respuesta.statusCode() == 200 ? JsonParser.parseString(respuesta.body()).getAsJsonObject() : null;
    }

    public void detener() {
        if (proceso != null && proceso.isAlive()) {
            proceso.destroy();
        }
    }
}
