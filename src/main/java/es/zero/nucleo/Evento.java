package es.zero.nucleo;

/**
 * Mensaje que el núcleo manda a la interfaz para contarle lo que pasa.
 *
 * @param tipo qué ha pasado
 * @param dato información extra (un texto, un Estado, un InfoSistema...) o null
 */
public record Evento(Tipo tipo, Object dato) {

    public enum Tipo {
        ESTADO,        // dato: Estado
        USUARIO,       // dato: String, lo que has dicho o escrito
        ZERO,          // dato: String, una frase completa de Zero
        ZERO_TROZO,    // dato: String, una frase de una respuesta que aún sigue
        ZERO_FIN,      // la respuesta ha terminado
        HERRAMIENTA,   // dato: String, acción ejecutada y su resultado
        INFO,          // dato: String
        IGNORADO,      // dato: String, algo que oyó pero no iba para Zero
        AVISO,         // dato: String
        ERROR,         // dato: String
        SISTEMA,       // dato: InfoSistema
        CONFIRMAR,     // dato: String, la pregunta
        CONFIRMAR_FIN, // la confirmación se ha resuelto
        SALIR
    }

    /** Información para el panel "Sistema" de la interfaz. */
    public record InfoSistema(String oido, String voz, String cerebro, String micro) {
    }
}
