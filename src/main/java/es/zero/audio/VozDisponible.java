package es.zero.audio;

/**
 * Una voz que se puede elegir en la interfaz.
 * <p>
 * Es un {@code record} (Java 16+): una clase inmutable que solo guarda datos.
 * Java genera solo el constructor, los getters, equals, hashCode y toString.
 *
 * @param nombre   nombre que se ve en el desplegable
 * @param motor    qué programa genera la voz
 * @param modelo   PIPER: ruta del modelo en su repositorio. CHATTERBOX: archivo .wav de referencia
 *                 dentro de servicio-ia/voces-clonadas (o null para su voz por defecto)
 * @param hablante solo PIPER, para modelos con varias voces ("F" o "M"); null si no hace falta
 */
public record VozDisponible(String nombre, Motor motor, String modelo, String hablante) {

    /** Los "motores" de voz que sabe usar Zero. */
    public enum Motor {
        PIPER,      // rápido y ligero, algo robótico
        CHATTERBOX  // muy realista, más lento y necesita la gráfica
    }

    // Métodos de fábrica: una forma más legible de crear objetos que llamar al constructor
    public static VozDisponible piper(String nombre, String modelo, String hablante) {
        return new VozDisponible(nombre, Motor.PIPER, modelo, hablante);
    }

    public static VozDisponible chatterbox(String nombre, String archivoReferencia) {
        return new VozDisponible(nombre, Motor.CHATTERBOX, archivoReferencia, null);
    }

    @Override
    public String toString() {
        return nombre; // así se ve bonito en el desplegable (JComboBox)
    }
}
