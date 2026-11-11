package es.zero.herramientas;

import com.google.gson.JsonObject;

/**
 * Una acción que Zero puede hacer en tu ordenador.
 * <p>
 * Todas las herramientas cumplen esta interfaz, así el registro puede tratarlas a todas por igual
 * aunque cada una haga algo distinto: eso es POLIMORFISMO.
 */
public interface Herramienta {

    /** Nombre técnico que usa el modelo, p. ej. "crear_recordatorio". */
    String nombre();

    /** Explicación para el modelo: cuándo debe usarla. */
    String descripcion();

    /** Esquema JSON de los parámetros (el formato que entiende Ollama). */
    JsonObject parametros();

    /**
     * Ejecuta la acción.
     *
     * @param argumentos lo que el modelo ha decidido pasarle
     * @return un texto con el resultado, que se le devuelve al modelo
     */
    String ejecutar(JsonObject argumentos) throws Exception;
}
