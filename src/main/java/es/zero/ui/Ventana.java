package es.zero.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import es.zero.Config;
import es.zero.audio.VozDisponible;
import es.zero.nucleo.Estado;
import es.zero.nucleo.Evento;
import es.zero.nucleo.Nucleo;
import es.zero.recordatorios.Recordatorios;

/**
 * La ventana principal de Zero.
 * <p>
 * REGLA DE ORO DE SWING: los componentes solo se tocan desde el "hilo de eventos" (EDT).
 * Como el núcleo trabaja en otro hilo, cada evento que llega se pasa al EDT con
 * {@link SwingUtilities#invokeLater(Runnable)}.
 */
public class Ventana extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Cómo se ve cada estado: texto, color y cuánto/qué rápido late el orbe. */
    private record Apariencia(String texto, Color color, double intensidad, double velocidad) {
    }

    private final Nucleo nucleo;
    private final Recordatorios recordatorios;

    private final Orbe orbe = new Orbe();
    private final JLabel lblEstado = new JLabel("Cargando...");
    private final IconoMicrofono iconoMicro = new IconoMicrofono(22, Colores.PANEL2);
    private final JButton btnMicro = new JButton(iconoMicro);
    private final PanelChat chat = new PanelChat();
    private final JTextField campoTexto = new JTextField();
    private final PanelRecordatorios panelRecordatorios;
    private final JLabel lblSistema = new JLabel();
    private DialogoConfirmacion dialogo;

    public Ventana(Nucleo nucleo, Recordatorios recordatorios) {
        super(Config.NOMBRE_ASISTENTE + " v" + Config.VERSION); // texto de la barra de título
        this.nucleo = nucleo;
        this.recordatorios = recordatorios;
        this.panelRecordatorios = new PanelRecordatorios(this::borrarRecordatorio);

        setIconImages(Iconos.imagenes()); // el "favicon" de la ventana y el icono de la barra de tareas
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(860, 540));
        setLocationRelativeTo(null); // centrada en la pantalla

        JPanel raiz = new JPanel(new BorderLayout(18, 0));
        raiz.setBackground(Colores.FONDO);
        raiz.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        raiz.add(construirPrincipal(), BorderLayout.CENTER);
        raiz.add(construirLateral(), BorderLayout.EAST);
        setContentPane(raiz);

        // El núcleo nos avisará de todo lo que pase (method reference: this::recibir)
        nucleo.ponerOyente(this::recibir);
        recordatorios.alCambiar(() -> SwingUtilities.invokeLater(this::refrescarRecordatorios));
        refrescarRecordatorios();
        pintarBotonMicro();
        aplicarEstado(Estado.CARGANDO);
    }

    // =========================================================================
    // Construcción de la interfaz
    // =========================================================================

    private JPanel construirPrincipal() {
        JPanel principal = new JPanel(new BorderLayout(0, 12));
        principal.setOpaque(false);

        // ---------- cabecera: orbe + nombre + estado + botón de micro ----------
        PanelRedondeado cabecera = new PanelRedondeado(new BorderLayout(14, 0), Colores.PANEL, 18);
        cabecera.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 16));
        JPanel titulos = new JPanel();
        titulos.setOpaque(false);
        titulos.setLayout(new BoxLayout(titulos, BoxLayout.Y_AXIS));
        // "ZERO" en grande y "v1.0" en pequeño al lado
        JLabel nombre = new JLabel(Config.NOMBRE_ASISTENTE.toUpperCase());
        nombre.setFont(Colores.fuenteTitulo(28, true));
        nombre.setForeground(Colores.TEXTO);
        JLabel version = new JLabel("v" + Config.VERSION);
        version.setFont(Colores.fuente(13, false));
        version.setForeground(Colores.TENUE);
        JPanel filaNombre = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        filaNombre.setOpaque(false);
        filaNombre.setAlignmentX(LEFT_ALIGNMENT);
        filaNombre.add(nombre);
        filaNombre.add(javax.swing.Box.createHorizontalStrut(8));
        filaNombre.add(version);
        lblEstado.setFont(Colores.fuente(14, false));
        lblEstado.setAlignmentX(LEFT_ALIGNMENT);
        titulos.add(javax.swing.Box.createVerticalGlue());
        titulos.add(filaNombre);
        titulos.add(lblEstado);
        titulos.add(javax.swing.Box.createVerticalGlue());

        // Botón redondo solo con el icono del micro (estilo Discord)
        btnMicro.setPreferredSize(new Dimension(46, 46));
        btnMicro.setFocusPainted(false);
        btnMicro.putClientProperty("FlatLaf.style", "arc: 999; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0");
        btnMicro.addActionListener(e -> {
            nucleo.ponerMicro(!nucleo.micActivo());
            pintarBotonMicro();
        });
        JPanel derecha = new JPanel(new GridBagLayout()); // centra el botón verticalmente
        derecha.setOpaque(false);
        derecha.add(btnMicro);

        cabecera.add(orbe, BorderLayout.WEST);
        cabecera.add(titulos, BorderLayout.CENTER);
        cabecera.add(derecha, BorderLayout.EAST);

        // ---------- barra de escribir ----------
        campoTexto.setFont(Colores.fuente(15, false));
        campoTexto.putClientProperty("JTextField.placeholderText",
                "Escribe a " + Config.NOMBRE_ASISTENTE + "...  (Enter para enviar)");
        campoTexto.setPreferredSize(new Dimension(100, 44));
        campoTexto.addActionListener(e -> enviar()); // Enter en un JTextField dispara ActionListener
        JButton btnEnviar = new JButton("Enviar  ›");
        btnEnviar.setFont(Colores.fuente(14, true));
        btnEnviar.setBackground(Colores.ACENTO2);
        btnEnviar.setForeground(new Color(0x04121C));
        btnEnviar.setPreferredSize(new Dimension(110, 44));
        btnEnviar.addActionListener(e -> enviar());
        JPanel barra = new JPanel(new BorderLayout(10, 0));
        barra.setOpaque(false);
        barra.add(campoTexto, BorderLayout.CENTER);
        barra.add(btnEnviar, BorderLayout.EAST);

        principal.add(cabecera, BorderLayout.NORTH);
        principal.add(chat, BorderLayout.CENTER);
        principal.add(barra, BorderLayout.SOUTH);
        return principal;
    }

    private JPanel construirLateral() {
        PanelRedondeado lateral = new PanelRedondeado(new GridBagLayout(), Colores.PANEL, 18);
        lateral.setPreferredSize(new Dimension(320, 100));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);

        c.gridy = 0;
        lateral.add(titulo("RECORDATORIOS"), c);
        c.gridy = 1;
        c.weighty = 1; // esta fila se estira y ocupa el espacio libre
        lateral.add(panelRecordatorios, c);
        c.weighty = 0;

        c.gridy = 2;
        lateral.add(titulo("VOZ"), c);
        c.gridy = 3;
        lateral.add(construirVoz(), c);

        c.gridy = 4;
        lateral.add(titulo("SISTEMA"), c);
        lblSistema.setFont(Colores.fuenteMono(12));
        lblSistema.setForeground(Colores.TENUE);
        lblSistema.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        mostrarSistema(new Evento.InfoSistema("...", "...", "...", "..."));
        c.gridy = 5;
        lateral.add(lblSistema, c);
        return lateral;
    }

    private JPanel construirVoz() {
        JPanel panel = new JPanel(new BorderLayout(8, 6));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

        JComboBox<VozDisponible> selector = new JComboBox<>(Config.VOCES.toArray(new VozDisponible[0]));
        selector.setSelectedItem(nucleo.ajustes().voz());
        selector.addActionListener(e -> nucleo.cambiarVoz((VozDisponible) selector.getSelectedItem()));
        JButton probar = new JButton("Probar");
        probar.addActionListener(e -> nucleo.probarVoz());

        // JSlider trabaja con enteros: 75..135 representa 0,75x..1,35x
        JPanel velocidad = deslizador("Velocidad  ·  %.2fx", 75, 135,
                (int) Math.round(nucleo.ajustes().velocidad() * 100),
                valor -> valor / 100.0,
                valor -> nucleo.ponerVelocidad(valor / 100.0));
        // 0..100 representa el volumen en %
        JPanel volumen = deslizador("Volumen  ·  %.0f%%", 0, 100,
                (int) Math.round(nucleo.ajustes().volumen() * 100),
                valor -> valor * 1.0,
                valor -> nucleo.ponerVolumen(valor / 100.0));

        JPanel fila = new JPanel(new BorderLayout(8, 0));
        fila.setOpaque(false);
        fila.add(selector, BorderLayout.CENTER);
        fila.add(probar, BorderLayout.EAST);

        // Velocidad y volumen uno al lado del otro: GridLayout de 1 fila y 2 columnas
        JPanel deslizadores = new JPanel(new java.awt.GridLayout(1, 2, 14, 0));
        deslizadores.setOpaque(false);
        deslizadores.add(velocidad);
        deslizadores.add(volumen);

        panel.add(fila, BorderLayout.NORTH);
        panel.add(deslizadores, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Crea una etiqueta + un JSlider. Así no repetimos el mismo código para velocidad y volumen.
     *
     * @param formato    texto de la etiqueta, con un %... para el valor
     * @param aMostrar   convierte el valor del slider en el número que se enseña
     * @param alSoltar   qué hacer cuando sueltas el ratón (recibe el valor del slider)
     */
    private static JPanel deslizador(String formato, int min, int max, int inicial,
            java.util.function.IntToDoubleFunction aMostrar, java.util.function.IntConsumer alSoltar) {
        JLabel etiqueta = new JLabel(formato.formatted(aMostrar.applyAsDouble(inicial)));
        etiqueta.setForeground(Colores.TENUE);
        etiqueta.setFont(Colores.fuente(12, false));
        JSlider slider = new JSlider(min, max, inicial);
        slider.setOpaque(false);
        slider.addChangeListener(e -> {
            etiqueta.setText(formato.formatted(aMostrar.applyAsDouble(slider.getValue())));
            if (!slider.getValueIsAdjusting()) { // solo al soltar el ratón
                alSoltar.accept(slider.getValue());
            }
        });
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(etiqueta, BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        return panel;
    }

    private JLabel titulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(Colores.fuente(12, true));
        l.setForeground(Colores.TENUE);
        l.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        return l;
    }

    // =========================================================================
    // Eventos del núcleo
    // =========================================================================

    /** Lo llama el núcleo desde SU hilo: lo pasamos al hilo de Swing. */
    private void recibir(Evento evento) {
        SwingUtilities.invokeLater(() -> procesar(evento));
    }

    private void procesar(Evento evento) {
        String nombreZero = Config.NOMBRE_ASISTENTE.toUpperCase();
        // switch con flechas (Java 14+): más limpio, sin break
        switch (evento.tipo()) {
            case ESTADO -> aplicarEstado((Estado) evento.dato());
            case USUARIO -> chat.mensaje("TÚ", (String) evento.dato(), Colores.TU);
            case ZERO -> chat.mensaje(nombreZero, (String) evento.dato(), Colores.ACENTO);
            case ZERO_TROZO -> chat.trozo(nombreZero, (String) evento.dato(), Colores.ACENTO);
            case ZERO_FIN -> chat.finRespuesta();
            case HERRAMIENTA -> chat.nota("»", (String) evento.dato(), Colores.OK);
            case INFO -> chat.nota("·", (String) evento.dato(), Colores.TENUE);
            case IGNORADO -> chat.nota("·", "oído, no era para mí: " + evento.dato(), Colores.TENUE);
            case AVISO -> chat.nota("!", (String) evento.dato(), Colores.AVISO);
            case ERROR -> chat.nota("×", (String) evento.dato(), Colores.ERROR);
            case SISTEMA -> mostrarSistema((Evento.InfoSistema) evento.dato());
            case CONFIRMAR -> abrirConfirmacion((String) evento.dato());
            case CONFIRMAR_FIN -> cerrarConfirmacion();
            case SALIR -> new javax.swing.Timer(500, e -> System.exit(0)).start();
        }
    }

    private void aplicarEstado(Estado estado) {
        Apariencia a = switch (estado) {
            case CARGANDO -> new Apariencia("Cargando...", Colores.TENUE, 2, 2);
            case CALIBRANDO -> new Apariencia("Calibrando micro · silencio, por favor", Colores.AVISO, 2, 2);
            case ESPERA -> new Apariencia("En espera · di \"" + Config.NOMBRE_ASISTENTE + ", ...\"", Colores.ACENTO2, 1, 1.2);
            case ESCUCHANDO -> new Apariencia("Escuchando...", Colores.OK, 4, 4);
            case ENTENDIENDO -> new Apariencia("Entendiendo...", Colores.OK, 3, 6);
            case PENSANDO -> new Apariencia("Pensando...", Colores.AVISO, 3, 6);
            case HABLANDO -> new Apariencia("Hablando...", Colores.ACENTO, 5, 7);
            case CONFIRMANDO -> new Apariencia("Esperando confirmación", Colores.AVISO, 4, 3);
            case SILENCIADO -> new Apariencia("Micro silenciado · escríbeme abajo", Colores.TENUE, 0, 1);
            case SIN_MICRO -> new Apariencia("Sin micrófono · escríbeme abajo", Colores.TENUE, 0, 1);
            case ERROR -> new Apariencia("Error · revisa el mensaje en rojo", Colores.ERROR, 1, 1);
        };
        lblEstado.setText(a.texto());
        lblEstado.setForeground(a.color());
        orbe.ponerEstado(a.color(), a.intensidad(), a.velocidad());
    }

    private void mostrarSistema(Evento.InfoSistema info) {
        lblSistema.setText("<html>Oído&nbsp;&nbsp;&nbsp;&nbsp;·&nbsp;&nbsp;" + info.oido()
                + "<br>Voz&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;·&nbsp;&nbsp;" + info.voz()
                + "<br>Cerebro&nbsp;·&nbsp;&nbsp;" + info.cerebro()
                + "<br>Micro&nbsp;&nbsp;&nbsp;·&nbsp;&nbsp;" + info.micro() + "</html>");
    }

    // =========================================================================
    // Acciones del usuario
    // =========================================================================

    private void enviar() {
        String texto = campoTexto.getText().strip();
        if (!texto.isEmpty()) {
            campoTexto.setText("");
            nucleo.enviarTexto(texto);
        }
    }

    private void pintarBotonMicro() {
        boolean silenciado = !nucleo.micActivo();
        iconoMicro.ponerSilenciado(silenciado);
        btnMicro.setBackground(silenciado ? new Color(0x2A1215) : Colores.PANEL2); // fondo rojizo si está silenciado
        btnMicro.setToolTipText(silenciado ? "Activar micrófono" : "Silenciar micrófono");
        btnMicro.repaint();
    }

    private void refrescarRecordatorios() {
        try {
            panelRecordatorios.mostrar(recordatorios.todos());
        } catch (SQLException e) {
            chat.nota("×", "No puedo leer los recordatorios: " + e.getMessage(), Colores.ERROR);
        }
    }

    private void borrarRecordatorio(int id) {
        try {
            chat.nota("·", recordatorios.borrar(String.valueOf(id)), Colores.TENUE);
        } catch (SQLException e) {
            chat.nota("×", "No he podido borrarlo: " + e.getMessage(), Colores.ERROR);
        }
    }

    private void abrirConfirmacion(String pregunta) {
        cerrarConfirmacion();
        setExtendedState(NORMAL);
        toFront();
        dialogo = new DialogoConfirmacion(this, pregunta, nucleo::responderConfirmacion);
        dialogo.setVisible(true);
    }

    private void cerrarConfirmacion() {
        if (dialogo != null) {
            dialogo.dispose();
            dialogo = null;
        }
    }
}
