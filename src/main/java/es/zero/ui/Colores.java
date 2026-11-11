package es.zero.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Paleta de colores y fuentes de la interfaz. Cambia aquí el aspecto de Zero. */
public final class Colores {

    private Colores() {
    }

    // ===================== Colores: todo negro =====================
    public static final Color FONDO = new Color(0x000000);   // fondo de la ventana
    public static final Color PANEL = new Color(0x0A0A0A);   // cabecera, chat, barra lateral
    public static final Color PANEL2 = new Color(0x141414);  // botones, filas de recordatorios
    public static final Color BORDE = new Color(0x1F1F1F);
    public static final Color TEXTO = new Color(0xEDEDED);
    public static final Color TENUE = new Color(0x7A7A7A);

    // Colores de acento (estados del orbe, nombres en el chat...)
    public static final Color ACENTO = new Color(0x38BDF8);
    public static final Color ACENTO2 = new Color(0x0EA5E9);
    public static final Color TU = new Color(0xA5B4FC);
    public static final Color OK = new Color(0x34D399);
    public static final Color AVISO = new Color(0xFBBF24);
    public static final Color ERROR = new Color(0xF87171);
    public static final Color SILENCIADO = new Color(0xED4245); // el rojo del micro silenciado de Discord

    // ===================== Fuentes: las de Windows 11 =====================
    // Windows 11 usa "Segoe UI Variable". Si no está (Windows 10), se usa Segoe UI.
    public static final String FUENTE = elegirFuente("Segoe UI Variable Text", "Segoe UI Variable", "Segoe UI");
    public static final String FUENTE_TITULOS = elegirFuente("Segoe UI Variable Display", "Segoe UI Variable", "Segoe UI");
    public static final String FUENTE_MONO = elegirFuente("Cascadia Mono", "Cascadia Code", "Consolas");

    /** Devuelve la primera fuente de la lista que esté instalada en el ordenador. */
    private static String elegirFuente(String... candidatas) {
        Set<String> instaladas = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidata : candidatas) {
            if (instaladas.contains(candidata)) {
                return candidata;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font fuente(int tamano, boolean negrita) {
        return new Font(FUENTE, negrita ? Font.BOLD : Font.PLAIN, tamano);
    }

    public static Font fuenteTitulo(int tamano, boolean negrita) {
        return new Font(FUENTE_TITULOS, negrita ? Font.BOLD : Font.PLAIN, tamano);
    }

    public static Font fuenteMono(int tamano) {
        return new Font(FUENTE_MONO, Font.PLAIN, tamano);
    }
}
