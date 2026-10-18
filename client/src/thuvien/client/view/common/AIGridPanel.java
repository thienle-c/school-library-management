package thuvien.client.view.common;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.util.Random;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * AI Futuristic Digital Grid Background with floating cyber nodes and subtle connective links.
 * Runs at a battery-friendly 30 FPS and automatically pauses when hidden.
 */
public class AIGridPanel extends JPanel {

    private static final int NUM_NODES = 12;
    private static final int GRID_STEP = 36;

    private static class Node {
        float x, y;
        float vx, vy;
        float radius;
        float alpha;

        Node(float x, float y, float vx, float vy, float radius, float alpha) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.alpha = alpha;
        }
    }

    private final Node[] nodes = new Node[NUM_NODES];
    private Timer animTimer;
    private float scanlineY = 0f;

    public AIGridPanel() {
        setOpaque(false);
        setBackground(ModernUIHelper.BG_APP);
        initNodes();
        initAnimation();
    }

    private void initNodes() {
        Random rand = new Random(42);
        for (int i = 0; i < NUM_NODES; i++) {
            nodes[i] = new Node(
                    rand.nextFloat() * 800f,
                    rand.nextFloat() * 600f,
                    (rand.nextFloat() - 0.5f) * 0.7f,
                    (rand.nextFloat() - 0.5f) * 0.7f,
                    1.5f + rand.nextFloat() * 2.0f,
                    0.25f + rand.nextFloat() * 0.45f
            );
        }
    }

    private void initAnimation() {
        // ~30 FPS timer (33ms)
        animTimer = new Timer(33, e -> {
            if (isShowing()) {
                updatePhysics();
                repaint();
            }
        });

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    if (!animTimer.isRunning()) animTimer.start();
                } else {
                    if (animTimer.isRunning()) animTimer.stop();
                }
            }
        });
    }

    private void updatePhysics() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        scanlineY += 1.2f;
        if (scanlineY > h + 100) {
            scanlineY = -50f;
        }

        for (Node n : nodes) {
            n.x += n.vx;
            n.y += n.vy;

            if (n.x < 0) { n.x = 0; n.vx = -n.vx; }
            else if (n.x > w) { n.x = w; n.vx = -n.vx; }

            if (n.y < 0) { n.y = 0; n.vy = -n.vy; }
            else if (n.y > h) { n.y = h; n.vy = -n.vy; }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth();
        int h = getHeight();

        // 1. Scholarly Bookstore Soft Gradient (#EEF2F6 to #F8FAFC)
        java.awt.GradientPaint bgPaint = new java.awt.GradientPaint(
                0, 0, new Color(0xEE, 0xF2, 0xF6),
                0, h, new Color(0xF8, 0xFA, 0xFC)
        );
        g2.setPaint(bgPaint);
        g2.fillRect(0, 0, w, h);

        // 2. Subtle Library Lattice Grid Pattern
        g2.setColor(new Color(203, 213, 225, 45)); // Slate 300 with soft 18% alpha
        g2.setStroke(new BasicStroke(1.0f));
        for (int x = 0; x < w; x += GRID_STEP) {
            g2.drawLine(x, 0, x, h);
        }
        for (int y = 0; y < h; y += GRID_STEP) {
            g2.drawLine(0, y, w, y);
        }

        // 3. Connective lines between nearby ambient nodes (soft scholarly tint)
        g2.setStroke(new BasicStroke(0.8f));
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                float dx = nodes[i].x - nodes[j].x;
                float dy = nodes[i].y - nodes[j].y;
                float distSq = dx * dx + dy * dy;
                if (distSq < 16000f) {
                    float alpha = (1.0f - distSq / 16000f) * 0.15f;
                    g2.setColor(new Color(37, 99, 235, (int) (alpha * 255)));
                    g2.drawLine((int) nodes[i].x, (int) nodes[i].y, (int) nodes[j].x, (int) nodes[j].y);
                }
            }
        }

        // 4. Floating Warm Scholarly Bokeh Dots (Warm Amber & Bookstore Blue)
        for (int i = 0; i < nodes.length; i++) {
            Node n = nodes[i];
            Color baseColor = (i % 2 == 0) ? new Color(37, 99, 235) : new Color(217, 119, 6); // Blue or Amber
            
            // Soft aura
            g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int) (n.alpha * 45)));
            g2.fillOval((int) (n.x - n.radius * 2.0f), (int) (n.y - n.radius * 2.0f), (int) (n.radius * 4.0f), (int) (n.radius * 4.0f));

            // Core particle
            g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int) (n.alpha * 160)));
            g2.fillOval((int) (n.x - n.radius), (int) (n.y - n.radius), (int) (n.radius * 2), (int) (n.radius * 2));
        }

        g2.dispose();
    }
}
