package es.zero.audio;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Estado compartido entre el micrófono y la voz.
 * Mientras Zero habla, el micrófono ignora lo que oye para no escucharse a sí mismo.
 * <p>
 * Se usa {@link AtomicBoolean} en vez de un boolean normal porque lo leen y escriben
 * varios hilos a la vez: así los cambios se ven siempre al momento y sin conflictos.
 */
public final class EstadoAudio {

    private EstadoAudio() {
    }

    public static final AtomicBoolean HABLANDO = new AtomicBoolean(false);
}
