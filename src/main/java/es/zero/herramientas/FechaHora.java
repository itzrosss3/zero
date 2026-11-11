package es.zero.herramientas;

import java.time.LocalDateTime;

import com.google.gson.JsonObject;

import es.zero.util.Fechas;

/** La herramienta más sencilla: un buen sitio para empezar a leer. */
public class FechaHora extends HerramientaBase {

    public FechaHora() {
        super("obtener_fecha_hora", "Devuelve la fecha y hora actuales.");
        // no tiene parámetros
    }

    @Override
    public String ejecutar(JsonObject argumentos) {
        LocalDateTime ahora = LocalDateTime.now();
        return "Son las %d y %d del %s %d de %s de %d.".formatted(
                ahora.getHour(), ahora.getMinute(),
                Fechas.diaSemana(ahora.toLocalDate()), ahora.getDayOfMonth(),
                Fechas.mes(ahora.toLocalDate()), ahora.getYear());
    }
}
