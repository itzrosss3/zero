package es.zero.ui;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * La conversación. Usa un {@link JTextPane}, que permite texto con varios colores y estilos.
 *
 * <pre>
 *   19:05  TÚ    › qué hora es
 *   19:05  ZERO  › Son las 19 y 5.
 *            » obtener_fecha_hora: Son las 19 y 5...
 * </pre>
 */
public class PanelChat extends JScrollPane {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final JTextPane texto = new JTextPane();
    private final StyledDocument documento = texto.getStyledDocument();
    private boolean respuestaAbierta = false; // ¿hay una respuesta de Zero llegando trozo a trozo?

    public PanelChat() {
        texto.setEditable(false);
        texto.setBackground(Colores.PANEL);
        texto.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setViewportView(texto);
        setBorder(BorderFactory.createLineBorder(Colores.BORDE));
        getViewport().setBackground(Colores.PANEL);
        getVerticalScrollBar().setUnitIncrement(16);
    }

    private static SimpleAttributeSet estilo(Color color, boolean negrita, int tamano) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        StyleConstants.setBold(s, negrita);
        StyleConstants.setFontFamily(s, Colores.FUENTE);
        StyleConstants.setFontSize(s, tamano);
        return s;
    }

    private void anadir(String cadena, SimpleAttributeSet estilo) {
        try {
            documento.insertString(documento.getLength(), cadena, estilo);
            texto.setCaretPosition(documento.getLength()); // baja hasta el final
        } catch (BadLocationException e) {
            throw new IllegalStateException(e); // no debería pasar nunca
        }
    }

    private void cabecera(String quien, Color color) {
        anadir(LocalTime.now().format(HORA) + "  ", estilo(Colores.TENUE, false, 13));
        anadir(String.format("%-5s › ", quien), estilo(color, true, 15));
    }

    private void cerrarRespuesta() {
        if (respuestaAbierta) {
            anadir("\n\n", estilo(Colores.TEXTO, false, 15));
            respuestaAbierta = false;
        }
    }

    /** Un mensaje completo (tuyo o de Zero). */
    public void mensaje(String quien, String contenido, Color color) {
        cerrarRespuesta();
        cabecera(quien, color);
        anadir(contenido + "\n\n", estilo(Colores.TEXTO, false, 15));
    }

    /** Una frase de una respuesta que todavía está llegando. */
    public void trozo(String quien, String frase, Color color) {
        if (!respuestaAbierta) {
            cabecera(quien, color);
            respuestaAbierta = true;
        }
        anadir(frase + " ", estilo(Colores.TEXTO, false, 15));
    }

    public void finRespuesta() {
        cerrarRespuesta();
    }

    /** Línea pequeña: información, acciones, avisos o errores. */
    public void nota(String simbolo, String contenido, Color color) {
        cerrarRespuesta();
        anadir("           " + simbolo + " " + contenido + "\n", estilo(color, false, 13));
    }
}
