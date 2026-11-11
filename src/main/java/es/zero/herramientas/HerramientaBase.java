package es.zero.herramientas;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Clase ABSTRACTA con el código común a todas las herramientas.
 * <p>
 * Cada herramienta concreta HEREDA de esta ({@code extends HerramientaBase}), declara sus
 * parámetros en el constructor y solo tiene que escribir su método {@code ejecutar()}.
 * Así no repetimos en cada clase el código para construir el esquema JSON.
 */
public abstract class HerramientaBase implements Herramienta {

    private final String nombre;
    private final String descripcion;
    private final JsonObject propiedades = new JsonObject();
    private final JsonArray obligatorios = new JsonArray();

    protected HerramientaBase(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    // ---------- para declarar parámetros (se usan en el constructor de cada herramienta) ----------

    protected final void parametroTexto(String nombre, String descripcion, boolean obligatorio) {
        parametro(nombre, "string", descripcion, obligatorio);
    }

    protected final void parametroEntero(String nombre, String descripcion, boolean obligatorio) {
        parametro(nombre, "integer", descripcion, obligatorio);
    }

    private void parametro(String nombre, String tipo, String descripcion, boolean obligatorio) {
        JsonObject p = new JsonObject();
        p.addProperty("type", tipo);
        p.addProperty("description", descripcion);
        propiedades.add(nombre, p);
        if (obligatorio) {
            obligatorios.add(nombre);
        }
    }

    // ---------- para leer argumentos de forma segura (dentro de ejecutar) ----------

    protected static String texto(JsonObject args, String clave, String porDefecto) {
        if (args.has(clave) && !args.get(clave).isJsonNull()) {
            return args.get(clave).getAsString().strip();
        }
        return porDefecto;
    }

    protected static int entero(JsonObject args, String clave, int porDefecto) {
        try {
            return args.has(clave) ? (int) Math.round(args.get(clave).getAsDouble()) : porDefecto;
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return porDefecto;
        }
    }

    // ---------- implementación de la interfaz Herramienta ----------

    @Override
    public String nombre() {
        return nombre;
    }

    @Override
    public String descripcion() {
        return descripcion;
    }

    @Override
    public JsonObject parametros() {
        JsonObject esquema = new JsonObject();
        esquema.addProperty("type", "object");
        esquema.add("properties", propiedades);
        esquema.add("required", obligatorios);
        return esquema;
    }
}
