package es.zero.util;

import java.util.regex.Pattern;

/** Utilidades para limpiar el texto antes de pasarlo a la voz. */
public final class Textos {

    private Textos() {
    }

    // Las expresiones regulares se "compilan" una sola vez y se reutilizan: es más eficiente
    private static final Pattern PENSAMIENTO = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
    private static final Pattern EMOJIS = Pattern.compile("[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}\\x{FE0F}]");
    private static final Pattern SIMBOLOS = Pattern.compile("[*_#`>|~]");

    /** Quita etiquetas de "pensamiento" del modelo, emojis y símbolos de markdown. */
    public static String limpiar(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = PENSAMIENTO.matcher(texto).replaceAll("");
        limpio = limpio.replace("<think>", "").replace("</think>", "");
        limpio = EMOJIS.matcher(limpio).replaceAll("");
        limpio = SIMBOLOS.matcher(limpio).replaceAll("");
        return limpio.strip();
    }
}
