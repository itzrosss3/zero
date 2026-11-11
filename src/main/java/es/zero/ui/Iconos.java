package es.zero.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * El icono de Zero: el de la barra de título, la barra de tareas y el acceso directo.
 * <p>
 * Si existe {@code recursos/logo.png} (o .jpg) se usa tu logo; si no, se dibuja uno por defecto (el orbe).
 * Además crea {@code recursos/zero.ico}, el formato que necesita Windows para los accesos directos.
 */
public final class Iconos {

    private Iconos() {
    }

    public static final Path CARPETA = Path.of("recursos");
    /** Nombres que se aceptan para el logo, por orden de preferencia. */
    private static final List<String> NOMBRES_LOGO = List.of("logo.png", "logo.jpg", "logo.jpeg");
    public static final Path ICO = CARPETA.resolve("zero.ico");

    /** Tamaños que Windows usa en distintos sitios (título, barra de tareas, escritorio, Alt+Tab...). */
    private static final int[] TAMANOS = { 16, 20, 24, 32, 40, 48, 64, 128, 256 };

    private static List<BufferedImage> cache;

    /** El icono en varios tamaños: Swing elige el mejor para cada sitio. */
    public static synchronized List<BufferedImage> imagenes() {
        if (cache == null) {
            BufferedImage original = cargarLogo();
            cache = new ArrayList<>();
            for (int tamano : TAMANOS) {
                cache.add(escalar(original, tamano));
            }
        }
        return cache;
    }

    /** El primer archivo de logo que exista (logo.png, logo.jpg...), o null si no hay ninguno. */
    private static Path buscarLogo() {
        return NOMBRES_LOGO.stream()
                .map(CARPETA::resolve)
                .filter(Files::exists)
                .findFirst()
                .orElse(null);
    }

    private static BufferedImage cargarLogo() {
        Path logo = buscarLogo();
        if (logo != null) {
            try {
                BufferedImage imagen = ImageIO.read(logo.toFile()); // ImageIO entiende PNG, JPG, GIF y BMP
                if (imagen != null) {
                    return imagen;
                }
            } catch (IOException e) {
                System.err.println("[!] No he podido leer " + logo + ": " + e.getMessage());
            }
        }
        return logoPorDefecto();
    }

    /** Si no hay logo: el orbe de Zero dibujado con código. */
    private static BufferedImage logoPorDefecto() {
        int t = 256;
        BufferedImage img = new BufferedImage(t, t, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Colores.FONDO);
        g.fill(new Ellipse2D.Double(0, 0, t, t));
        g.setColor(new Color(0x5538BDF8, true));
        g.setStroke(new BasicStroke(14f));
        g.draw(new Ellipse2D.Double(34, 34, t - 68, t - 68));
        g.setColor(Colores.ACENTO);
        g.fill(new Ellipse2D.Double(78, 78, t - 156, t - 156));
        g.dispose();
        return img;
    }

    /** Encaja la imagen en un cuadrado de lado x lado (sin deformarla) y la escala con buena calidad. */
    private static BufferedImage escalar(BufferedImage original, int lado) {
        double factor = Math.min((double) lado / original.getWidth(), (double) lado / original.getHeight());
        int ancho = Math.max(1, (int) Math.round(original.getWidth() * factor));
        int alto = Math.max(1, (int) Math.round(original.getHeight() * factor));
        Image escalada = original.getScaledInstance(ancho, alto, Image.SCALE_SMOOTH);

        BufferedImage cuadrado = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cuadrado.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(escalada, (lado - ancho) / 2, (lado - alto) / 2, null); // centrada
        g.dispose();
        return cuadrado;
    }

    /**
     * Crea (o actualiza) recursos/zero.ico para el acceso directo.
     * <p>
     * Un .ico es un "paquete" de varias imágenes. Su formato es sencillo: una cabecera, una ficha
     * por imagen (tamaño y dónde empieza) y después las imágenes en PNG, todo en little-endian.
     */
    public static void prepararIco() {
        try {
            Files.createDirectories(CARPETA);
            Path logo = buscarLogo();
            if (Files.exists(ICO) && logo != null
                    && Files.getLastModifiedTime(ICO).compareTo(Files.getLastModifiedTime(logo)) > 0) {
                return; // ya está al día
            }
            if (Files.exists(ICO) && logo == null) {
                return; // sin logo propio: dejamos el que haya
            }
            int[] tamanos = { 16, 24, 32, 48, 64, 128, 256 };
            List<byte[]> pngs = new ArrayList<>();
            for (int t : tamanos) {
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                ImageIO.write(escalar(cargarLogo(), t), "png", salida);
                pngs.add(salida.toByteArray());
            }

            int cabecera = 6 + 16 * tamanos.length;
            int total = cabecera + pngs.stream().mapToInt(p -> p.length).sum();
            ByteBuffer ico = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
            ico.putShort((short) 0);                // reservado
            ico.putShort((short) 1);                // tipo: 1 = icono
            ico.putShort((short) tamanos.length);   // número de imágenes
            int posicion = cabecera;
            for (int i = 0; i < tamanos.length; i++) {
                int t = tamanos[i];
                ico.put((byte) (t >= 256 ? 0 : t)); // ancho (0 significa 256)
                ico.put((byte) (t >= 256 ? 0 : t)); // alto
                ico.put((byte) 0);                  // colores de paleta (ninguno)
                ico.put((byte) 0);                  // reservado
                ico.putShort((short) 1);            // planos
                ico.putShort((short) 32);           // bits por píxel
                ico.putInt(pngs.get(i).length);     // tamaño de la imagen
                ico.putInt(posicion);               // dónde empieza
                posicion += pngs.get(i).length;
            }
            pngs.forEach(ico::put);
            Files.write(ICO, ico.array());
        } catch (IOException e) {
            System.err.println("[!] No he podido crear el icono: " + e.getMessage());
        }
    }
}
