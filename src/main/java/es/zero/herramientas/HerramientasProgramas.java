package es.zero.herramientas;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;

/**
 * Buscar, instalar y desinstalar programas con winget.
 * <p>
 * IMPORTANTE: instalar y desinstalar SIEMPRE piden confirmación al usuario a través del
 * {@link Asistente}. Así, aunque Zero entienda mal una frase, nunca toca nada sin tu "sí".
 */
public final class HerramientasProgramas {

    private HerramientasProgramas() {
    }

    public static class Buscar extends HerramientaBase {
        public Buscar() {
            super("buscar_programa", "Busca programas instalables con winget y devuelve sus Id.");
            parametroTexto("nombre", "Programa a buscar, p. ej. 'Temurin JDK' o 'Visual Studio Code'", true);
        }

        @Override
        public String ejecutar(JsonObject args) throws Exception {
            String nombre = texto(args, "nombre", "");
            Winget.Resultado r = Winget.ejecutar(Duration.ofSeconds(60), "search", nombre);
            List<Winget.Paquete> paquetes = Winget.parsearTabla(r.salida());
            if (paquetes.isEmpty()) {
                return "No he encontrado ningún programa llamado '" + nombre + "'.";
            }
            return "Resultados: " + paquetes.stream().limit(6)
                    .map(p -> "%s (Id: %s, versión %s)".formatted(p.nombre(), p.id(), p.version()))
                    .collect(Collectors.joining(" | "));
        }
    }

    public static class Instalar extends HerramientaBase {
        private final Asistente asistente;

        public Instalar(Asistente asistente) {
            super("instalar_programa", "Instala un programa con winget. Necesita el Id exacto obtenido con "
                    + "buscar_programa. Pide confirmación al usuario.");
            this.asistente = asistente;
            parametroTexto("id_paquete", "Id exacto, p. ej. EclipseAdoptium.Temurin.21.JDK", true);
            parametroTexto("nombre", "Nombre legible del programa", false);
        }

        @Override
        public String ejecutar(JsonObject args) {
            String id = texto(args, "id_paquete", "");
            String etiqueta = texto(args, "nombre", id);
            if (!asistente.confirmar("¿Instalo " + etiqueta + "?\nPaquete: " + id)) {
                return "El usuario ha cancelado la instalación.";
            }
            // La instalación puede tardar minutos: se hace en otro hilo y se avisa al terminar
            Thread hilo = new Thread(() -> {
                asistente.avisar("Instalando " + etiqueta + "...");
                try {
                    Winget.Resultado r = Winget.ejecutar(Duration.ofMinutes(30),
                            "install", "--id", id, "--exact", "--silent", "--accept-package-agreements");
                    String salida = r.salida().toLowerCase();
                    if (r.ok()) {
                        asistente.hablar(etiqueta + " se ha instalado correctamente.");
                    } else if (salida.contains("ya está instalado") || salida.contains("already installed")
                            || salida.contains("no se encontró ninguna actualización")) {
                        asistente.hablar(etiqueta + " ya estaba instalado.");
                    } else {
                        asistente.hablar("La instalación de " + etiqueta + " ha fallado. Tiene los detalles en pantalla.");
                        asistente.avisar(ultimos(r.salida(), 600));
                    }
                } catch (Exception e) {
                    asistente.hablar("No he podido instalar " + etiqueta + ": " + e.getMessage());
                }
            }, "instalacion");
            hilo.setDaemon(true);
            hilo.start();
            return "Instalando " + etiqueta + " en segundo plano. Avisaré al terminar.";
        }
    }

    public static class Desinstalar extends HerramientaBase {
        private final Asistente asistente;

        public Desinstalar(Asistente asistente) {
            super("desinstalar_programa", "Desinstala un programa con winget. Pide confirmación al usuario.");
            this.asistente = asistente;
            parametroTexto("id_paquete", "Id exacto del programa", true);
            parametroTexto("nombre", "Nombre legible del programa", false);
        }

        @Override
        public String ejecutar(JsonObject args) {
            String id = texto(args, "id_paquete", "");
            String etiqueta = texto(args, "nombre", id);
            if (!asistente.confirmar("¿Desinstalo " + etiqueta + "?\nPaquete: " + id)) {
                return "El usuario ha cancelado la desinstalación.";
            }
            Thread hilo = new Thread(() -> {
                asistente.avisar("Desinstalando " + etiqueta + "...");
                try {
                    Winget.Resultado r = Winget.ejecutar(Duration.ofMinutes(30),
                            "uninstall", "--id", id, "--exact", "--silent");
                    if (r.ok()) {
                        asistente.hablar(etiqueta + " desinstalado.");
                    } else {
                        asistente.hablar("No he podido desinstalar " + etiqueta + ". Tiene los detalles en pantalla.");
                        asistente.avisar(ultimos(r.salida(), 600));
                    }
                } catch (Exception e) {
                    asistente.hablar("No he podido desinstalar " + etiqueta + ": " + e.getMessage());
                }
            }, "desinstalacion");
            hilo.setDaemon(true);
            hilo.start();
            return "Desinstalando " + etiqueta + " en segundo plano.";
        }
    }

    private static String ultimos(String texto, int caracteres) {
        String t = texto.strip();
        return t.length() <= caracteres ? t : t.substring(t.length() - caracteres);
    }
}
