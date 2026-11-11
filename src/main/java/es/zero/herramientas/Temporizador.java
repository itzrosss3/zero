package es.zero.herramientas;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;

import es.zero.Config;

/** Aviso hablado dentro de X segundos. Usa un ScheduledExecutorService (tareas programadas). */
public class Temporizador extends HerramientaBase {

    private final Asistente asistente;
    private final ScheduledExecutorService programador = Executors.newSingleThreadScheduledExecutor(tarea -> {
        Thread hilo = new Thread(tarea, "temporizador");
        hilo.setDaemon(true);
        return hilo;
    });

    public Temporizador(Asistente asistente) {
        super("temporizador", "Aviso hablado dentro de unos segundos o minutos.");
        this.asistente = asistente;
        parametroEntero("segundos", "Segundos hasta el aviso", true);
        parametroTexto("mensaje", "Qué decir cuando termine", false);
    }

    @Override
    public String ejecutar(JsonObject argumentos) {
        int segundos = entero(argumentos, "segundos", 60);
        String mensaje = texto(argumentos, "mensaje", "El tiempo ha terminado");
        // Lambda: el código que se ejecutará cuando pase el tiempo
        programador.schedule(() -> asistente.hablar(capitalizar(Config.NOMBRE_USUARIO) + ", aviso: " + mensaje + "."),
                segundos, TimeUnit.SECONDS);
        return "Temporizador de %d minutos y %d segundos programado.".formatted(segundos / 60, segundos % 60);
    }

    static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
