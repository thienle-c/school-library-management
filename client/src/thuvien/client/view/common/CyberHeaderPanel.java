package thuvien.client.view.common;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Modern Clean Light Top Bar Panel with crisp surface (#FFFFFF) and subtle divider (#E2E8F0).
 */
public class CyberHeaderPanel extends JPanel {

    public CyberHeaderPanel() {
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Clean White Header surface (#FFFFFF)
        g2.setColor(ModernUIHelper.BG_SURFACE);
        g2.fillRect(0, 0, w, h);

        // 2. Crisp Subtle Bottom Divider (#E2E8F0)
        g2.setColor(ModernUIHelper.BORDER_COLOR);
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawLine(0, h - 1, w, h - 1);

        g2.dispose();
    }
}
