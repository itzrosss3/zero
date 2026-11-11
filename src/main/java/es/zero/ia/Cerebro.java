package es.zero.ia;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import es.zero.Config;
import es.zero.herramientas.RegistroHerramientas;
import es.zero.util.Fechas;

/**
 * El cerebro de Zero: habla con el modelo de lenguaje que corre en Ollama.
 * <p>
 * Cómo funciona una "conversación con herramientas":
 * <ol>
 * <li>Se manda a Ollama el historial + la lista de herramientas disponibles.</li>
 * <li>El modelo responde con texto... o pide usar una herramienta (p. ej. crear_recordatorio).</li>
 * <li>Si pide herramienta, Java la ejecuta, le manda el resultado y vuelve al paso 1.</li>
 * </ol>
 * La respuesta llega en "streaming" (trozo a trozo): cada frase completa se manda a la voz
 * en cuanto está lista, así Zero empieza a hablar antes de terminar de pensar.
 */
public class Cerebro {

    /** Quien quiera enterarse de lo que pasa implementa esta interfaz (patrón observador). */
    public interface Oyente {
        void frase(String frase);

        void herramienta(String nombre, String resultado);
    }

    private record Respuesta(String contenido, JsonArray llamadas) {
    }

    private static final Pattern FIN_FRASE = Pattern.compile("(.+?[.!?…;:])(\\s+)", Pattern.DOTALL);

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final RegistroHerramientas herramientas;
    private final List<JsonObject> historial = new ArrayList<>();

    public Cerebro(RegistroHerramientas herramientas) {
        this.herramientas = herramientas;
    }

    // ===================== preparación =====================

