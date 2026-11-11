package es.zero.audio;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import es.zero.ia.ServicioIA;
import es.zero.util.Textos;

/**
 * La voz de Zero.
 * <p>
 * Trabaja con DOS hilos en cadena (una "tubería"):
 * <pre>
 *   encolar(frase) -> [colaTexto] -> hilo sintetizador -> [colaAudio] -> hilo reproductor -> altavoces
 * </pre>
 * Mientras suena una frase, ya se está generando la siguiente, así no hay pausas.
 */
public class Voz {

    /** Un trozo de audio listo para sonar. */
    private record Sonido(AudioFormat formato, byte[] datos) {
    }

    private final ServicioIA servicio;
    private final BlockingQueue<String> colaTexto = new LinkedBlockingQueue<>();
    private final BlockingQueue<Sonido> colaAudio = new LinkedBlockingQueue<>();
    private final Object candado = new Object();
    private int pendientes = 0; // frases encoladas que aún no han terminado de sonar

    private volatile VozDisponible voz;
    private volatile double velocidad;
    private volatile double volumen;

    public Voz(ServicioIA servicio, VozDisponible voz, double velocidad, double volumen) {
        this.servicio = servicio;
        this.voz = voz;
        this.velocidad = velocidad;
        this.volumen = volumen;
        iniciarHilo("voz-sintetizador", this::sintetizarSinParar);
        iniciarHilo("voz-reproductor", this::reproducirSinParar);
    }

    private static void iniciarHilo(String nombre, Runnable tarea) {
        Thread hilo = new Thread(tarea, nombre);
        hilo.setDaemon(true);
        hilo.start();
    }

    public void ponerVoz(VozDisponible voz) {
        this.voz = voz;
    }

    public VozDisponible voz() {
        return voz;
    }

    public void ponerVelocidad(double velocidad) {
        this.velocidad = velocidad;
    }

    /** @param volumen de 0.0 (mudo) a 1.0 (máximo) */
    public void ponerVolumen(double volumen) {
        this.volumen = Math.max(0, Math.min(1, volumen));
    }

    /**
     * Baja el volumen multiplicando cada muestra de audio por un factor entre 0 y 1.
     * El oído humano percibe el volumen de forma logarítmica, por eso se usa volumen²:
     * así la barra "se siente" más natural (al 50% suena a la mitad, no casi igual).
     */
    private static byte[] aplicarVolumen(Sonido sonido, double volumen) {
        AudioFormat f = sonido.formato();
        if (volumen >= 0.999 || f.getSampleSizeInBits() != 16 || f.isBigEndian()) {
            return sonido.datos(); // sin cambios
        }
        double factor = volumen * volumen;
        byte[] salida = new byte[sonido.datos().length];
        ByteBuffer entrada = ByteBuffer.wrap(sonido.datos()).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer destino = ByteBuffer.wrap(salida).order(ByteOrder.LITTLE_ENDIAN);
        while (entrada.remaining() >= 2) {
            destino.putShort((short) Math.round(entrada.getShort() * factor));
        }
        return salida;
    }

    // ===================== uso desde fuera =====================

    /** Añade una frase a la cola y vuelve enseguida (no espera a que suene). */
    public void encolar(String texto) {
        String limpio = Textos.limpiar(texto);
        if (!limpio.isEmpty()) {
            marcar();
            colaTexto.add(limpio);
        }
    }

    /** Espera a que termine de sonar todo lo que hay en cola. */
    public void esperar() throws InterruptedException {
        synchronized (candado) {
            while (pendientes > 0) {
                candado.wait(); // se despierta con notifyAll() en desmarcar()
            }
        }
    }

    /** Dice una frase y espera a que termine. */
    public void decir(String texto) throws InterruptedException {
        encolar(texto);
        esperar();
    }

    /** Pitido corto generado matemáticamente (una onda senoidal que se apaga). */
    public void pitido(double frecuencia) throws InterruptedException {
        float muestreo = 22050f;
        int n = (int) (muestreo * 0.12);
        ByteBuffer buffer = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            double t = i / muestreo;
            double apagado = 1.0 - (double) i / n;
            buffer.putShort((short) (Math.sin(2 * Math.PI * frecuencia * t) * 0.25 * apagado * Short.MAX_VALUE));
        }
        marcar();
        colaAudio.add(new Sonido(new AudioFormat(muestreo, 16, 1, true, false), buffer.array()));
        esperar();
    }

    // ===================== hilos internos =====================

    private void sintetizarSinParar() {
        while (true) {
            try {
                String texto = colaTexto.take(); // espera hasta que haya algo
                byte[] wav = servicio.sintetizar(texto, voz, velocidad);
                try (AudioInputStream entrada = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
                    colaAudio.add(new Sonido(entrada.getFormat(), entrada.readAllBytes()));
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                System.err.println("[!] Error de voz: " + e.getMessage());
                desmarcar();
            }
        }
    }

    private void reproducirSinParar() {
        while (true) {
            Sonido sonido;
            try {
                sonido = colaAudio.take();
            } catch (InterruptedException e) {
                return;
            }
            try (SourceDataLine altavoz = AudioSystem.getSourceDataLine(sonido.formato())) {
                altavoz.open(sonido.formato());
                altavoz.start();
                byte[] datos = aplicarVolumen(sonido, volumen);
                altavoz.write(datos, 0, datos.length);
                altavoz.drain(); // espera a que se reproduzca entero
            } catch (Exception e) {
                System.err.println("[!] Error reproduciendo audio: " + e.getMessage());
            }
            if (colaAudio.isEmpty() && colaTexto.isEmpty()) {
                try {
                    Thread.sleep(250); // deja que se apague el eco antes de volver a escuchar
                } catch (InterruptedException e) {
                    return;
                }
            }
            desmarcar();
        }
    }

    private void marcar() {
        synchronized (candado) {
            pendientes++;
            EstadoAudio.HABLANDO.set(true);
        }
    }

    private void desmarcar() {
        synchronized (candado) {
            pendientes = Math.max(0, pendientes - 1);
            if (pendientes == 0) {
                EstadoAudio.HABLANDO.set(false);
                candado.notifyAll();
            }
        }
    }
}
