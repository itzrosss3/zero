package es.zero.herramientas;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/** Utilidades para lanzar programas y abrir páginas web. */
final class Comandos {

    private Comandos() {
    }

    static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    /** Ejecuta una orden de la consola de Windows (cmd) sin esperar a que termine. */
    static void lanzar(String orden) throws IOException {
        ProcessBuilder pb = WINDOWS
                ? new ProcessBuilder("cmd", "/c", orden)
                : new ProcessBuilder("sh", "-c", orden);
        pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }

    /** Abre una dirección en el navegador predeterminado. */
    static void abrirUrl(String url) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
        } else {
            lanzar(WINDOWS ? "start \"\" \"" + url + "\"" : "xdg-open '" + url + "'");
        }
    }
}
