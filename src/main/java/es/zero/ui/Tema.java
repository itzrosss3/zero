package es.zero.ui;

import java.awt.Color;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * Aplica el tema negro a TODOS los componentes de Swing.
 * <p>
 * Swing guarda los colores y fuentes por defecto en {@link UIManager} con claves de texto
 * ("Button.background", "TextField.background"...). Cambiándolas aquí, cada botón, campo o
 * desplegable que se cree después ya sale negro, sin tener que configurarlos uno a uno.
 * Lista completa de claves: https://www.formdev.com/flatlaf/components/
 */
public final class Tema {

    private Tema() {
    }

    public static void aplicar() {
        // FlatLaf dibuja su propia barra de título: así también puede ser negra
        System.setProperty("flatlaf.useWindowDecorations", "true");
        FlatDarkLaf.setup();

        UIManager.put("defaultFont", Colores.fuente(13, false));

        // Barra de título de la ventana
        UIManager.put("TitlePane.background", Colores.FONDO);
        UIManager.put("TitlePane.inactiveBackground", Colores.FONDO);
        UIManager.put("TitlePane.foreground", Colores.TEXTO);
        UIManager.put("TitlePane.inactiveForeground", Colores.TENUE);
        UIManager.put("TitlePane.unifiedBackground", true);

        // Fondos generales
        UIManager.put("Panel.background", Colores.FONDO);
        UIManager.put("RootPane.background", Colores.FONDO);
        UIManager.put("ScrollPane.background", Colores.PANEL);
        UIManager.put("OptionPane.background", Colores.PANEL);

        // Botones
        UIManager.put("Button.background", Colores.PANEL2);
        UIManager.put("Button.foreground", Colores.TEXTO);
        UIManager.put("Button.hoverBackground", Colores.BORDE);
        UIManager.put("Button.pressedBackground", new Color(0x2A2A2A));
        UIManager.put("Button.borderColor", Colores.BORDE);
        UIManager.put("Button.hoverBorderColor", Colores.BORDE);
        UIManager.put("Button.default.background", Colores.PANEL2);

        // Campos de texto, desplegables y listas
        UIManager.put("Component.borderColor", Colores.BORDE);
        UIManager.put("Component.focusedBorderColor", Colores.ACENTO2);
        UIManager.put("Component.focusColor", new Color(0x330EA5E9, true));
        UIManager.put("TextField.background", Colores.PANEL);
        UIManager.put("TextField.foreground", Colores.TEXTO);
        UIManager.put("TextField.placeholderForeground", Colores.TENUE);
        UIManager.put("ComboBox.background", Colores.PANEL2);
        UIManager.put("ComboBox.buttonBackground", Colores.PANEL2);
        UIManager.put("ComboBox.popupBackground", Colores.PANEL);
        UIManager.put("ComboBox.selectionBackground", Colores.BORDE);
        UIManager.put("List.background", Colores.PANEL);
        UIManager.put("List.selectionBackground", Colores.BORDE);
        UIManager.put("PopupMenu.background", Colores.PANEL);
        UIManager.put("ToolTip.background", Colores.PANEL2);
        UIManager.put("ToolTip.foreground", Colores.TEXTO);

        // Barras de desplazamiento y deslizadores
        UIManager.put("ScrollBar.track", Colores.PANEL);
        UIManager.put("ScrollBar.thumb", Colores.BORDE);
        UIManager.put("ScrollBar.hoverThumbColor", new Color(0x2E2E2E));
        UIManager.put("Slider.trackColor", Colores.BORDE);
        UIManager.put("Slider.trackValueColor", Colores.ACENTO2);
        UIManager.put("Slider.thumbColor", Colores.ACENTO);
        UIManager.put("Slider.focusedColor", new Color(0, 0, 0, 0));

        // Formas redondeadas
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
    }
}
