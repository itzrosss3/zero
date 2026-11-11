package es.zero.herramientas;

/**
 * Lo que una herramienta puede pedirle a Zero mientras trabaja.
 * <p>
 * Es una INTERFAZ: dice QUÉ se puede hacer, no CÓMO. El núcleo (Nucleo) la implementa.
 * Gracias a esto, las herramientas no dependen del núcleo ni de la interfaz gráfica:
 * solo de este "contrato".
 */
public interface Asistente {

    /** Dice algo en voz alta (y lo muestra en la conversación). */
    void hablar(String texto);

    /** Muestra un aviso en la interfaz sin decirlo en voz alta. */
    void avisar(String texto);

    /** Pregunta al usuario (por voz y con una ventana Sí/No). Devuelve true si acepta. */
    boolean confirmar(String pregunta);
}
