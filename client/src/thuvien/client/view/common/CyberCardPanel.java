package thuvien.client.view.common;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * AI Futuristic Card with rounded dark surface, tech brackets, and smooth 60 FPS hover glow.
 */
public class CyberCardPanel extends JPanel {

    private float hoverProgress = 0f;
    private Timer lerpTimer;
    private Color accentColor = ModernUIHelper.NEON_CYAN;

    public CyberCardPanel() {
        this(ModernUIHelper.NEON_CYAN);
    }

    public CyberCardPanel(Color accentColor) {
        this.accentColor = accentColor;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                startTransition(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                startTransition(false);
            }
        });
    }

    private void startTransition(boolean forward) {
        if (lerpTimer != null && lerpTimer.isRunning()) {
            lerpTimer.stop();
        }
        lerpTimer = new Timer(15, e -> {
            if (forward) {
                hoverProgress += 0.12f;
                if (hoverProgress >= 1.0f) {
                    hoverProgress = 1.0f;
                    lerpTimer.stop();
                }
            } else {
                hoverProgress -= 0.12f;
                if (hoverProgress <= 0.0f) {
                    hoverProgress = 0.0f;
                    lerpTimer.stop();
                }
            }
            repaint();
        });
        lerpTimer.start();
    }

    public void setAccentColor(Color accent) {
        this.accentColor = accent;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 12;

        // 1. Clean White Card Background (#FFFFFF to #F1F5F9 lerp)
        Color bgCol = ModernUIHelper.lerp(ModernUIHelper.BG_SURFACE, ModernUIHelper.BG_CARD_HOVER, hoverProgress);
        g2.setColor(bgCol);
        g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);

        // 2. Base Border (#E2E8F0 to accentColor lerp on hover)
        Color borderCol = ModernUIHelper.lerp(ModernUIHelper.BORDER_COLOR, accentColor, hoverProgress);
        g2.setColor(borderCol);
        g2.setStroke(new BasicStroke(hoverProgress > 0.05f ? 1.5f : 1.0f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);

        // 3. Clean Left Accent Stripe (3.5px)
        g2.setColor(accentColor);
        g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(4, 12, 4, h - 12);

        g2.dispose();
    }
}
