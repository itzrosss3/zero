package es.zero.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Ventanita "¿Instalo X?" con Sí / No. No es modal: también se puede responder con la voz. */
public class DialogoConfirmacion extends JDialog {

    private static final long serialVersionUID = 1L;

    public DialogoConfirmacion(JFrame padre, String pregunta, Consumer<Boolean> alResponder) {
        super(padre, "Confirmar", false);
        setAlwaysOnTop(true);
        setResizable(false);

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        contenido.setBackground(Colores.PANEL);
        contenido.setBorder(BorderFactory.createEmptyBorder(22, 26, 18, 26));

        JLabel lblPregunta = new JLabel("<html><div style='text-align:center;width:300px'>"
                + pregunta.replace("\n", "<br>") + "</div></html>", SwingConstants.CENTER);
        lblPregunta.setForeground(Colores.TEXTO);
        lblPregunta.setFont(Colores.fuente(15, false));

        JLabel ayuda = new JLabel("También puedes responder con la voz: \"sí\" o \"no\"", SwingConstants.CENTER);
        ayuda.setForeground(Colores.TENUE);
        ayuda.setFont(Colores.fuente(12, false));

        JButton si = new JButton("Sí, adelante");
        si.setForeground(Colores.OK);
        JButton no = new JButton("No");
        si.addActionListener(e -> {
            alResponder.accept(true);
            dispose();
        });
        no.addActionListener(e -> {
            alResponder.accept(false);
            dispose();
        });
        addWindowListener(new WindowAdapter() { // cerrar con la X cuenta como "no"
            @Override
            public void windowClosing(WindowEvent e) {
                alResponder.accept(false);
            }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        botones.setOpaque(false);
        botones.add(si);
        botones.add(no);

        JPanel textos = new JPanel(new BorderLayout(0, 6));
        textos.setOpaque(false);
        textos.add(lblPregunta, BorderLayout.CENTER);
        textos.add(ayuda, BorderLayout.SOUTH);

        contenido.add(textos, BorderLayout.CENTER);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);
        pack();
        setLocationRelativeTo(padre);
    }
}
