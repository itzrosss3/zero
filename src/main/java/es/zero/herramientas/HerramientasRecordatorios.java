package es.zero.herramientas;

import java.sql.SQLException;

import com.google.gson.JsonObject;

import es.zero.recordatorios.Recordatorios;

/** Las tres herramientas de recordatorios. Todas usan el mismo servicio {@link Recordatorios}. */
public final class HerramientasRecordatorios {

    private HerramientasRecordatorios() {
    }

    public static class Crear extends HerramientaBase {
        private final Recordatorios recordatorios;

        public Crear(Recordatorios recordatorios) {
            super("crear_recordatorio", "Guarda un recordatorio permanente. Avisa a su hora y se repite cada vez "
                    + "que se enciende el PC hasta que se borre.");
            this.recordatorios = recordatorios;
            parametroTexto("texto", "Qué hay que recordar", true);
            parametroTexto("fecha_hora", "AAAA-MM-DD HH:MM, o cadena vacía si no hay hora", false);
        }

        @Override
        public String ejecutar(JsonObject args) throws SQLException {
            return recordatorios.crear(texto(args, "texto", ""), texto(args, "fecha_hora", ""));
        }
    }

    public static class Listar extends HerramientaBase {
        private final Recordatorios recordatorios;

        public Listar(Recordatorios recordatorios) {
            super("listar_recordatorios", "Lee todos los recordatorios guardados.");
            this.recordatorios = recordatorios;
        }

        @Override
        public String ejecutar(JsonObject args) throws SQLException {
            return recordatorios.listarTexto();
        }
    }

    public static class Borrar extends HerramientaBase {
        private final Recordatorios recordatorios;

        public Borrar(Recordatorios recordatorios) {
            super("borrar_recordatorio", "Borra un recordatorio (también cuando el usuario dice que ya lo ha hecho).");
            this.recordatorios = recordatorios;
            parametroTexto("referencia", "Número, unas palabras de su texto, o 'todos'", true);
        }

        @Override
        public String ejecutar(JsonObject args) throws SQLException {
            return recordatorios.borrar(texto(args, "referencia", ""));
        }
    }
}
