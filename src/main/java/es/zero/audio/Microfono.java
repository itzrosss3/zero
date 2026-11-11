package es.zero.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import es.zero.Config;

/**
 * Captura el micrófono con javax.sound (viene con el JDK) y graba frases.
 * <p>
 * Funciona con el patrón PRODUCTOR / CONSUMIDOR:
 * <ul>
 * <li>Un hilo (el productor) lee el micro sin parar y mete bloques de 80 ms en una cola.</li>
 * <li>El núcleo (el consumidor) saca bloques de la cola cuando quiere grabar una frase.</li>
 * </ul>
 * Detectar si hablas es sencillo: se mide el volumen (RMS) de cada bloque y se compara con
 * un umbral calculado a partir del ruido de fondo de tu habitación.
 */
public class Microfono implements AutoCloseable {

    /** Valor para grabarFrase(): esperar a que hables sin límite de tiempo. */
    public static final double SIN_LIMITE = -1;

    private final TargetDataLine linea;
    private final BlockingQueue<short[]> cola = new LinkedBlockingQueue<>(400);
    private volatile boolean activo = true;     // volatile: varios hilos lo leen
    private volatile boolean abierto = true;
    private double umbralVoz = 500;

    public Microfono() throws LineUnavailableException {
        // 16000 muestras/segundo, 16 bits, 1 canal (mono), con signo, little-endian
        AudioFormat formato = new AudioFormat(Config.FRECUENCIA_MUESTREO, 16, 1, true, false);
        linea = AudioSystem.getTargetDataLine(formato);
        linea.open(formato, Config.MUESTRAS_POR_BLOQUE * 2 * 8);
        linea.start();

        Thread productor = new Thread(this::leerSinParar, "microfono");
        productor.setDaemon(true); // un hilo "daemon" no impide que el programa se cierre
        productor.start();
    }

    private void leerSinParar() {
        byte[] buffer = new byte[Config.MUESTRAS_POR_BLOQUE * 2]; // 2 bytes por muestra
        while (abierto) {
            int leidos = linea.read(buffer, 0, buffer.length); // se bloquea hasta tener datos
            if (leidos <= 0 || !activo || EstadoAudio.HABLANDO.get()) {
                continue;
            }
            short[] bloque = new short[leidos / 2];
            ByteBuffer.wrap(buffer, 0, leidos).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(bloque);
            cola.offer(bloque); // offer: si la cola está llena, descarta en lugar de bloquear
        }
    }

    public void ponerActivo(boolean activo) {
        this.activo = activo;
        if (!activo) {
            vaciar();
        }
    }

    public void vaciar() {
        cola.clear();
    }

    /** Volumen medio de un bloque (raíz cuadrática media). */
    private static double rms(short[] bloque) {
        double suma = 0;
        for (short muestra : bloque) {
            suma += (double) muestra * muestra;
        }
        return Math.sqrt(suma / bloque.length);
    }

    /** Lee un bloque de la cola. Devuelve null si alguien pide parar. */
    private short[] leer(AtomicBoolean parar) throws InterruptedException {
        while (true) {
            if (parar != null && parar.get()) {
                return null;
            }
            short[] bloque = cola.poll(100, TimeUnit.MILLISECONDS);
            if (bloque != null) {
                return bloque;
            }
        }
    }

    /** Mide el ruido de fondo para saber a partir de qué volumen estás hablando. */
    public void calibrar() throws InterruptedException {
        vaciar();
        List<Double> niveles = new ArrayList<>();
        long fin = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < fin) {
            short[] bloque = cola.poll(500, TimeUnit.MILLISECONDS);
            if (bloque != null) {
                niveles.add(rms(bloque));
            }
        }
        double ruido = 100;
        if (!niveles.isEmpty()) {
            double[] ordenados = niveles.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            ruido = ordenados[ordenados.length / 2]; // mediana
        }
        umbralVoz = Math.max(ruido * 3, 300);
        System.out.printf("[i] Ruido de fondo %.0f -> umbral de voz %.0f%n", ruido, umbralVoz);
    }

    /**
     * Graba desde que empiezas a hablar hasta que haces una pausa.
     *
     * @param esperaInicio segundos que espera a que empieces a hablar, o {@link #SIN_LIMITE}
     * @param parar        si se pone a true desde otro hilo, deja de grabar (puede ser null)
     * @return el audio en bytes (PCM 16 bits little-endian) o null si no hablaste
     */
    public byte[] grabarFrase(double esperaInicio, AtomicBoolean parar) throws InterruptedException {
        vaciar();
        double bloquesPorSegundo = Config.FRECUENCIA_MUESTREO / Config.MUESTRAS_POR_BLOQUE;
        long limite = esperaInicio < 0 ? Long.MAX_VALUE
                : System.currentTimeMillis() + (long) (Math.max(0.3, esperaInicio) * 1000);

        // 1) Esperar a que empiece a hablar, guardando un poco de audio previo
        List<short[]> previo = new ArrayList<>();
        while (true) {
            short[] bloque = leer(parar);
            if (bloque == null) {
                return null;
            }
            previo.add(bloque);
            if (previo.size() > 4) {
                previo.remove(0);
            }
            if (rms(bloque) > umbralVoz) {
                break;
            }
            if (System.currentTimeMillis() > limite) {
                return null;
            }
        }

        // 2) Grabar hasta un silencio
        List<short[]> grabado = new ArrayList<>(previo);
        int silencio = 0;
        int conVoz = 0;
        int maxBloques = (int) (Config.MAX_SEGUNDOS_FRASE * bloquesPorSegundo);
        int bloquesSilencio = (int) (Config.SEGUNDOS_SILENCIO * bloquesPorSegundo);
        while (grabado.size() < maxBloques) {
            short[] bloque = leer(parar);
            if (bloque == null) {
                return null;
            }
            grabado.add(bloque);
            if (rms(bloque) < umbralVoz) {
                if (++silencio >= bloquesSilencio) {
                    break;
                }
            } else {
                silencio = 0;
                conVoz++;
            }
        }

        if (conVoz < 3) { // solo un golpe o una tos: no cuenta como frase
            if (limite == Long.MAX_VALUE) {
                return null;
            }
            double restante = (limite - System.currentTimeMillis()) / 1000.0;
            return restante > 0 ? grabarFrase(restante, parar) : null;
        }
        return aBytes(grabado);
    }

    private static byte[] aBytes(List<short[]> bloques) {
        int total = bloques.stream().mapToInt(b -> b.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(total * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short[] bloque : bloques) {
            for (short muestra : bloque) {
                buffer.putShort(muestra);
            }
        }
        return Arrays.copyOf(buffer.array(), total * 2);
    }

    @Override
    public void close() {
        abierto = false;
        linea.stop();
        linea.close();
    }
}
