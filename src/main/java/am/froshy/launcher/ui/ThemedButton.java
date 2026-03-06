package am.froshy.launcher.ui;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ThemedButton extends JPanel {
    private JLabel label;
    private Color normalBackground;
    private Color hoverBackground;
    private Color textColor;
    private ActionListener actionListener;
    private boolean isHovered = false;

    public ThemedButton(String text, Color background, Color textColor) {
        this.normalBackground = background;
        this.textColor = textColor;
        this.hoverBackground = brighten(background, 30);

        setOpaque(true);
        setLayout(new BorderLayout());
        setBackground(normalBackground);
        setBorder(BorderFactory.createLineBorder(textColor, 2));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        label = new JLabel(text);
        label.setForeground(textColor);
        label.setFont(new Font("Dialog", Font.BOLD, 12));
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setVerticalAlignment(JLabel.CENTER);
        label.setOpaque(false);
        add(label, BorderLayout.CENTER);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (actionListener != null) {
                    actionListener.actionPerformed(null);
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color currentBackground = isHovered ? hoverBackground : normalBackground;
        g.setColor(currentBackground);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

    public void addActionListener(ActionListener listener) {
        this.actionListener = listener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        label.setEnabled(enabled);
    }

    private Color brighten(Color color, int amount) {
        return new Color(
            Math.min(255, color.getRed() + amount),
            Math.min(255, color.getGreen() + amount),
            Math.min(255, color.getBlue() + amount)
        );
    }
}
