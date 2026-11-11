package es.zero;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import es.zero.audio.VozDisponible;

/**
 * Ajustes que se cambian desde la interfaz (voz, velocidad, micro).
 * Se guardan en datos/ajustes.properties usando {@link Properties}, la forma clásica
 * de Java de guardar pares clave=valor en un fichero de texto.
 */
public class Ajustes {

    private final Properties propiedades = new Properties();

    public Ajustes() {
        if (Files.exists(Config.ARCHIVO_AJUSTES)) {
            // try-with-resources: el Reader se cierra solo al acabar, aunque haya excepción
            try (Reader lector = Files.newBufferedReader(Config.ARCHIVO_AJUSTES, StandardCharsets.UTF_8)) {
                propiedades.load(lector);
            } catch (IOException e) {
                System.err.println("[!] No se pudieron leer los ajustes: " + e.getMessage());
            }
        }
    }

    public VozDisponible voz() {
        String nombre = propiedades.getProperty("voz", "");
        return Config.VOCES.stream()
                .filter(v -> v.nombre().equals(nombre))
                .findFirst()
                .orElse(Config.VOCES.get(0));
    }

    public void ponerVoz(VozDisponible voz) {
        propiedades.setProperty("voz", voz.nombre());
        guardar();
    }

    public double velocidad() {
        try {
            return Double.parseDouble(propiedades.getProperty("velocidad", "1.0"));
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    public void ponerVelocidad(double velocidad) {
        propiedades.setProperty("velocidad", String.valueOf(velocidad));
        guardar();
    }

    /** Volumen de la voz de Zero, de 0.0 (mudo) a 1.0 (máximo). */
    public double volumen() {
        try {
            return Double.parseDouble(propiedades.getProperty("volumen", "1.0"));
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    public void ponerVolumen(double volumen) {
        propiedades.setProperty("volumen", String.valueOf(volumen));
        guardar();
    }

    public boolean micro() {
        return Boolean.parseBoolean(propiedades.getProperty("micro", "true"));
    }

    public void ponerMicro(boolean activo) {
        propiedades.setProperty("micro", String.valueOf(activo));
        guardar();
    }

    private synchronized void guardar() {
        try {
            Files.createDirectories(Config.CARPETA_DATOS);
            try (Writer escritor = Files.newBufferedWriter(Config.ARCHIVO_AJUSTES, StandardCharsets.UTF_8)) {
                propiedades.store(escritor, "Ajustes de Zero");
            }
        } catch (IOException e) {
            System.err.println("[!] No se pudieron guardar los ajustes: " + e.getMessage());
        }
    }
}