    /** Comprueba que Ollama está en marcha y que el modelo está descargado, y lo carga en la gráfica. */
    public void preparar() throws IOException, InterruptedException {
        List<String> modelos = modelosInstalados();
        if (modelos == null) {
            System.out.println("[i] Ollama no está en marcha, arrancándolo...");
            try {
                new ProcessBuilder("ollama", "serve")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (IOException e) {
                throw new IllegalStateException("No encuentro Ollama. Instálalo desde https://ollama.com/download");
            }
            for (int i = 0; i < 60 && modelos == null; i++) {
                Thread.sleep(2000);
                modelos = modelosInstalados();
            }
            if (modelos == null) {
                throw new IllegalStateException("Ollama no responde. Ábrelo y vuelve a intentarlo.");
            }
        }
        if (!modelos.contains(Config.LLM_MODELO) && !modelos.contains(Config.LLM_MODELO + ":latest")) {
            throw new IllegalStateException("Falta el modelo. Ejecuta en PowerShell:  ollama pull " + Config.LLM_MODELO);
        }

        descargarOtrosModelos();

        // keep_alive -1: el modelo se queda en la gráfica para siempre (sin recargas lentas)
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("model", Config.LLM_MODELO);
        cuerpo.add("messages", new JsonArray());
        cuerpo.addProperty("keep_alive", -1);
        enviar("/api/chat", cuerpo, Duration.ofMinutes(5));
    }

    /**
     * Saca de la gráfica cualquier otro modelo que siga cargado (por ejemplo, de una versión anterior).
     * Como usamos keep_alive -1, un modelo viejo se quedaría ocupando memoria para siempre.
     * Se descarga pidiéndole a Ollama ese modelo con keep_alive 0.
     */
    private void descargarOtrosModelos() {
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(Config.OLLAMA_URL + "/api/ps"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            String json = http.send(peticion, HttpResponse.BodyHandlers.ofString()).body();
            for (JsonElement e : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("models")) {
                String nombre = e.getAsJsonObject().get("name").getAsString();
                if (nombre.equals(Config.LLM_MODELO) || nombre.equals(Config.LLM_MODELO + ":latest")) {
                    continue; // este es el nuestro
                }
                System.out.println("[i] Sacando de la gráfica un modelo que no se usa: " + nombre);
                JsonObject cuerpo = new JsonObject();
                cuerpo.addProperty("model", nombre);
                cuerpo.addProperty("keep_alive", 0);
                enviar("/api/generate", cuerpo, Duration.ofSeconds(30));
            }
        } catch (Exception e) {
            System.err.println("[!] No he podido revisar los modelos cargados: " + e.getMessage());
        }
    }

    private List<String> modelosInstalados() {
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(Config.OLLAMA_URL + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            String json = http.send(peticion, HttpResponse.BodyHandlers.ofString()).body();
            List<String> nombres = new ArrayList<>();
            for (JsonElement modelo : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("models")) {
                nombres.add(modelo.getAsJsonObject().get("name").getAsString());
            }
            return nombres;
        } catch (Exception e) {
            return null; // Ollama no responde
        }
    }

    /** Qué porcentaje del modelo está en la gráfica (menos de 100 = va más lento), o -1 si no se sabe. */
    public int porcentajeGpu() {
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(Config.OLLAMA_URL + "/api/ps"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            String json = http.send(peticion, HttpResponse.BodyHandlers.ofString()).body();
            for (JsonElement e : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("models")) {
                JsonObject modelo = e.getAsJsonObject();
                String nombre = modelo.get("name").getAsString();
                if (nombre.equals(Config.LLM_MODELO) || nombre.equals(Config.LLM_MODELO + ":latest")) {
                    double tamano = modelo.get("size").getAsDouble();
                    return (int) Math.round(100 * modelo.get("size_vram").getAsDouble() / tamano);
                }
            }
        } catch (Exception e) {
            // no pasa nada, simplemente no lo sabemos
        }
        return -1;
    }

    private String enviar(String ruta, JsonObject cuerpo, Duration tiempo) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(Config.OLLAMA_URL + ruta))
                .timeout(tiempo)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString()).body();
    }

    // ===================== pensar =====================

    /**
     * Procesa lo que ha dicho el usuario. Las frases de la respuesta se van entregando al oyente.
     *
     * @return el texto completo de la respuesta
     */
    public String pensar(String textoUsuario, Oyente oyente) throws IOException, InterruptedException {
        historial.add(mensaje("user", textoUsuario));
        while (historial.size() > Config.MAX_HISTORIAL) {
            historial.remove(0);
        }
        while (!historial.isEmpty() && !historial.get(0).get("role").getAsString().equals("user")) {
            historial.remove(0); // el historial siempre debe empezar por un mensaje del usuario
        }

        for (int ronda = 0; ronda < 4; ronda++) { // máximo 4 rondas de herramientas
            JsonArray mensajes = new JsonArray();
            mensajes.add(mensaje("system", Config.PROMPT_SISTEMA + contextoFecha()));
            historial.forEach(mensajes::add);

            Respuesta respuesta = llamar(mensajes, oyente);
            JsonObject delAsistente = mensaje("assistant", respuesta.contenido());
            if (!respuesta.llamadas().isEmpty()) {
                delAsistente.add("tool_calls", respuesta.llamadas());
            }
            historial.add(delAsistente);

            if (respuesta.llamadas().isEmpty()) {
                return respuesta.contenido(); // respuesta final
            }

            for (JsonElement elemento : respuesta.llamadas()) {
                JsonObject funcion = elemento.getAsJsonObject().getAsJsonObject("function");
                String nombre = funcion.get("name").getAsString();
                JsonObject argumentos = leerArgumentos(funcion.get("arguments"));
                String resultado = herramientas.ejecutar(nombre, argumentos);
                oyente.herramienta(nombre, resultado);

                JsonObject deHerramienta = mensaje("tool", resultado);
                deHerramienta.addProperty("tool_name", nombre);
                historial.add(deHerramienta);
            }
        }
        String texto = "Me he liado con las herramientas, " + Config.NOMBRE_USUARIO + ". ¿Puede repetirlo?";
        oyente.frase(texto);
        return texto;
    }

    private Respuesta llamar(JsonArray mensajes, Oyente oyente) throws IOException, InterruptedException {
        JsonObject opciones = new JsonObject();
        opciones.addProperty("temperature", 0.5);
        opciones.addProperty("num_ctx", Config.CONTEXTO_LLM);

        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("model", Config.LLM_MODELO);
        cuerpo.add("messages", mensajes);
        cuerpo.add("tools", herramientas.esquemas());
        cuerpo.addProperty("stream", true);
        cuerpo.addProperty("think", false);   // sin "pensar en voz alta": más rápido
        cuerpo.addProperty("keep_alive", -1);
        cuerpo.add("options", opciones);

        HttpRequest peticion = HttpRequest.newBuilder(URI.create(Config.OLLAMA_URL + "/api/chat"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))
                .build();

        // ofLines(): la respuesta llega línea a línea, cada línea es un trozo en JSON
        HttpResponse<Stream<String>> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofLines());
        StringBuilder contenido = new StringBuilder();
        FiltroPensamientos filtro = new FiltroPensamientos(oyente);
        JsonArray llamadas = new JsonArray();

        try (Stream<String> lineas = respuesta.body()) {
            Iterator<String> it = lineas.iterator();
            while (it.hasNext()) {
                String linea = it.next();
                if (linea.isBlank()) {
                    continue;
                }
                JsonObject trozo = JsonParser.parseString(linea).getAsJsonObject();
                if (trozo.has("error")) {
                    throw new IOException("Ollama: " + trozo.get("error").getAsString());
                }
                JsonObject msg = trozo.has("message") ? trozo.getAsJsonObject("message") : new JsonObject();
                if (msg.has("tool_calls")) {
                    llamadas.addAll(msg.getAsJsonArray("tool_calls"));
                }
                if (msg.has("content") && !msg.get("content").getAsString().isEmpty()) {
                    String texto = msg.get("content").getAsString();
                    contenido.append(texto);
                    filtro.anadir(texto);
                }
                if (trozo.has("done") && trozo.get("done").getAsBoolean()) {
                    break;
                }
            }
        }
        filtro.terminar();
        return new Respuesta(FiltroPensamientos.limpiar(contenido.toString(), filtro.seQuedoPensando()), llamadas);
    }

    /**
     * Filtro que separa los "pensamientos" del modelo de su respuesta real, y entrega la respuesta
     * frase a frase a medida que llega.
     * <p>
     * Algunos modelos razonan en voz alta antes de responder, a veces sin la etiqueta de apertura
     * {@code <think>} (solo con la de cierre {@code </think>}). Este filtro tiene tres ESTADOS:
     * <ul>
     * <li>INDECISO: aún no sabe si lo que llega es razonamiento o respuesta.</li>
     * <li>PENSANDO: es razonamiento; no se dice nada hasta ver {@code </think>}.</li>
     * <li>RESPONDIENDO: es la respuesta; cada frase completa se manda a la voz.</li>
     * </ul>
     * Un objeto que cambia de comportamiento según su estado es una "máquina de estados".
     */
    private static final class FiltroPensamientos {

        private enum Estado { INDECISO, PENSANDO, RESPONDIENDO }

        /** Cómo suelen empezar los razonamientos (en inglés, aunque luego respondan en español). */
        private static final Pattern INICIO_RAZONAMIENTO = Pattern.compile(
                "^\\s*(<think>|okay|ok[,.]|alright|all right|let me|let's|the user|hmm|first,|so,|well,|i need|i should|we need)",
                Pattern.CASE_INSENSITIVE);
        private static final String CIERRE = "</think>";

        private final Oyente oyente;
        private final StringBuilder buffer = new StringBuilder();
        private Estado estado = Estado.INDECISO;

        FiltroPensamientos(Oyente oyente) {
            this.oyente = oyente;
        }

        void anadir(String trozo) {
            buffer.append(trozo);
            int cierre = buffer.lastIndexOf(CIERRE);
            if (cierre >= 0) { // fin del razonamiento: tiramos todo lo anterior
                buffer.delete(0, cierre + CIERRE.length());
                estado = Estado.RESPONDIENDO;
            }
            if (estado == Estado.INDECISO) {
                String inicio = buffer.toString().strip();
                if (inicio.length() < 12 && !FIN_FRASE.matcher(buffer).find()) {
                    return; // aún hay muy poco texto para decidir
                }
                estado = INICIO_RAZONAMIENTO.matcher(inicio).lookingAt() ? Estado.PENSANDO : Estado.RESPONDIENDO;
            }
            if (estado == Estado.RESPONDIENDO) {
                entregarFrasesCompletas();
            }
        }

        void terminar() {
            String resto = buffer.toString().strip();
            if (estado != Estado.PENSANDO && !resto.isEmpty()) {
                oyente.frase(resto);
            }
            buffer.setLength(0);
        }

        boolean seQuedoPensando() {
            return estado == Estado.PENSANDO;
        }

        private void entregarFrasesCompletas() {
            String texto = buffer.toString();
            Matcher m = FIN_FRASE.matcher(texto);
            int inicio = 0;
            while (m.lookingAt()) {
                oyente.frase(m.group(1).strip());
                inicio = m.end();
                m.region(inicio, texto.length());
            }
            buffer.delete(0, inicio);
        }

        /** El texto que se guarda en el historial: sin pensamientos. */
        static String limpiar(String contenido, boolean soloPensamiento) {
            if (soloPensamiento) {
                return "";
            }
            int cierre = contenido.lastIndexOf(CIERRE);
            String limpio = cierre >= 0 ? contenido.substring(cierre + CIERRE.length()) : contenido;
            return limpio.replaceAll("(?s)<think>.*?</think>", "").strip();
        }
    }

    private static JsonObject leerArgumentos(JsonElement argumentos) {
        if (argumentos == null || argumentos.isJsonNull()) {
            return new JsonObject();
        }
        if (argumentos.isJsonObject()) {
            return argumentos.getAsJsonObject();
        }
        try { // a veces llegan como texto con JSON dentro
            return JsonParser.parseString(argumentos.getAsString()).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static JsonObject mensaje(String rol, String contenido) {
        JsonObject m = new JsonObject();
        m.addProperty("role", rol);
        m.addProperty("content", contenido);
        return m;
    }

    /** Le dice al modelo qué día es hoy y los próximos días, para que calcule bien las fechas. */
    private static String contextoFecha() {
        LocalDateTime ahora = LocalDateTime.now();
        String dias = IntStream.range(0, 8)
                .mapToObj(i -> LocalDate.now().plusDays(i))
                .map(d -> Fechas.diaSemana(d) + " " + d)
                .collect(Collectors.joining(", "));
        return "\n\nAhora es %s %s. Próximos días: %s."
                .formatted(Fechas.diaSemana(ahora.toLocalDate()), ahora.format(Fechas.FORMATO), dias);
    }
}
