package es.zero.herramientas;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Habla con winget, el gestor de paquetes oficial de Windows.
 * Es como "apt" en Linux o "npm" en JavaScript, pero para programas de Windows.
 */
final class Winget {

    private Winget() {
    }

    record Resultado(int codigo, String salida) {
        boolean ok() {
            return codigo == 0;
        }
    }

    record Paquete(String nombre, String id, String version) {
    }

    private static final Pattern PATRON_ID = Pattern.compile("[A-Za-z][\\w\\-+]*(\\.[\\w\\-+]+)+");

    static Resultado ejecutar(Duration limite, String... argumentos) throws IOException, InterruptedException {
        List<String> orden = new ArrayList<>();
        orden.add("winget");
        orden.addAll(Arrays.asList(argumentos));
        orden.add("--accept-source-agreements");
        orden.add("--disable-interactivity");

        Process proceso;
        try {
            proceso = new ProcessBuilder(orden).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IOException("winget no está disponible en este Windows");
        }
        // La salida se lee en otro hilo: si nadie la lee, el proceso puede quedarse bloqueado
        CompletableFuture<String> salida = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        if (!proceso.waitFor(limite.toSeconds(), TimeUnit.SECONDS)) {
            proceso.destroyForcibly();
            throw new IOException("winget ha tardado demasiado");
        }
        try {
            // winget dibuja barras de progreso con '\r': nos quedamos con lo último de cada línea
            String texto = salida.get().lines()
                    .map(l -> l.substring(l.lastIndexOf('\r') + 1))
                    .collect(Collectors.joining("\n"));
            return new Resultado(proceso.exitValue(), texto);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
    }

    /** Convierte la tabla que imprime "winget search" en una lista de paquetes. */
    static List<Paquete> parsearTabla(String salida) {
        List<String> lineas = salida.lines().toList();
        int separador = -1;
        for (int i = 1; i < lineas.size(); i++) {
            if (lineas.get(i).strip().matches("-{10,}")) {
                separador = i;
                break;
            }
        }
        List<Paquete> paquetes = new ArrayList<>();
        if (separador < 0) {
            return paquetes;
        }
        // La cabecera dice dónde empieza cada columna (Nombre, Id, Versión...)
        List<Integer> inicios = new ArrayList<>();
        Matcher palabra = Pattern.compile("\\S+").matcher(lineas.get(separador - 1));
        while (palabra.find()) {
            inicios.add(palabra.start());
        }
        for (String linea : lineas.subList(separador + 1, lineas.size())) {
            if (linea.isBlank()) {
                continue;
            }
            List<String> columnas = new ArrayList<>();
            for (int c = 0; c < inicios.size(); c++) {
                int desde = Math.min(inicios.get(c), linea.length());
                int hasta = c + 1 < inicios.size() ? Math.min(inicios.get(c + 1), linea.length()) : linea.length();
                columnas.add(linea.substring(desde, hasta).strip());
            }
            if (columnas.size() >= 3 && !columnas.get(1).isEmpty() && !columnas.get(1).contains(" ")) {
                paquetes.add(new Paquete(columnas.get(0), columnas.get(1), columnas.get(2)));
                continue;
            }
            // Columnas descuadradas: buscamos el Id como la primera palabra tipo "Editor.Programa"
            String[] palabras = linea.strip().split("\\s+");
            for (int i = 1; i < palabras.length; i++) {
                if (PATRON_ID.matcher(palabras[i]).matches()) {
                    String nombre = String.join(" ", Arrays.copyOfRange(palabras, 0, i));
                    paquetes.add(new Paquete(nombre, palabras[i], i + 1 < palabras.length ? palabras[i + 1] : ""));
                    break;
                }
            }
        }
        return paquetes;
    }
}
