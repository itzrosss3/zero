package es.zero.herramientas;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.sun.management.OperatingSystemMXBean;

/** Uso de procesador, memoria y gráfica. */
public class EstadoSistema extends HerramientaBase {

    public EstadoSistema() {
        super("estado_sistema", "Informa del uso de procesador, memoria y tarjeta gráfica.");
    }

    @Override
    public String ejecutar(JsonObject argumentos) throws Exception {
        // MXBean: Java expone datos del sistema operativo a través de "beans" de gestión
        OperatingSystemMXBean so = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        so.getCpuLoad();          // la primera lectura no es fiable
        Thread.sleep(500);
        double cpu = so.getCpuLoad() * 100;
        double total = so.getTotalMemorySize() / 1e9;
        double usada = total - so.getFreeMemorySize() / 1e9;

        String texto = "Procesador al %.0f por ciento. Memoria usada %.1f de %.0f gigas.".formatted(cpu, usada, total);
        return texto + grafica();
    }

    /** Pregunta a nvidia-smi (viene con el driver de NVIDIA) por el estado de la gráfica. */
    private static String grafica() {
        try {
            Process p = new ProcessBuilder("nvidia-smi",
                    "--query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu",
                    "--format=csv,noheader,nounits").start();
            String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (!p.waitFor(3, TimeUnit.SECONDS) || salida.isEmpty()) {
                return "";
            }
            String[] d = salida.split("\n")[0].split(",");
            return " Gráfica al %s por ciento, %.1f de %.0f gigas de memoria, a %s grados.".formatted(
                    d[0].strip(), Integer.parseInt(d[1].strip()) / 1024.0,
                    Integer.parseInt(d[2].strip()) / 1024.0, d[3].strip());
        } catch (Exception e) {
            return "";
        }
    }
}
