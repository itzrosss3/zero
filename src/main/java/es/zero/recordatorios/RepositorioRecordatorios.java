package es.zero.recordatorios;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import es.zero.util.Fechas;

/**
 * Acceso a la base de datos de recordatorios (SQLite) con JDBC.
 * <p>
 * Es un "repositorio" o DAO: la ÚNICA clase que escribe SQL. El resto del programa
 * pide y guarda objetos {@link Recordatorio} sin saber que hay una base de datos detrás.
 * <p>
 * SQLite guarda toda la base de datos en un solo archivo (datos/zero.db). Puedes abrirlo
 * con "DB Browser for SQLite" para ver la tabla y lanzar tus propias consultas.
 */
public class RepositorioRecordatorios {

    private final String url;
    private final List<Runnable> oyentes = new CopyOnWriteArrayList<>();

    public RepositorioRecordatorios(Path archivo) throws IOException, SQLException {
        Files.createDirectories(archivo.toAbsolutePath().getParent());
        this.url = "jdbc:sqlite:" + archivo.toAbsolutePath();
        crearTabla();
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void crearTabla() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS recordatorios (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    texto   TEXT    NOT NULL,
                    cuando  TEXT,
                    avisado INTEGER NOT NULL DEFAULT 0,
                    creado  TEXT    NOT NULL
                )""";
        try (Connection c = conectar(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** Patrón observador: quien se apunte aquí se entera de cada cambio (la interfaz, por ejemplo). */
    public void alCambiar(Runnable oyente) {
        oyentes.add(oyente);
    }

    private void avisarCambio() {
        oyentes.forEach(Runnable::run);
    }

    // ===================== CRUD =====================

    public Recordatorio crear(String texto, LocalDateTime cuando) throws SQLException {
        // PreparedStatement con "?" -> nunca concatenes texto del usuario en SQL (inyección SQL)
        String sql = "INSERT INTO recordatorios (texto, cuando, avisado, creado) VALUES (?, ?, 0, ?)";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, texto);
            ps.setString(2, cuando == null ? null : cuando.format(Fechas.FORMATO));
            ps.setString(3, LocalDateTime.now().format(Fechas.FORMATO));
            ps.executeUpdate();
            // last_insert_rowid(): el id que SQLite acaba de asignar en ESTA conexión
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                Recordatorio nuevo = new Recordatorio(rs.getInt(1), texto, cuando, false);
                avisarCambio();
                return nuevo;
            }
        }
    }

    /** Todos, primero los que tienen hora (de antes a después) y al final los que no. */
    public List<Recordatorio> listar() throws SQLException {
        String sql = "SELECT id, texto, cuando, avisado FROM recordatorios ORDER BY cuando IS NULL, cuando, id";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            return leerFilas(ps);
        }
    }

    public Optional<Recordatorio> buscarPorId(int id) throws SQLException {
        String sql = "SELECT id, texto, cuando, avisado FROM recordatorios WHERE id = ?";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return leerFilas(ps).stream().findFirst();
        }
    }

    public List<Recordatorio> buscarPorTexto(String fragmento) throws SQLException {
        String sql = "SELECT id, texto, cuando, avisado FROM recordatorios WHERE lower(texto) LIKE ?";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + fragmento.toLowerCase() + "%");
            return leerFilas(ps);
        }
    }

    public boolean borrar(int id) throws SQLException {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement("DELETE FROM recordatorios WHERE id = ?")) {
            ps.setInt(1, id);
            boolean borrado = ps.executeUpdate() > 0;
            if (borrado) {
                avisarCambio();
            }
            return borrado;
        }
    }

    public int borrarTodos() throws SQLException {
        try (Connection c = conectar(); Statement st = c.createStatement()) {
            int n = st.executeUpdate("DELETE FROM recordatorios");
            avisarCambio();
            return n;
        }
    }

    /** Recordatorios cuya hora ha llegado y no se han avisado. Los marca como avisados. */
    public List<Recordatorio> marcarVencidos(LocalDateTime ahora) throws SQLException {
        String consulta = "SELECT id, texto, cuando, avisado FROM recordatorios "
                + "WHERE cuando IS NOT NULL AND avisado = 0 AND cuando <= ?";
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(consulta)) {
            ps.setString(1, ahora.format(Fechas.FORMATO));
            List<Recordatorio> vencidos = leerFilas(ps);
            if (!vencidos.isEmpty()) {
                try (PreparedStatement upd = c.prepareStatement("UPDATE recordatorios SET avisado = 1 WHERE id = ?")) {
                    for (Recordatorio r : vencidos) {
                        upd.setInt(1, r.id());
                        upd.addBatch(); // se acumulan y se ejecutan todos juntos
                    }
                    upd.executeBatch();
                }
            }
            return vencidos;
        }
    }

    /** Convierte cada fila del ResultSet en un objeto Recordatorio. */
    private static List<Recordatorio> leerFilas(PreparedStatement ps) throws SQLException {
        List<Recordatorio> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cuando = rs.getString("cuando");
                lista.add(new Recordatorio(
                        rs.getInt("id"),
                        rs.getString("texto"),
                        cuando == null ? null : LocalDateTime.parse(cuando, Fechas.FORMATO),
                        rs.getInt("avisado") == 1));
            }
        }
        return lista;
    }
}
