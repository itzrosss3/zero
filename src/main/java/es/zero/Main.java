package es.zero;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import es.zero.nucleo.Nucleo;
import es.zero.recordatorios.Recordatorios;
import es.zero.recordatorios.RepositorioRecordatorios;
import es.zero.ui.Iconos;
import es.zero.ui.Tema;
import es.zero.ui.Ventana;

/**
 * Punto de entrada de Zero. EMPIEZA A LEER POR AQUÍ.
 * <p>
 * Orden de arranque:
 * <ol>
 * <li>Comprueba que no haya otro Zero abierto.</li>
 * <li>Prepara el aspecto oscuro (FlatLaf).</li>
 * <li>Crea los objetos principales: ajustes, recordatorios, núcleo y ventana.</li>
 * <li>Arranca el núcleo en su propio hilo.</li>
 * </ol>
 * Argumentos opcionales:
 * <ul>
 * <li>{@code --inicio}: lo pasa el arranque automático de Windows (espera unos segundos).</li>
 * <li>{@code --log}: guarda los mensajes en datos/zero.log en vez de en la consola.</li>
 * </ul>
 */
public class Main {

    /** Se guarda en un atributo para que no lo borre el recolector de basura mientras Zero esté abierto. */
    @SuppressWarnings("unused") // se usa para ocupar el puerto, no para leerla
    private static ServerSocket candado;

    public static void main(String[] args) throws Exception {
        boolean arranqueConWindows = Arrays.asList(args).contains("--inicio");
        Files.createDirectories(Config.CARPETA_DATOS);
        if (Arrays.asList(args).contains("--log")) {
            redirigirSalidaAFichero();
        }

        // 1) Una sola instancia: si el puerto ya está ocupado, es que Zero ya está abierto
        try {
            candado = new ServerSocket(47613, 1, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Zero ya está abierto.");
            return;
        }

        // 2) Aspecto: tema negro y fuentes de Windows 11 (ver ui/Tema.java)
        Tema.aplicar();
        Iconos.prepararIco(); // recursos/zero.ico para el acceso directo del escritorio

        // 3) Objetos principales
        Ajustes ajustes = new Ajustes();
        Recordatorios recordatorios = new Recordatorios(new RepositorioRecordatorios(Config.BASE_DATOS));
        Nucleo nucleo = new Nucleo(ajustes, recordatorios, arranqueConWindows);

        // 4) La ventana se crea en el hilo de Swing; después se arranca el núcleo en otro hilo
        SwingUtilities.invokeLater(() -> {
            Ventana ventana = new Ventana(nucleo, recordatorios);
            ventana.setVisible(true);
            Thread hiloNucleo = new Thread(nucleo, "nucleo");
            hiloNucleo.setDaemon(true);
            hiloNucleo.start();
        });
    }

    private static void redirigirSalidaAFichero() throws IOException {
        PrintStream log = new PrintStream(new FileOutputStream(Config.ARCHIVO_LOG.toFile(), true), true,
                StandardCharsets.UTF_8);
        System.setOut(log);
        System.setErr(log);
    }
}
