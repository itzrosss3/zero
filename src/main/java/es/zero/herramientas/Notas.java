package es.zero.herramientas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;

import com.google.gson.JsonObject;

import es.zero.Config;
import es.zero.util.Fechas;

/**
 * Dos herramientas relacionadas en un mismo archivo: clases ANIDADAS estáticas.
 * Se usan como {@code new Notas.Tomar()} y {@code new Notas.Leer()}.
 */
public final class Notas {

    private Notas() {
    }

    public static class Tomar extends HerramientaBase {
        public Tomar() {
            super("tomar_nota", "Guarda una nota de texto.");
            parametroTexto("texto", "Contenido de la nota", true);
        }

        @Override
        public String ejecutar(JsonObject argumentos) throws IOException {
            Files.createDirectories(Config.CARPETA_DATOS);
            String linea = "[" + LocalDateTime.now().format(Fechas.FORMATO) + "] "
                    + texto(argumentos, "texto", "") + System.lineSeparator();
            Files.writeString(Config.ARCHIVO_NOTAS, linea, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Nota guardada.";
        }
    }

    public static class Leer extends HerramientaBase {
        public Leer() {
            super("leer_notas", "Lee las últimas notas guardadas.");
        }

        @Override
        public String ejecutar(JsonObject argumentos) throws IOException {
            if (!Files.exists(Config.ARCHIVO_NOTAS)) {
                return "No hay notas guardadas.";
            }
            List<String> lineas = Files.readAllLines(Config.ARCHIVO_NOTAS, StandardCharsets.UTF_8);
            List<String> ultimas = lineas.subList(Math.max(0, lineas.size() - 10), lineas.size());
            return "Últimas notas: " + String.join(" ", ultimas);
        }
    }
}
