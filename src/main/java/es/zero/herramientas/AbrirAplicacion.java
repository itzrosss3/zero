package es.zero.herramientas;

import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonObject;

/** Abre programas ya instalados. Añade los tuyos al mapa APLICACIONES. */
public class AbrirAplicacion extends HerramientaBase {

    /** Nombre hablado -> orden para abrirlo. Map.ofEntries crea un mapa que no se puede modificar. */
    private static final Map<String, String> APLICACIONES = Map.ofEntries(
            Map.entry("navegador", "start chrome"),
            Map.entry("chrome", "start chrome"),
            Map.entry("edge", "start msedge"),
            Map.entry("firefox", "start firefox"),
            Map.entry("bloc de notas", "start notepad"),
            Map.entry("calculadora", "start calc"),
            Map.entry("explorador", "start explorer"),
            Map.entry("archivos", "start explorer"),
            Map.entry("terminal", "start cmd"),
            Map.entry("powershell", "start powershell"),
            Map.entry("eclipse", "start eclipse"),
            Map.entry("spotify", "start spotify:"),
            Map.entry("discord", "start discord:"),
            Map.entry("steam", "start steam:"),
            Map.entry("visual studio code", "start code"),
            Map.entry("word", "start winword"),
            Map.entry("excel", "start excel"),
            Map.entry("administrador de tareas", "start taskmgr"),
            Map.entry("configuración", "start ms-settings:"));

    public AbrirAplicacion() {
        super("abrir_aplicacion", "Abre un programa ya instalado en el ordenador.");
        parametroTexto("nombre", "Nombre del programa, p. ej. 'spotify'", true);
    }

    @Override
    public String ejecutar(JsonObject argumentos) throws IOException {
        String nombre = texto(argumentos, "nombre", "");
        String clave = nombre.toLowerCase();

        String orden = APLICACIONES.get(clave);
        if (orden == null) { // coincidencia parcial: "el spotify" -> spotify
            orden = APLICACIONES.entrySet().stream()
                    .filter(e -> clave.contains(e.getKey()) || e.getKey().contains(clave))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (orden == null) {
            return "No conozco la aplicación '" + nombre + "'. Hay que añadirla en AbrirAplicacion.java.";
        }
        Comandos.lanzar(orden);
        return "Abriendo " + nombre + ".";
    }
}
