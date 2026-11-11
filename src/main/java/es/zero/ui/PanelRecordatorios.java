package es.zero.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import es.zero.recordatorios.Recordatorio;
import es.zero.util.Fechas;

/** Lista de recordatorios de la barra lateral, con un botón × para borrar cada uno. */
public class PanelRecordatorios extends JScrollPane {

    private static final long serialVersionUID = 1L;

    private final JPanel lista = new JPanel();
    private final IntConsumer alBorrar; // función que recibe el id a borrar

    public PanelRecordatorios(IntConsumer alBorrar) {
        this.alBorrar = alBorrar;
        lista.setLayout(new BoxLayout(lista, BoxLayout.Y_AXIS));
        lista.setBackground(Colores.PANEL);
        lista.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        JPanel contenedor = new JPanel(new BorderLayout()); // evita que las filas se estiren
        contenedor.setBackground(Colores.PANEL);
        contenedor.add(lista, BorderLayout.NORTH);
        setViewportView(contenedor);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(Colores.PANEL);
        getVerticalScrollBar().setUnitIncrement(16);
    }

    public void mostrar(List<Recordatorio> recordatorios) {
        lista.removeAll();
        if (recordatorios.isEmpty()) {
            JLabel vacio = new JLabel("<html>Sin recordatorios.<br>Di: \"Zero, recuérdame...\"</html>");
            vacio.setForeground(Colores.TENUE);
            vacio.setFont(Colores.fuente(13, false));
            lista.add(vacio);
        }
        for (Recordatorio r : recordatorios) {
            lista.add(fila(r));
            lista.add(Box.createVerticalStrut(8));
        }
        lista.revalidate(); // recalcula la colocación
        lista.repaint();
    }

    private JPanel fila(Recordatorio r) {
        PanelRedondeado fila = new PanelRedondeado(new BorderLayout(6, 0), Colores.PANEL2, 14);
        fila.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 6));

        JPanel textos = new JPanel(new GridLayout(0, 1, 0, 2));
        textos.setOpaque(false);
        JLabel texto = new JLabel("<html><body style='width:190px'>" + escapar(r.texto()) + "</body></html>");
        texto.setForeground(Colores.TEXTO);
        texto.setFont(Colores.fuente(13, true));
        textos.add(texto);

        String cuando = r.tieneHora() ? capitalizar(Fechas.cuandoHablado(r.cuando())) : "Sin hora";
        JLabel lblCuando = new JLabel(cuando);
        lblCuando.setForeground(r.haPasado() ? Colores.AVISO : Colores.TENUE);
        lblCuando.setFont(Colores.fuente(12, false));
        textos.add(lblCuando);

        JButton borrar = new JButton("×");
        borrar.setFont(Colores.fuente(16, false));
        borrar.setForeground(Colores.TENUE);
        borrar.setContentAreaFilled(false);
        borrar.setBorderPainted(false);
        borrar.setFocusPainted(false);
        borrar.setToolTipText("Borrar");
        borrar.setPreferredSize(new Dimension(32, 32));
        borrar.addActionListener(e -> alBorrar.accept(r.id()));

        fila.add(textos, BorderLayout.CENTER);
        fila.add(borrar, BorderLayout.EAST);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, fila.getPreferredSize().height));
        return fila;
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
