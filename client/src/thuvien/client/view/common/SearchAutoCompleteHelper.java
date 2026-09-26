package thuvien.client.view.common;

import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import thuvien.client.controller.ClientSearchSuggestionController;

/**
 * Reusable Autocomplete Suggestion Helper for Search TextFields with 300ms debounce.
 */
public class SearchAutoCompleteHelper {

    private static final int DEBOUNCE_DELAY_MS = 300;

    public static void attach(final JTextField textField, final String entityType,
                              final ClientSearchSuggestionController suggestionController,
                              final Runnable onSelectCallback) {
        if (textField == null || suggestionController == null) {
            return;
        }

        final JPopupMenu popup = new JPopupMenu();
        popup.setFocusable(false);

        final Timer debounceTimer = new Timer(DEBOUNCE_DELAY_MS, null);
        debounceTimer.setRepeats(false);

        debounceTimer.addActionListener(e -> {
            final String text = textField.getText().trim();
            if (text.length() < 2 || !textField.isFocusOwner()) {
                popup.setVisible(false);
                return;
            }

            AsyncWorker.run(
                    () -> suggestionController.getSuggestions(entityType, text),
                    (List<String> suggestions) -> {
                        if (!textField.isFocusOwner() || suggestions == null || suggestions.isEmpty()) {
                            popup.setVisible(false);
                            return;
                        }

                        popup.removeAll();
                        int limit = Math.min(suggestions.size(), 8);
                        for (int i = 0; i < limit; i++) {
                            final String itemText = suggestions.get(i);
                            JMenuItem menuItem = new JMenuItem(itemText);
                            menuItem.setPreferredSize(new Dimension(Math.max(textField.getWidth(), 250), 24));
                            menuItem.addActionListener(ev -> {
                                textField.setText(itemText);
                                popup.setVisible(false);
                                if (onSelectCallback != null) {
                                    onSelectCallback.run();
                                }
                            });
                            popup.add(menuItem);
                        }

                        popup.show(textField, 0, textField.getHeight());
                        textField.requestFocusInWindow();
                    },
                    (Exception ex) -> popup.setVisible(false)
            );
        });

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                schedule();
            }

            private void schedule() {
                debounceTimer.restart();
            }
        });

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.setVisible(false);
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN && popup.isVisible() && popup.getComponentCount() > 0) {
                    popup.getComponent(0).requestFocusInWindow();
                }
            }
        });

        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // If focus moves outside, hide popup
                Timer closeTimer = new Timer(200, evt -> {
                    if (!popup.isFocusOwner()) {
                        popup.setVisible(false);
                    }
                });
                closeTimer.setRepeats(false);
                closeTimer.start();
            }
        });
    }
}
