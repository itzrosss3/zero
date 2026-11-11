package es.zero.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * Icono de micrófono estilo Discord, dibujado con código (sin imágenes).
 * Cuando está silenciado se pinta en rojo y con una raya cruzada.
 * <p>
 * Implementa la interfaz {@link Icon}: cualquier JButton o JLabel puede mostrarlo.
 * Se dibuja en una "rejilla" de 24x24 y luego se escala al tamaño pedido.
 */
public class IconoMicrofono implements Icon {

    private final int tamano;
    private final Color fondo;    // para el "hueco" alrededor de la raya
    private boolean silenciado;

    public IconoMicrofono(int tamano, Color fondo) {
        this.tamano = tamano;
        this.fondo = fondo;
    }

    public void ponerSilenciado(boolean silenciado) {
        this.silenciado = silenciado;
    }

    @Override
    public int getIconWidth() {
        return tamano;
    }

    @Override
    public int getIconHeight() {
        return tamano;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        g2.scale(tamano / 24.0, tamano / 24.0);

        BasicStroke trazo = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g2.setColor(silenciado ? Colores.SILENCIADO : Colores.TEXTO);

        // Cápsula del micrófono
        g2.fill(new RoundRectangle2D.Double(8.5, 2.5, 7, 12, 7, 7));
        // Soporte en forma de U (media circunferencia de abajo)
        g2.setStroke(trazo);
        g2.draw(new Arc2D.Double(5.5, 4.5, 13, 13, 180, 180, Arc2D.OPEN));
        // Pie
        g2.draw(new Line2D.Double(12, 17.5, 12, 21));
        g2.draw(new Line2D.Double(8.5, 21, 15.5, 21));

        if (silenciado) {
            Line2D raya = new Line2D.Double(4, 3, 20, 21);
            g2.setColor(fondo); // primero una raya gruesa del color del fondo (el hueco)...
            g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(raya);
            g2.setColor(Colores.SILENCIADO); // ...y encima la raya roja fina
            g2.setStroke(trazo);
            g2.draw(raya);
        }
        g2.dispose();
    }
}
