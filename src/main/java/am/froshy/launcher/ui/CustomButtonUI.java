package am.froshy.launcher.ui;

import javax.swing.JButton;
import javax.swing.plaf.metal.MetalButtonUI;
import java.awt.Color;
import java.awt.Graphics;

public class CustomButtonUI extends MetalButtonUI {
    private Color backgroundColor;
    private Color foregroundColor;

    public CustomButtonUI(Color backgroundColor, Color foregroundColor) {
        this.backgroundColor = backgroundColor;
        this.foregroundColor = foregroundColor;
    }

    @Override
    public void paint(Graphics g, javax.swing.JComponent c) {
        JButton button = (JButton) c;

        // Pintar fondo
        g.setColor(backgroundColor);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());

        // Pintar borde
        g.setColor(foregroundColor);
        g.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);

        // Pintar texto
        super.paint(g, c);
    }
}

