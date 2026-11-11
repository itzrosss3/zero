package es.zero.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

import javax.swing.JPanel;

/**
 * Un JPanel con las esquinas redondeadas y un borde fino.
 * <p>
 * Ejemplo de cómo personalizar un componente de Swing: se hereda de él y se sobrescribe
 * {@code paintComponent}, que es el método que Swing llama cada vez que tiene que dibujarlo.
 */
public class PanelRedondeado extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Color fondo;
    private final int radio;

    public PanelRedondeado(LayoutManager layout, Color fondo, int radio) {
        super(layout);
        this.fondo = fondo;
        this.radio = radio;
        setOpaque(false); // el fondo lo pintamos nosotros
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // bordes suaves
        g2.setColor(fondo);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radio, radio);
        g2.setColor(Colores.BORDE);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radio, radio);
        g2.dispose();
        super.paintComponent(g);
    }
}
