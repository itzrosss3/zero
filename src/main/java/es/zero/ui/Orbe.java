package es.zero.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * El círculo animado que muestra el estado de Zero.
 * <p>
 * Animación en Swing: un {@link Timer} llama a repaint() 30 veces por segundo y
 * paintComponent dibuja el círculo con un tamaño que oscila con una función seno.
 */
public class Orbe extends JComponent {

    private static final long serialVersionUID = 1L;

    private Color color = Colores.TENUE;
    private double intensidad = 2;
    private double velocidad = 2;
    private double fase = 0;

    public Orbe() {
        setPreferredSize(new Dimension(76, 76));
        new Timer(33, e -> {
            fase += 0.033 * velocidad;
            repaint();
        }).start();
    }

    public void ponerEstado(Color color, double intensidad, double velocidad) {
        this.color = color;
        this.intensidad = intensidad;
        this.velocidad = velocidad;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double cx = getWidth() / 2.0;
        double cy = getHeight() / 2.0;
        double pulso = Math.sin(fase * Math.PI);

        double radioHalo = 27 + intensidad * 1.2 * (0.5 + 0.5 * pulso);
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90)); // semitransparente
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Ellipse2D.Double(cx - radioHalo, cy - radioHalo, radioHalo * 2, radioHalo * 2));

        double radio = 13 + intensidad * 0.6 * pulso;
        g2.setColor(color);
        g2.fill(new Ellipse2D.Double(cx - radio, cy - radio, radio * 2, radio * 2));
        g2.dispose();
    }
}
