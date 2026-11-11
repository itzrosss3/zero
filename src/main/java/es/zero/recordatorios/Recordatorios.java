package es.zero.recordatorios;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import es.zero.util.Fechas;

/**
 * Lógica de los recordatorios (la capa de "servicio").
 * <p>
 * Separación en capas: el {@link RepositorioRecordatorios} sabe de SQL; esta clase sabe de
 * reglas ("si la fecha ya pasó, avisa", "si hay varios que coinciden, pregunta")
 * y prepara los textos que dirá Zero.
 */
public class Recordatorios {

    private final RepositorioRecordatorios repositorio;

    public Recordatorios(RepositorioRecordatorios repositorio) {
        this.repositorio = repositorio;
    }

    public void alCambiar(Runnable oyente) {
        repositorio.alCambiar(oyente);
    }

    public List<Recordatorio> todos() throws SQLException {
        return repositorio.listar();
    }

    public String crear(String texto, String fechaHora) throws SQLException {
        LocalDateTime cuando = null;
        if (fechaHora != null && !fechaHora.isBlank()) {
            try {
                cuando = Fechas.parsear(fechaHora);
            } catch (DateTimeParseException e) {
                return "No entiendo la fecha '" + fechaHora + "'. Usa el formato AAAA-MM-DD HH:MM.";
            }
        }
        Recordatorio r = repositorio.crear(texto, cuando);
        if (cuando == null) {
            return "Recordatorio número %d guardado: %s, sin hora concreta.".formatted(r.id(), texto);
        }
        if (r.haPasado()) {
            return "Guardado, pero la hora indicada ya ha pasado: " + Fechas.cuandoHablado(cuando) + ".";
        }
        return "Recordatorio número %d guardado: %s, %s.".formatted(r.id(), texto, Fechas.cuandoHablado(cuando));
    }

    public String listarTexto() throws SQLException {
        List<Recordatorio> lista = repositorio.listar();
        if (lista.isEmpty()) {
            return "No hay recordatorios guardados.";
        }
        String detalle = lista.stream()
                .map(r -> "Número " + r.id() + ": " + describir(r) + ".")
                .collect(Collectors.joining(" "));
        return "Hay " + lista.size() + (lista.size() == 1 ? " recordatorio. " : " recordatorios. ") + detalle;
    }

    /** Borra por número, por palabras de su texto o "todos". */
    public String borrar(String referencia) throws SQLException {
        String ref = referencia.strip().toLowerCase();
        if (ref.equals("todos") || ref.equals("todo")) {
            return "Borrados los " + repositorio.borrarTodos() + " recordatorios.";
        }
        List<Recordatorio> candidatos;
        if (ref.matches("\\d+")) {
            Optional<Recordatorio> porId = repositorio.buscarPorId(Integer.parseInt(ref));
            candidatos = porId.map(List::of).orElse(List.of());
        } else {
            candidatos = repositorio.buscarPorTexto(ref);
        }

        if (candidatos.isEmpty()) {
            return "No encuentro ningún recordatorio que coincida con '" + referencia + "'.";
        }
        if (candidatos.size() > 1) {
            return "Hay varios que coinciden: " + candidatos.stream()
                    .map(r -> "número " + r.id() + ", " + r.texto())
                    .collect(Collectors.joining(". ")) + ". ¿Cuál borro?";
        }
        Recordatorio r = candidatos.get(0);
        repositorio.borrar(r.id());
        return "Borrado el recordatorio: " + r.texto() + ".";
    }

    /** Recordatorios cuya hora ha llegado ahora mismo. */
    public List<Recordatorio> vencidos() throws SQLException {
        return repositorio.marcarVencidos(LocalDateTime.now());
    }

    /** Lo que dice Zero al encender el PC. Vacío si no hay nada pendiente. */
    public Optional<String> resumenArranque() throws SQLException {
        List<Recordatorio> lista = repositorio.listar();
        if (lista.isEmpty()) {
            return Optional.empty();
        }
        String texto = "Tiene " + lista.size() + (lista.size() == 1 ? " recordatorio pendiente. " : " recordatorios pendientes. ")
                + lista.stream().map(r -> describir(r) + ".").collect(Collectors.joining(" "));
        vencidos(); // los que vencieron con el PC apagado quedan avisados aquí
        return Optional.of(texto);
    }

    public static String describir(Recordatorio r) {
        if (!r.tieneHora()) {
            return r.texto();
        }
        String cuando = Fechas.cuandoHablado(r.cuando());
        return r.haPasado() ? r.texto() + ", que era para " + cuando : r.texto() + ", " + cuando;
    }
}
