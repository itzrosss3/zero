package es.zero.herramientas;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Guarda todas las herramientas disponibles y las ejecuta por nombre.
 * <p>
 * Usa un {@link Map} (nombre -> herramienta) para encontrar la herramienta al instante.
 */
public class RegistroHerramientas {

    private final Map<String, Herramienta> herramientas = new LinkedHashMap<>();

    public void registrar(Herramienta herramienta) {
        herramientas.put(herramienta.nombre(), herramienta);
    }

    /** Lista de herramientas en el formato que espera Ollama. */
    public JsonArray esquemas() {
        JsonArray lista = new JsonArray();
        for (Herramienta h : herramientas.values()) {
            JsonObject funcion = new JsonObject();
            funcion.addProperty("name", h.nombre());
            funcion.addProperty("description", h.descripcion());
            funcion.add("parameters", h.parametros());

            JsonObject envoltorio = new JsonObject();
            envoltorio.addProperty("type", "function");
            envoltorio.add("function", funcion);
            lista.add(envoltorio);
        }
        return lista;
    }

    public String ejecutar(String nombre, JsonObject argumentos) {
        Herramienta herramienta = herramientas.get(nombre);
        if (herramienta == null) {
            return "Herramienta desconocida: " + nombre;
        }
        try {
            return herramienta.ejecutar(argumentos);
        } catch (Exception e) {
            return "Error al ejecutar " + nombre + ": " + e.getMessage();
        }
    }
}
