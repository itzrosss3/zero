package es.zero.recordatorios;

import java.time.LocalDateTime;

/**
 * Un recordatorio. Es un record: inmutable y sin código repetitivo.
 *
 * @param id      clave primaria en la base de datos
 * @param texto   qué hay que recordar
 * @param cuando  momento del aviso, o null si no tiene hora
 * @param avisado si ya se avisó a su hora
 */
public record Recordatorio(int id, String texto, LocalDateTime cuando, boolean avisado) {

    public boolean tieneHora() {
        return cuando != null;
    }

    public boolean haPasado() {
        return cuando != null && !cuando.isAfter(LocalDateTime.now());
    }
}
