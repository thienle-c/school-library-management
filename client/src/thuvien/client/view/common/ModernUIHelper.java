package thuvien.client.view.common;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

/**
/**
 * Senior UI/UX Design System & Custom Rendering Engine for Java 8 Swing.
 * Prestigious Scholarly Bookstore & Academic Library Light Theme with WCAG AAA contrast.
 */
public final class ModernUIHelper {

    // ==========================================
    // BOOKSTORE LIGHT THEME SURFACES & PALETTE
    // ==========================================
    public static final Color BG_MAIN = new Color(0xF8, 0xFA, 0xFC);            // #F8FAFC Soft warm pearl canvas
    public static final Color BG_APP = BG_MAIN;                                  // Backwards-compatibility alias
    public static final Color BG_SIDEBAR = new Color(0x0F, 0x17, 0x2A);          // #0F172A Midnight Academic Slate (Deep Navy)
    public static final Color BG_SIDEBAR_HEADER = BG_SIDEBAR;                    // Backwards-compatibility alias
    public static final Color BG_SURFACE = new Color(0xFF, 0xFF, 0xFF);          // #FFFFFF Pure white cards, panels, tables
    public static final Color BG_CARD = BG_SURFACE;                              // Backwards-compatibility alias
    public static final Color BG_CARD_HOVER = new Color(0xF8, 0xFA, 0xFC);      // #F8FAFC Card hover tint
    public static final Color BG_INPUT = new Color(0xFF, 0xFF, 0xFF);            // #FFFFFF Text fields & Combos
    public static final Color BG_HOVER = new Color(0xF1, 0xF5, 0xF9);            // #F1F5F9 Soft hover surface
    public static final Color BG_ACTIVE = new Color(0xDB, 0xEA, 0xFE);           // #DBEAFE Active/selected table row

    // ==========================================
    // BORDERS & PRIMARY ACCENTS
    // ==========================================
    public static final Color BORDER_COLOR = new Color(0xE2, 0xE8, 0xF0);        // #E2E8F0 Crisp subtle 1px divider
    public static final Color BORDER_SUBTLE = BORDER_COLOR;                      // Backwards-compatibility alias
    public static final Color BORDER_INPUT = new Color(0xCB, 0xD5, 0xE1);        // #CBD5E1 Input border
    public static final Color BORDER_FOCUS = new Color(0x25, 0x63, 0xEB);        // #2563EB Bookstore Blue focus ring
    public static final Color PRIMARY_BLUE = new Color(0x25, 0x63, 0xEB);       // #2563EB Primary Bookstore Action
    public static final Color PRIMARY_HOVER = new Color(0x1D, 0x4E, 0xD8);      // #1D4ED8 Primary Action Hover
    public static final Color CYAN_PRIMARY = PRIMARY_BLUE;                       // Compatibility alias
    public static final Color CYAN_HOVER = PRIMARY_HOVER;                        // Compatibility alias
    public static final Color CYAN_TEXT = Color.WHITE;                           // White text on primary button
    public static final Color NEON_CYAN = PRIMARY_BLUE;                          // Map old neon cyan to professional bookstore blue
    public static final Color ELECTRIC_BLUE = PRIMARY_BLUE;
    public static final Color SIDEBAR_ACTIVE_TEXT = Color.WHITE;                 // Sidebar active text
    public static final Color SIDEBAR_TEXT = new Color(0x94, 0xA3, 0xB8);        // #94A3B8 Refined muted sidebar text
    public static final Color INDICATOR_CYAN = new Color(0x3B, 0x82, 0xF6);      // #3B82F6 Active indicator stripe
    public static final Color BORDER_CYAN_GLOW = new Color(37, 99, 235, 40);

    // Warm Bookstore Accents
    public static final Color BOOKSTORE_AMBER = new Color(0xD9, 0x77, 0x06);     // #D97706 Warm leather / bookmark amber
    public static final Color BOOKSTORE_EMERALD = new Color(0x16, 0xA3, 0x4A);   // #16A34A Academic green
    public static final Color BOOKSTORE_ROSE = new Color(0xDC, 0x26, 0x26);      // #DC2626 Scholarly warning red

    // ==========================================
    // KPI ACCENT PALETTES (WCAG AA/AAA Light Theme)
    // ==========================================
    public static final Color KPI_BLUE = new Color(0x25, 0x63, 0xEB);            // #2563EB Total books
    public static final Color KPI_GREEN = new Color(0x16, 0xA3, 0x4A);           // #16A34A Available
    public static final Color KPI_AMBER = new Color(0xD9, 0x77, 0x06);           // #D97706 Borrowed
    public static final Color KPI_PURPLE = new Color(0x7C, 0x3A, 0xED);          // #7C3AED Active loans
    public static final Color KPI_RED = new Color(0xDC, 0x26, 0x26);             // #DC2626 Overdue
    public static final Color KPI_ORANGE = new Color(0xEA, 0x58, 0x0C);          // #EA580C Unpaid fines

