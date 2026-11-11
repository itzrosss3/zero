package es.zero.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Utilidades de fechas con la API moderna java.time (LocalDate, LocalDateTime...). */
public final class Fechas {

    private Fechas() {
    }

    public static final Locale ESPANOL = Locale.of("es", "ES");
    /** Formato con el que se guardan las fechas en la base de datos: "2026-11-12 17:30". */
    public static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static String diaSemana(LocalDate fecha) {
        return fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, ESPANOL);
    }

    public static String mes(LocalDate fecha) {
        return fecha.getMonth().getDisplayName(TextStyle.FULL, ESPANOL);
    }

    /**
     * Convierte lo que manda el modelo ("2026-11-12 17:00", "2026-11-12T17:00", "2026-11-12")
     * en un LocalDateTime. Si viene solo la fecha, pone las 9:00.
     *
     * @throws java.time.format.DateTimeParseException si el texto no es una fecha válida
     */
    public static LocalDateTime parsear(String texto) {
        String limpio = texto.strip().replace('T', ' ');
        if (limpio.length() <= 10) {
            return LocalDate.parse(limpio).atTime(9, 0);
        }
        if (limpio.length() > 16) {
            limpio = limpio.substring(0, 16); // quita los segundos
        }
        return LocalDateTime.parse(limpio, FORMATO);
    }

    /** "mañana a las 17 en punto", "el viernes a las 9 y 30"... para decirlo en voz alta. */
    public static String cuandoHablado(LocalDateTime momento) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), momento.toLocalDate());
        String dia;
        if (dias == 0) {
            dia = "hoy";
        } else if (dias == 1) {
            dia = "mañana";
        } else if (dias == -1) {
            dia = "ayer";
        } else if (dias > 1 && dias < 7) {
            dia = "el " + diaSemana(momento.toLocalDate());
        } else {
            dia = "el %s %d de %s".formatted(diaSemana(momento.toLocalDate()), momento.getDayOfMonth(),
                    mes(momento.toLocalDate()));
        }
        int hora = momento.getHour();
        String horaTexto = momento.getMinute() == 0
                ? hora + " en punto"
                : hora + " y " + momento.getMinute();
        return dia + " a " + (hora == 1 ? "la " : "las ") + horaTexto;
    }
}