    public static final Color SUCCESS_EMERALD = KPI_GREEN;                       // #16A34A
    public static final Color DANGER_ROSE = KPI_RED;                             // #DC2626
    public static final Color WARNING_AMBER = KPI_AMBER;                         // #D97706
    public static final Color ACCENT_CORAL = KPI_RED;
    public static final Color ACCENT_AMBER = KPI_AMBER;

    // ==========================================
    // TYPOGRAPHY (High-Contrast Bookstore Font Hierarchy)
    // ==========================================
    public static final Color TEXT_PRIMARY = new Color(0x0F, 0x17, 0x2A);        // #0F172A Deep charcoal primary text
    public static final Color TEXT_TITLE = TEXT_PRIMARY;
    public static final Color TEXT_ACCENT = PRIMARY_BLUE;                        // #2563EB Accent headings
    public static final Color TEXT_VALUE = new Color(0x1E, 0x29, 0x3B);          // #1E293B Body/values
    public static final Color TEXT_BODY = TEXT_VALUE;
    public static final Color LABEL_DETAIL = new Color(0x47, 0x55, 0x69);        // #475569 Crisp secondary labels
    public static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B);          // #64748B Secondary/muted text
    public static final Color TEXT_LIGHT = new Color(0x94, 0xA3, 0xB8);

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_LABEL = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_INPUT = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);

    static {
        applyGlobalTheme();
    }

    private ModernUIHelper() {}

    /**
     * Injects light, high-contrast theme defaults into UIManager so standard Swing
     * components (labels, tables, text fields, popups) match the modern light design system.
     */
    public static void applyGlobalTheme() {
        try {
            UIManager.put("Label.foreground", TEXT_PRIMARY);
            UIManager.put("Label.font", FONT_LABEL);
            UIManager.put("Panel.foreground", TEXT_PRIMARY);
            UIManager.put("Panel.background", BG_MAIN);
            UIManager.put("Viewport.background", BG_SURFACE);
            UIManager.put("Viewport.foreground", TEXT_PRIMARY);
            UIManager.put("ScrollPane.background", BG_SURFACE);
            UIManager.put("ScrollPane.foreground", TEXT_PRIMARY);
            UIManager.put("Table.foreground", TEXT_PRIMARY);
            UIManager.put("Table.background", BG_SURFACE);
            UIManager.put("Table.selectionForeground", TEXT_PRIMARY);
            UIManager.put("Table.selectionBackground", BG_ACTIVE);
            UIManager.put("TableHeader.foreground", new Color(0x47, 0x55, 0x69));
            UIManager.put("TableHeader.background", new Color(0xF1, 0xF5, 0xF9));
            UIManager.put("TextField.foreground", TEXT_PRIMARY);
            UIManager.put("TextField.background", BG_INPUT);
            UIManager.put("TextField.caretForeground", PRIMARY_BLUE);
            UIManager.put("PasswordField.foreground", TEXT_PRIMARY);
            UIManager.put("PasswordField.background", BG_INPUT);
            UIManager.put("PasswordField.caretForeground", PRIMARY_BLUE);
            UIManager.put("ComboBox.foreground", TEXT_PRIMARY);
            UIManager.put("ComboBox.background", BG_INPUT);
            UIManager.put("ComboBox.selectionForeground", TEXT_PRIMARY);
            UIManager.put("ComboBox.selectionBackground", BG_ACTIVE);
            UIManager.put("Spinner.foreground", TEXT_PRIMARY);
            UIManager.put("Spinner.background", BG_INPUT);
            UIManager.put("Button.foreground", TEXT_PRIMARY);
            UIManager.put("OptionPane.background", BG_SURFACE);
            UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
            UIManager.put("OptionPane.foreground", TEXT_PRIMARY);
            UIManager.put("TitledBorder.titleColor", PRIMARY_BLUE);
            UIManager.put("SplitPane.background", BG_MAIN);
            UIManager.put("SplitPane.dividerSize", 6);
            UIManager.put("CheckBox.foreground", TEXT_PRIMARY);
            UIManager.put("RadioButton.foreground", TEXT_PRIMARY);
            UIManager.put("TabbedPane.foreground", TEXT_PRIMARY);
            UIManager.put("ToolTip.background", BG_SURFACE);
            UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        } catch (Exception ignored) {}
    }

    /**
     * Color linear interpolation helper for smooth 60fps micro-animations.
     */
    public static Color lerp(Color c1, Color c2, float t) {
        if (c1 == null) return c2;
        if (c2 == null) return c1;
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * t);
        return new Color(r, g, b, a);
    }

    /**
     * Creates a soft rounded border with antialiased rendering.
     */
    public static Border createRoundedBorder(final Color color, final int radius, final int topPad, final int leftPad, final int bottomPad, final int rightPad) {
        return new Border() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color != null ? color : BORDER_COLOR);
                g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
                g2.dispose();
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(topPad, leftPad, bottomPad, rightPad);
            }

            @Override
            public boolean isBorderOpaque() {
                return false;
            }
        };
    }

    /**
     * Styles an input field with clean padding, dark surface, and focus ring.
     */
    public static void styleInputField(final JTextField field) {
        if (field == null) return;
        field.setFont(FONT_INPUT);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(BORDER_FOCUS);

        final Border defaultBorder = createRoundedBorder(BORDER_INPUT, 8, 7, 10, 7, 10);
        final Border focusedBorder = createRoundedBorder(BORDER_FOCUS, 8, 7, 10, 7, 10);

        field.setBorder(defaultBorder);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(focusedBorder);
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(defaultBorder);
            }
        });
    }

    public static void styleTextField(final JTextField field) {
        styleInputField(field);
    }

    /**
     * Styles a form field label (Muted slate #94A3B8, Font.BOLD 13px).
     */
    public static void styleLabel(JLabel label, String text) {
        if (label == null) return;
        label.setText(text);
        label.setFont(FONT_LABEL);
        label.setForeground(LABEL_DETAIL);
    }

    /**
     * Styles a display value label with dynamic empty/selected state.
     */
    public static void styleValueLabel(JLabel label, String text) {
        if (label == null) return;
        label.setText(text);
        if (text == null || text.trim().isEmpty() || text.equals("-") || text.equals("Chưa chọn")) {
            label.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            label.setForeground(TEXT_MUTED);
        } else {
            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            label.setForeground(TEXT_PRIMARY);
        }
    }

    /**
     * Sets value and updates font/color according to selection status.
     */
    public static void updateValueLabel(JLabel label, String text, boolean isEmpty) {
        if (label == null) return;
        label.setText(text);
        if (isEmpty || text == null || text.equals("Chưa chọn") || text.equals("-")) {
            label.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            label.setForeground(TEXT_MUTED);
        } else {
            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            label.setForeground(TEXT_PRIMARY);
        }
    }

    // =========================================================================
    // CUSTOM 60 FPS BUTTON RENDERING (Disables OS native white backgrounds)
    // =========================================================================

    public static class ModernButtonUI extends BasicButtonUI {
        private final Color normalBg;
        private final Color hoverBg;
        private final Color normalFg;
        private final Color hoverFg;
        private final Color normalBorder;
        private final Color hoverBorder;
        private final int radius;
        private final boolean hasLeftIndicator;
        private boolean isActive = false;

        private float hoverProgress = 0f;
        private Timer animTimer;

        public ModernButtonUI(Color normalBg, Color hoverBg, Color normalFg, Color hoverFg,
                              Color normalBorder, Color hoverBorder, int radius, boolean hasLeftIndicator) {
            this.normalBg = normalBg;
            this.hoverBg = hoverBg;
            this.normalFg = normalFg;
            this.hoverFg = hoverFg;
            this.normalBorder = normalBorder;
            this.hoverBorder = hoverBorder;
            this.radius = radius;
            this.hasLeftIndicator = hasLeftIndicator;
        }

        public void setActive(boolean active, JButton b) {
            this.isActive = active;
            if (b != null) b.repaint();
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            final JButton b = (JButton) c;
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setOpaque(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            b.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (b.isEnabled()) startTransition(b, true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (b.isEnabled()) startTransition(b, false);
                }
            });
        }

        private void startTransition(final JButton b, final boolean forward) {
            if (animTimer != null && animTimer.isRunning()) {
                animTimer.stop();
            }
            // ~60 FPS step (15ms), duration ~150ms
            animTimer = new Timer(15, e -> {
                if (forward) {
                    hoverProgress += 0.12f;
                    if (hoverProgress >= 1.0f) {
                        hoverProgress = 1.0f;
                        animTimer.stop();
                    }
                } else {
                    hoverProgress -= 0.12f;
                    if (hoverProgress <= 0.0f) {
                        hoverProgress = 0.0f;
                        animTimer.stop();
                    }
                }
                b.repaint();
            });
            animTimer.start();
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            JButton b = (JButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int w = b.getWidth();
            int h = b.getHeight();

            Color curBg;
            Color curFg;
            Color curBorder;

            if (isActive) {
                curBg = new Color(0x1E, 0x40, 0xAF); // #1E40AF Active sidebar item
                curFg = Color.WHITE;
                curBorder = null;
            } else {
                curBg = lerp(normalBg, hoverBg, hoverProgress);
                curFg = lerp(normalFg, hoverFg, hoverProgress);
                curBorder = lerp(normalBorder, hoverBorder, hoverProgress);
            }

            // Fill rounded background
            g2.setColor(curBg);
            g2.fillRoundRect(0, 0, w, h, radius, radius);

            // Draw border
            if (curBorder != null) {
                g2.setColor(curBorder);
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
            }

            // Left vertical active indicator (4px primary blue)
            if (hasLeftIndicator && isActive) {
                g2.setColor(INDICATOR_CYAN);
                g2.fillRoundRect(0, 4, 4, h - 8, 3, 3);
            }

            // Text & Icon
            b.setForeground(curFg);
            super.paint(g2, c);
            g2.dispose();
        }
    }

    /**
     * Styles a primary Call-To-Action button (#2563EB, white text, hover #1D4ED8, 8px radius).
     */
    public static void stylePrimaryButton(final JButton btn, String text) {
        if (btn == null) return;
        btn.setText(text);
        btn.setFont(FONT_BUTTON);
        btn.setUI(new ModernButtonUI(PRIMARY_BLUE, PRIMARY_HOVER, Color.WHITE, Color.WHITE, null, null, 8, false));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    }

    /**
     * Styles a secondary / cancel button (#FFFFFF surface, #E2E8F0 border, #0F172A text, hover #F1F5F9).
     */
    public static void styleSecondaryButton(final JButton btn, String text) {
        if (btn == null) return;
        btn.setText(text);
        btn.setFont(FONT_BUTTON);
        btn.setUI(new ModernButtonUI(Color.WHITE, new Color(0xF1, 0xF5, 0xF9), TEXT_PRIMARY, PRIMARY_BLUE, BORDER_COLOR, PRIMARY_BLUE, 8, false));
        btn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
    }

    /**
     * Styles an alert / danger button (#DC2626 red, hover #B91C1C, white text).
     */
    public static void styleDangerButton(final JButton btn, String text) {
        if (btn == null) return;
        btn.setText(text);
        btn.setFont(FONT_BUTTON);
        btn.setUI(new ModernButtonUI(new Color(0xDC, 0x26, 0x26), new Color(0xB9, 0x1C, 0x1C), Color.WHITE, Color.WHITE,
                null, null, 8, false));
        btn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
    }

    /**
     * Styles the "Change Password" button (#FFFFFF surface, #E2E8F0 border, #0F172A text, hover #F1F5F9).
     */
    public static void styleChangePasswordButton(final JButton btn, String text) {
        if (btn == null) return;
        btn.setText(text);
        btn.setFont(FONT_BUTTON);
        btn.setUI(new ModernButtonUI(Color.WHITE, new Color(0xF1, 0xF5, 0xF9), TEXT_PRIMARY, PRIMARY_BLUE,
                BORDER_COLOR, PRIMARY_BLUE, 8, false));
        btn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
    }

    /**
     * Styles a success button (#16A34A emerald, hover #15803D, white text).
     */
    public static void styleSuccessButton(final JButton btn, String text) {
        if (btn == null) return;
        btn.setText(text);
        btn.setFont(FONT_BUTTON);
        btn.setUI(new ModernButtonUI(new Color(0x16, 0xA3, 0x4A), new Color(0x15, 0x80, 0x3D), Color.WHITE, Color.WHITE,
                null, null, 8, false));
        btn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
    }

    /**
     * Styles a sidebar navigation button (Navy #172554 normal, #CBD5E1 text; hover #1E3A8A, white; active #1E40AF with 4px left blue indicator).
     */
    public static void styleSidebarButton(final JButton btn, String text, Icon icon) {
        if (btn == null) return;
        btn.setText(text);
        if (icon != null) {
            btn.setIcon(icon);
            btn.setIconTextGap(10);
        }
        btn.setFont(FONT_BUTTON);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setUI(new ModernButtonUI(BG_SIDEBAR, new Color(0x1E, 0x3A, 0x8A), SIDEBAR_TEXT, Color.WHITE, null, null, 8, true));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 12));
    }

    /**
     * Updates active state of a sidebar button.
     */
    public static void setSidebarButtonActive(JButton btn, boolean active) {
        if (btn != null && btn.getUI() instanceof ModernButtonUI) {
            ((ModernButtonUI) btn.getUI()).setActive(active, btn);
            btn.setFont(active ? new Font("Segoe UI", Font.BOLD, 12) : FONT_BUTTON);
        }
    }

    /**
     * Styles a card container panel with clean surface #131F33, subtle border #1E293B.
     */
    public static void styleCardPanel(JComponent panel) {
        if (panel == null) return;
        panel.setBackground(BG_SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                createRoundedBorder(BORDER_COLOR, 10, 0, 0, 0, 0),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));
        styleComponentTree(panel);
    }

    /**
     * Smoothly highlights a panel's border for 200ms when data is selected.
     */
    public static void highlightCardBorder(final JComponent panel) {
        if (panel == null) return;
        final Timer pulseTimer = new Timer(20, null);
        final long startTime = System.currentTimeMillis();
        final int duration = 220;

        pulseTimer.addActionListener(e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= duration) {
                pulseTimer.stop();
                panel.setBorder(BorderFactory.createCompoundBorder(
                        createRoundedBorder(BORDER_COLOR, 10, 0, 0, 0, 0),
                        BorderFactory.createEmptyBorder(16, 18, 16, 18)
                ));
                panel.repaint();
            } else {
                float progress = 1.0f - ((float) elapsed / duration);
                Color glow = lerp(BORDER_COLOR, BORDER_FOCUS, progress);
                panel.setBorder(BorderFactory.createCompoundBorder(
                        createRoundedBorder(glow, 10, 0, 0, 0, 0),
                        BorderFactory.createEmptyBorder(16, 18, 16, 18)
                ));
                panel.repaint();
            }
        });
        pulseTimer.start();
    }

    /**
     * Styles a JSplitPane to remove white divider artifacts and set dark borders.
     */
    public static void styleSplitPane(JSplitPane splitPane) {
        if (splitPane == null) return;
        splitPane.setOpaque(false);
        splitPane.setBackground(BG_MAIN);
        splitPane.setBorder(null);
        splitPane.setDividerSize(6);
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(BG_MAIN);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(BORDER_COLOR);
                        if (getOrientation() == JSplitPane.HORIZONTAL_SPLIT) {
                            int x = getWidth() / 2;
                            g.drawLine(x, 0, x, getHeight());
                        } else {
                            int y = getHeight() / 2;
                            g.drawLine(0, y, getWidth(), y);
                        }
                    }

                    @Override
                    public void setBorder(Border b) {}
                };
            }
        });
    }

    /**
     * Styles a slim clean light scrollbar for JScrollPane.
     */
    public static void styleScrollBar(JScrollBar bar) {
        if (bar == null) return;
        bar.setPreferredSize(new Dimension(8, 8));
        bar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(0xCB, 0xD5, 0xE1); // #CBD5E1 Slate 300
                this.trackColor = new Color(0xF8, 0xFA, 0xFC); // #F8FAFC Slate 50
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isThumbRollover() ? new Color(0x94, 0xA3, 0xB8) : thumbColor);
                g2.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
                g.setColor(trackColor);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });
    }

    /**
     * Styles a JTable with alternating clean rows (#FFFFFF and #F8FAFC), 36px height, 38px header, and high contrast.
     */
    public static void styleTable(final JTable table) {
        if (table == null) return;
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(BORDER_COLOR);
        table.setBackground(BG_SURFACE);
        table.setForeground(TEXT_PRIMARY);
        table.setSelectionBackground(BG_ACTIVE); // #DBEAFE
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setFillsViewportHeight(true);

        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? BG_SURFACE : BG_MAIN); // Alternating #FFFFFF & #F8FAFC
                    c.setForeground(TEXT_PRIMARY);
                } else {
                    c.setBackground(BG_ACTIVE); // #DBEAFE
                    c.setForeground(TEXT_PRIMARY);
                }
                return c;
            }
        };

        table.setDefaultRenderer(Object.class, defaultRenderer);
        table.setDefaultRenderer(Number.class, defaultRenderer);
        table.setDefaultRenderer(Integer.class, defaultRenderer);
        table.setDefaultRenderer(Long.class, defaultRenderer);
        table.setDefaultRenderer(Double.class, defaultRenderer);
        table.setDefaultRenderer(Float.class, defaultRenderer);
        table.setDefaultRenderer(Boolean.class, defaultRenderer);
        table.setDefaultRenderer(String.class, defaultRenderer);
        table.setDefaultRenderer(java.util.Date.class, defaultRenderer);

        javax.swing.table.JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setFont(new Font("Segoe UI", Font.BOLD, 12));
            header.setBackground(new Color(0xF1, 0xF5, 0xF9)); // #F1F5F9 Slate 100
            header.setForeground(new Color(0x47, 0x55, 0x69)); // #475569 Slate 600
            header.setPreferredSize(new Dimension(header.getWidth(), 38));
            header.setReorderingAllowed(false);
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        }

        Container parent = table.getParent();
        if (parent instanceof javax.swing.JViewport) {
            parent.setBackground(BG_SURFACE);
            Container grandParent = parent.getParent();
            if (grandParent instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) grandParent;
                sp.setBackground(BG_SURFACE);
                sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
                sp.getViewport().setBackground(BG_SURFACE);
                styleScrollBar(sp.getVerticalScrollBar());
                styleScrollBar(sp.getHorizontalScrollBar());
            }
        }

        applyStatusBadge(table);
    }

    /**
     * Inspects column headers and automatically applies the StatusBadgeRenderer to status columns.
     */
    public static void applyStatusBadge(JTable table) {
        if (table == null || table.getColumnModel() == null) return;
        StatusBadgeRenderer badgeRenderer = new StatusBadgeRenderer();
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            Object header = col.getHeaderValue();
            if (header != null) {
                String hStr = header.toString().toLowerCase();
                if (hStr.contains("trạng thái") || hStr.contains("status")) {
                    col.setCellRenderer(badgeRenderer);
                }
            }
        }
    }

    /**
     * High-fidelity pill badge renderer for library status values in light theme.
     */
    public static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        private Color badgeBg;
        private Color badgeFg;
        private Color badgeBorder;
        private Color cellBg;

        public StatusBadgeRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            cellBg = isSelected ? BG_ACTIVE : (row % 2 == 0 ? BG_SURFACE : BG_MAIN);
            setFont(new Font("Segoe UI", Font.BOLD, 11));

            String text = value != null ? value.toString().trim() : "";
            String lower = text.toLowerCase();

            if (lower.contains("đã trả") || lower.contains("có sẵn") || lower.contains("hoạt động")
                    || lower.contains("đã nộp") || lower.contains("đã thanh toán") || lower.contains("đã nhận")
                    || lower.equals("returned") || lower.equals("available") || lower.equals("active") || lower.equals("paid")) {
                // Success Emerald (bg #DCFCE7, text #15803D, border #86EFAC)
                badgeBg = new Color(0xDC, 0xFC, 0xE7);
                badgeFg = new Color(0x15, 0x80, 0x3D);
                badgeBorder = new Color(0x86, 0xEF, 0xAC);
            } else if (lower.contains("quá hạn") || lower.contains("chưa nộp") || lower.contains("khóa")
                    || lower.contains("mất") || lower.contains("thất lạc") || lower.contains("hủy") || lower.contains("đình chỉ")
                    || lower.equals("overdue") || lower.equals("unpaid") || lower.equals("lost") || lower.equals("suspended")) {
                // Danger Red (bg #FEE2E2, text #B91C1C, border #FCA5A5)
                badgeBg = new Color(0xFE, 0xE2, 0xE2);
                badgeFg = new Color(0xB9, 0x1C, 0x1C);
                badgeBorder = new Color(0xFC, 0xA5, 0xA5);
            } else {
                // Info / Progress Blue (bg #DBEAFE, text #1D4ED8, border #93C5FD)
                badgeBg = new Color(0xDB, 0xEA, 0xFE);
                badgeFg = new Color(0x1D, 0x4E, 0xD8);
                badgeBorder = new Color(0x93, 0xC5, 0xFD);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            g2.setColor(cellBg != null ? cellBg : BG_SURFACE);
            g2.fillRect(0, 0, getWidth(), getHeight());

            String text = getText();
            if (text != null && !text.isEmpty()) {
                FontMetrics fm = g2.getFontMetrics(getFont());
                int textWidth = fm.stringWidth(text);
                int badgeWidth = Math.min(getWidth() - 8, textWidth + 18);
                int badgeHeight = 22;
                int badgeX = (getWidth() - badgeWidth) / 2;
                int badgeY = (getHeight() - badgeHeight) / 2;

                g2.setColor(badgeBg != null ? badgeBg : BG_CARD);
                g2.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 12, 12);

                if (badgeBorder != null) {
                    g2.setColor(badgeBorder);
                    g2.drawRoundRect(badgeX, badgeY, badgeWidth - 1, badgeHeight - 1, 12, 12);
                }

                g2.setColor(badgeFg != null ? badgeFg : TEXT_PRIMARY);
                g2.setFont(getFont());
                int textX = (getWidth() - textWidth) / 2;
                int textY = badgeY + ((badgeHeight - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString(text, textX, textY);
            }
            g2.dispose();
        }
    }

    /**
     * Styles a JComboBox for modern light appearance.
     */
    public static void styleComboBox(final JComboBox<?> combo) {
        if (combo == null) return;
        combo.setFont(FONT_INPUT);
        combo.setBackground(BG_INPUT);
        combo.setForeground(TEXT_PRIMARY);
        combo.setBorder(createRoundedBorder(BORDER_INPUT, 8, 4, 8, 4, 8));
        combo.setFocusable(false);
    }

    /**
     * Styles a JSpinner with dark inputs and rounded borders.
     */
    public static void styleSpinner(final JSpinner spinner) {
        if (spinner == null) return;
        spinner.setFont(FONT_INPUT);
        spinner.setBorder(createRoundedBorder(BORDER_INPUT, 8, 2, 6, 2, 6));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(BG_INPUT);
            tf.setForeground(TEXT_PRIMARY);
            tf.setCaretColor(BORDER_FOCUS);
            tf.setFont(FONT_INPUT);
            tf.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }
    }

    public static void installEscapeToClose(final JDialog dialog) {
        if (dialog == null || dialog.getRootPane() == null) return;
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    public static void styleDialog(final JDialog dialog) {
        if (dialog == null) return;
        dialog.getContentPane().setBackground(BG_MAIN);
        installEscapeToClose(dialog);
        styleComponentTree(dialog.getContentPane());
    }

    /**
     * Recursively traverses any component hierarchy and fixes unreadable components for the light theme.
     */
    public static void styleComponentTree(Component root) {
        if (root == null) return;
        if (root instanceof JLabel) {
            JLabel lbl = (JLabel) root;
            Color fg = lbl.getForeground();
            String text = lbl.getText();
            if (text != null && text.trim().endsWith(":")) {
                lbl.setForeground(LABEL_DETAIL);
                lbl.setFont(FONT_LABEL);
            } else if (fg == null || isLightColor(fg)) {
                lbl.setForeground(TEXT_PRIMARY);
            }
        } else if (root instanceof JTextField) {
            JTextField tf = (JTextField) root;
            tf.setForeground(TEXT_PRIMARY);
            tf.setBackground(BG_INPUT);
            tf.setCaretColor(PRIMARY_BLUE);
        } else if (root instanceof JTextArea) {
            JTextArea ta = (JTextArea) root;
            ta.setForeground(TEXT_PRIMARY);
            ta.setBackground(BG_INPUT);
            ta.setCaretColor(PRIMARY_BLUE);
        } else if (root instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) root;
            sp.getViewport().setBackground(BG_SURFACE);
            sp.setBackground(BG_SURFACE);
            sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
            styleScrollBar(sp.getVerticalScrollBar());
            styleScrollBar(sp.getHorizontalScrollBar());
        } else if (root instanceof JSplitPane) {
            styleSplitPane((JSplitPane) root);
        } else if (root instanceof javax.swing.JViewport) {
            ((javax.swing.JViewport) root).setBackground(BG_SURFACE);
        } else if (root instanceof JPanel) {
            JPanel p = (JPanel) root;
            Color bg = p.getBackground();
            if (bg != null && (bg.equals(new Color(0x0B, 0x11, 0x20)) || bg.equals(new Color(0x13, 0x1F, 0x33))
                    || bg.equals(new Color(0x0F, 0x17, 0x2A)))) {
                p.setBackground(BG_SURFACE);
            }
        }
        if (root instanceof Container) {
            Container container = (Container) root;
            for (Component child : container.getComponents()) {
                styleComponentTree(child);
            }
        }
    }

    public static boolean isLightColor(Color c) {
        if (c == null) return false;
        if (c.equals(Color.WHITE) || c.equals(new Color(0xF8, 0xFA, 0xFC)) || c.equals(new Color(0xE2, 0xE8, 0xF0))
                || c.equals(new Color(0x94, 0xA3, 0xB8)) || c.equals(new Color(0x22, 0xD3, 0xEE))
                || c.equals(new Color(0x00, 0xE5, 0xFF))) {
            return true;
        }
        double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
        return luminance > 200;
    }

    public static boolean isDarkColor(Color c) {
        return !isLightColor(c);
    }

    // =========================================================================
    // CRISP VECTOR ICONS (Immune to font missing glyphs / square '▯' box bug)
    // =========================================================================

    public static class VectorIcon implements Icon {
        private final String type;
        private final int size;
        private final Color fixedColor;

        public VectorIcon(String type, int size) {
            this(type, size, null);
        }

        public VectorIcon(String type, int size, Color fixedColor) {
            this.type = type != null ? type.toUpperCase() : "INFO";
            this.size = size;
            this.fixedColor = fixedColor;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Color color = fixedColor != null ? fixedColor : (c != null ? c.getForeground() : LABEL_DETAIL);
            g2.setColor(color);
            g2.translate(x, y);

            float s = size / 24.0f; // Standard 24x24 base grid
            g2.scale(s, s);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (type) {
                case "HOME":
                case "SESSION":
                    // Info circle
                    g2.drawOval(2, 2, 20, 20);
                    g2.drawLine(12, 10, 12, 17);
                    g2.fillOval(11, 6, 2, 2);
                    break;
                case "OVERVIEW":
                    // Bar chart
                    g2.drawLine(3, 21, 21, 21);
                    g2.drawRect(5, 12, 3, 9);
                    g2.drawRect(10, 7, 3, 14);
                    g2.drawRect(15, 3, 3, 18);
                    break;
                case "BOOKS":
                    // Book with pages
                    g2.drawRect(4, 4, 16, 16);
                    g2.drawLine(12, 4, 12, 20);
                    g2.drawLine(7, 8, 10, 8);
                    g2.drawLine(7, 12, 10, 12);
                    g2.drawLine(14, 8, 17, 8);
                    g2.drawLine(14, 12, 17, 12);
                    break;
                case "STUDENTS":
                    // User silhouette
                    g2.drawOval(8, 3, 8, 8);
                    g2.drawArc(4, 13, 16, 12, 0, 180);
                    break;
                case "BORROW":
                case "CIRCULATION":
                    // Two curved circulation loop arrows
                    g2.drawArc(4, 4, 16, 16, 45, 180);
                    g2.drawLine(18, 5, 20, 10);
                    g2.drawLine(14, 8, 20, 10);
                    g2.drawArc(4, 4, 16, 16, 225, 180);
                    g2.drawLine(6, 19, 4, 14);
                    g2.drawLine(10, 16, 4, 14);
                    break;
                case "FINES":
                    // Currency coin
                    g2.drawOval(3, 3, 18, 18);
                    g2.drawLine(12, 6, 12, 18);
                    g2.drawLine(9, 9, 14, 9);
                    g2.drawLine(9, 15, 14, 15);
                    break;
                case "RESERVATIONS":
                    // Bookmark badge
                    g2.drawRect(5, 3, 14, 18);
                    g2.drawLine(9, 3, 9, 12);
                    g2.drawLine(13, 3, 13, 12);
                    g2.drawLine(9, 12, 11, 10);
                    g2.drawLine(11, 10, 13, 12);
                    break;
                case "AUDIT":
                    // Shield
                    int[] sx = {12, 20, 18, 12, 6, 4};
                    int[] sy = {2, 5, 16, 21, 16, 5};
                    g2.drawPolygon(sx, sy, 6);
                    g2.drawLine(12, 6, 12, 18);
                    break;
                case "KEY":
                    // Key for change password
                    g2.drawOval(4, 4, 8, 8);
                    g2.drawLine(10, 10, 19, 19);
                    g2.drawLine(17, 17, 19, 15);
                    g2.drawLine(15, 19, 17, 17);
                    break;
                case "LOGOUT":
                    // Door & exit arrow
                    g2.drawRect(4, 3, 9, 18);
                    g2.drawLine(12, 12, 21, 12);
                    g2.drawLine(17, 8, 21, 12);
                    g2.drawLine(17, 16, 21, 12);
                    break;
                case "REFRESH":
                    // Refresh loop arrows
                    g2.drawArc(3, 3, 18, 18, 30, 260);
                    g2.drawLine(19, 6, 21, 11);
                    g2.drawLine(15, 9, 21, 11);
                    break;
                case "SEARCH":
                    // Magnifying glass
                    g2.drawOval(4, 4, 11, 11);
                    g2.drawLine(13, 13, 20, 20);
                    break;
                case "CHECK":
                    // Checkmark
                    g2.drawLine(4, 12, 9, 17);
                    g2.drawLine(9, 17, 20, 6);
                    break;
                case "PLUS":
                case "ADD":
                    // Plus sign
                    g2.drawLine(12, 5, 12, 19);
                    g2.drawLine(5, 12, 19, 12);
                    break;
                case "EDIT":
                    // Edit pencil
                    g2.drawLine(6, 18, 18, 6);
                    g2.drawLine(15, 3, 21, 9);
                    g2.drawLine(4, 20, 8, 19);
                    g2.drawLine(4, 20, 5, 16);
                    break;
                case "DELETE":
                case "TRASH":
                    // Trash can
                    g2.drawRect(6, 8, 12, 13);
                    g2.drawLine(4, 8, 20, 8);
                    g2.drawLine(9, 5, 15, 5);
                    g2.drawLine(10, 11, 10, 18);
                    g2.drawLine(14, 11, 14, 18);
                    break;
                case "RESET":
                    // Undo arc arrow
                    g2.drawArc(4, 4, 16, 16, 45, 270);
                    g2.drawLine(12, 2, 8, 6);
                    g2.drawLine(12, 10, 8, 6);
                    break;
                case "FILTER":
                    // Funnel
                    g2.drawLine(4, 5, 20, 5);
                    g2.drawLine(4, 5, 10, 12);
                    g2.drawLine(20, 5, 14, 12);
                    g2.drawLine(10, 12, 10, 19);
                    g2.drawLine(14, 12, 14, 17);
                    g2.drawLine(10, 19, 14, 17);
                    break;
                default:
                    g2.drawOval(3, 3, 18, 18);
                    break;
            }
            g2.dispose();
        }
    }

    public static Icon getVectorIcon(String type, int size) {
        return new VectorIcon(type, size);
    }

    public static Icon getVectorIcon(String type, int size, Color color) {
        return new VectorIcon(type, size, color);
    }

    public static JPanel createGradientBannerPanel(final Color topColor, final Color bottomColor) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, topColor, 0, getHeight(), bottomColor);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
    }

    public static javax.swing.ImageIcon loadResourceIcon(String resourcePath, int targetWidth, int targetHeight) {
        try {
            java.net.URL url = ModernUIHelper.class.getResource(resourcePath);
            if (url == null && resourcePath.startsWith("/")) {
                url = ModernUIHelper.class.getResource(resourcePath.substring(1));
            }
            if (url == null && !resourcePath.startsWith("/")) {
                url = ModernUIHelper.class.getResource("/" + resourcePath);
            }
            if (url == null) {
                java.io.File f = new java.io.File("client/resources" + (resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath));
                if (f.exists()) {
                    url = f.toURI().toURL();
                }
            }
            if (url == null) {
                java.io.File f = new java.io.File("resources" + (resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath));
                if (f.exists()) {
                    url = f.toURI().toURL();
                }
            }
            if (url != null) {
                java.awt.Image img = javax.imageio.ImageIO.read(url);
                if (img != null) {
                    java.awt.Image scaled = img.getScaledInstance(targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH);
                    return new javax.swing.ImageIcon(scaled);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void styleChipButton(final JButton btn, String text, final Color bgColor, final Color textColor) {
        if (btn == null) return;
        btn.setText(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setUI(new ModernButtonUI(bgColor != null ? bgColor : BG_SURFACE, BG_HOVER,
                textColor != null ? textColor : TEXT_PRIMARY, TEXT_ACCENT, BORDER_INPUT, BORDER_FOCUS, 14, false));
        btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
    }
}
