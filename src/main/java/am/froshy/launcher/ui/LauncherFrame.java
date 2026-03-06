package am.froshy.launcher.ui;

import am.froshy.launcher.api.internal.InternalApiClient;
import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.LauncherUpdateStatus;
import am.froshy.launcher.domain.MinecraftProfile;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.UIDefaults;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.ColorUIResource;

public final class LauncherFrame extends JFrame {
    private final InternalApiClient apiClient;
    private final Runnable onClose;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final DefaultListModel<MinecraftProfile> profilesModel = new DefaultListModel<>();
    private final JList<MinecraftProfile> profilesList = new JList<>(profilesModel);

    private final JTextField idField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField versionField = new JTextField("1.20.1");
    private final JTextField javaField = new JTextField("java");
    private final JTextField jvmArgsField = new JTextField("-Xmx2G");
    private final JTextField gameArgsField = new JTextField("--username Steve");
    private final JCheckBox demoCheck = new JCheckBox("Modo demo");

    private final JCheckBox darkModeCheck = new JCheckBox("Modo oscuro");
    private final JLabel healthLabel = new JLabel("API: cargando...");
    private final JLabel updateLabel = new JLabel("Updates: comprobando...");
    private final JTextArea outputArea = new JTextArea(8, 40);
    private final JButton playButton = new JButton("Jugar");
    private final JProgressBar prepareProgress = new JProgressBar(0, 100);
    private final JLabel prepareStatusLabel = new JLabel("Estado de version: esperando...");

    public LauncherFrame(InternalApiClient apiClient, int apiPort, Runnable onClose) {
        super("Froshy Launcher");
        this.apiClient = apiClient;
        this.onClose = onClose;
        setPreferredSize(new Dimension(1100, 700));
        initUi();
        refreshProfiles();
        refreshHealth();
        refreshUpdates();
    }

    private void initUi() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Si falla el look and feel seguimos con los defaults.
        }

        profilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profilesList.setCellRenderer(new ProfileCellRenderer());
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Inicio", createHomePanel());
        tabs.addTab("Perfiles", createConfigPanel());
        tabs.addTab("Consola", createConsolePanel());

        setContentPane(tabs);
        darkModeCheck.addActionListener(e -> applyTheme(darkModeCheck.isSelected()));
        applyTheme(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                executor.shutdownNow();
                onClose.run();
            }
        });
    }

    private JPanel createHomePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        ThemedButton refreshButton = new ThemedButton("Refrescar perfiles", new Color(82, 82, 82), new Color(235, 235, 235));
        refreshButton.addActionListener(e -> refreshProfiles());

        ThemedButton checkUpdatesButton = new ThemedButton("Buscar updates", new Color(82, 82, 82), new Color(235, 235, 235));
        checkUpdatesButton.addActionListener(e -> refreshUpdates());

        JPanel topLeft = new JPanel(new GridLayout(1, 2, 8, 0));
        topLeft.add(refreshButton);
        topLeft.add(checkUpdatesButton);

        JPanel topRight = new JPanel(new GridLayout(2, 1, 0, 4));
        topRight.add(darkModeCheck);
        topRight.add(demoCheck);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(topLeft, BorderLayout.WEST);
        top.add(topRight, BorderLayout.EAST);

        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Estado"));
        statusPanel.add(healthLabel);
        statusPanel.add(updateLabel);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createTitledBorder("Perfiles disponibles"));
        center.add(new JScrollPane(profilesList), BorderLayout.CENTER);

        playButton.setFont(new Font("Arial", Font.BOLD, 16));
        playButton.setPreferredSize(new Dimension(0, 48));
        playButton.addActionListener(e -> launchSelectedProfile());

        prepareProgress.setStringPainted(true);
        prepareProgress.setValue(0);

        JPanel progressPanel = new JPanel(new BorderLayout(6, 6));
        progressPanel.setBorder(BorderFactory.createTitledBorder("Descarga de version"));
        progressPanel.add(prepareStatusLabel, BorderLayout.NORTH);
        progressPanel.add(prepareProgress, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setBorder(BorderFactory.createTitledBorder("Accion principal"));
        bottom.add(progressPanel, BorderLayout.CENTER);
        bottom.add(playButton, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(statusPanel, BorderLayout.WEST);
        panel.add(center, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        ThemedButton createButton = new ThemedButton("Crear perfil", new Color(82, 82, 82), new Color(235, 235, 235));
        createButton.addActionListener(e -> createProfile());

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createTitledBorder("Nuevo perfil"));

        addLabeledField(formPanel, "ID", idField);
        addLabeledField(formPanel, "Nombre", nameField);
        addLabeledField(formPanel, "Version", versionField);
        addLabeledField(formPanel, "Java", javaField);
        addLabeledField(formPanel, "JVM Args", jvmArgsField);
        addLabeledField(formPanel, "Game Args", gameArgsField);

        panel.add(createButton, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        ThemedButton healthButton = new ThemedButton("Verificar API", new Color(82, 82, 82), new Color(235, 235, 235));
        healthButton.addActionListener(e -> refreshHealth());

        panel.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        panel.add(healthButton, BorderLayout.SOUTH);
        return panel;
    }

    private void addLabeledField(JPanel panel, String label, JTextField field) {
        panel.add(new JLabel(label + ":"));
        panel.add(field);
    }

    private void refreshProfiles() {
        runAsync(() -> {
            List<MinecraftProfile> profiles = apiClient.listProfiles();
            SwingUtilities.invokeLater(() -> {
                profilesModel.clear();
                profiles.forEach(profilesModel::addElement);
                if (!profiles.isEmpty()) {
                    profilesList.setSelectedIndex(0);
                }
                appendOutput("Perfiles cargados: " + profiles.size());
            });
        });
    }

    private void refreshHealth() {
        runAsync(() -> {
            Object status = apiClient.health().get("status");
            SwingUtilities.invokeLater(() -> healthLabel.setText("API: " + status));
        });
    }

    private void refreshUpdates() {
        runAsync(() -> {
            LauncherUpdateStatus status = apiClient.checkUpdates();
            SwingUtilities.invokeLater(() -> {
                if ("UPDATE_AVAILABLE".equals(status.state())) {
                    updateLabel.setText("Update disponible: " + status.latestVersion() + " - " + status.notes());
                } else if ("UP_TO_DATE".equals(status.state())) {
                    updateLabel.setText("Launcher actualizado: " + status.currentVersion());
                } else {
                    updateLabel.setText("Updates: " + status.state() + " - " + status.notes());
                }
                appendOutput("Check updates -> " + status.state());
            });
        });
    }

    private void createProfile() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "ID y nombre son obligatorios", "Validacion", JOptionPane.WARNING_MESSAGE);
            return;
        }

        MinecraftProfile profile = new MinecraftProfile(
                id,
                name,
                javaField.getText().trim(),
                versionField.getText().trim(),
                splitArgs(jvmArgsField.getText()),
                splitArgs(gameArgsField.getText())
        );

        runAsync(() -> {
            MinecraftProfile created = apiClient.createProfile(profile);
            SwingUtilities.invokeLater(() -> {
                appendOutput("Perfil creado: " + created.id());
                refreshProfiles();
            });
        });
    }

    private void launchSelectedProfile() {
        MinecraftProfile selected = profilesList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un perfil", "Launch", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        playButton.setEnabled(false);
        updatePreparationUi("Preparando version " + selected.gameVersion() + "...", 0);
        appendOutput("Preparando version " + selected.gameVersion() + " para " + selected.displayName() + "...");

        runAsync(() -> {
            DownloadStatus started = apiClient.prepareVersion(selected.gameVersion());
            try {
                waitForVersionPreparation(started.downloadId());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("La preparacion de version fue interrumpida", ex);
            }

            LaunchResult result = apiClient.launch(new LaunchRequest(selected.id(), demoCheck.isSelected()));
            SwingUtilities.invokeLater(() -> {
                updatePreparationUi("Version lista", 100);
                appendOutput("Comando listo: " + result.commandLine());
                playButton.setEnabled(true);
            });
        });
    }

    private void waitForVersionPreparation(String downloadId) throws InterruptedException {
        int lastProgress = -1;
        String lastState = "";

        while (true) {
            DownloadStatus status = apiClient.getDownloadStatus(downloadId);
            if (status.progress() != lastProgress || !status.state().equals(lastState)) {
                lastProgress = status.progress();
                lastState = status.state();
                String stateText = "Version " + status.target() + " -> " + status.state() + " (" + status.progress() + "%)";
                SwingUtilities.invokeLater(() -> updatePreparationUi(stateText, status.progress()));
            }

            if ("DONE".equals(status.state())) {
                return;
            }
            if ("FAILED".equals(status.state())) {
                throw new IllegalStateException("No se pudo descargar la version " + status.target());
            }

            Thread.sleep(400);
        }
    }

    private void updatePreparationUi(String statusText, int progress) {
        prepareStatusLabel.setText(statusText);
        prepareProgress.setValue(Math.max(0, Math.min(progress, 100)));
    }

    private void runAsync(Runnable action) {
        executor.submit(() -> {
            try {
                action.run();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    updatePreparationUi("Fallo al preparar version", 0);
                    appendOutput("Error: " + ex.getMessage());
                    playButton.setEnabled(true);
                });
            }
        });
    }

    private void appendOutput(String message) {
        outputArea.append(message + System.lineSeparator());
    }

    private List<String> splitArgs(String rawArgs) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawArgs.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private void applyTheme(boolean darkMode) {
        Theme theme = darkMode
                ? new Theme(new Color(34, 34, 34), new Color(60, 63, 65), new Color(75, 110, 175), new Color(235, 235, 235), new Color(50, 50, 50))
                : new Theme(new Color(245, 245, 245), Color.WHITE, new Color(56, 142, 60), new Color(20, 20, 20), new Color(225, 225, 225));

        // Aplicar tema global en UIManager
        updateUIDefaults(theme);

        // Aplicar recursivamente a todos los componentes
        applyThemeRecursively(this, theme);

        // Forzar repaint completo
        SwingUtilities.updateComponentTreeUI(this);
        this.repaint();
    }

    private void updateUIDefaults(Theme theme) {
        UIDefaults defaults = UIManager.getDefaults();

        // Botones - FONDO FUERTE
        defaults.put("Button.background", new ColorUIResource(theme.button()));
        defaults.put("Button.foreground", new ColorUIResource(theme.text()));
        defaults.put("Button.select", new ColorUIResource(theme.accent()));
        defaults.put("Button.focus", new ColorUIResource(theme.accent()));
        defaults.put("Button.darkShadow", new ColorUIResource(theme.text()));
        defaults.put("Button.light", new ColorUIResource(theme.button()));
        defaults.put("Button.highlight", new ColorUIResource(theme.button()));

        // Paneles
        defaults.put("Panel.background", new ColorUIResource(theme.panel()));
        defaults.put("Panel.foreground", new ColorUIResource(theme.text()));

        // CheckBox
        defaults.put("CheckBox.background", new ColorUIResource(theme.panel()));
        defaults.put("CheckBox.foreground", new ColorUIResource(theme.text()));
        defaults.put("CheckBox.select", new ColorUIResource(theme.accent()));

        // Tabs
        defaults.put("TabbedPane.background", new ColorUIResource(theme.panel()));
        defaults.put("TabbedPane.foreground", new ColorUIResource(theme.text()));
        defaults.put("TabbedPane.selected", new ColorUIResource(theme.button()));

        // Labels
        defaults.put("Label.background", new ColorUIResource(theme.panel()));
        defaults.put("Label.foreground", new ColorUIResource(theme.text()));

        // Text inputs
        defaults.put("TextField.background", new ColorUIResource(theme.background()));
        defaults.put("TextField.foreground", new ColorUIResource(theme.text()));
        defaults.put("TextArea.background", new ColorUIResource(theme.background()));
        defaults.put("TextArea.foreground", new ColorUIResource(theme.text()));

        // Listas
        defaults.put("List.background", new ColorUIResource(theme.background()));
        defaults.put("List.foreground", new ColorUIResource(theme.text()));
        defaults.put("List.selectionBackground", new ColorUIResource(theme.accent()));
        defaults.put("List.selectionForeground", new ColorUIResource(Color.WHITE));
    }

    private void applyThemeRecursively(Component component, Theme theme) {
        // Aplicar colores MÁS FUERTES y VISIBLES
        if (component instanceof JButton button) {
            // FORZAR COLORES SIN DEPENDER DE UI
            button.setBackground(theme.button());
            button.setForeground(theme.text());
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setBorderPainted(true);
            button.setFocusPainted(false);
            button.setFont(new Font("Dialog", Font.BOLD, 12));

            // Remover UI personalizado y dejar que pinte por defecto con los colores establecidos
            button.setUI(null);
            button.updateUI();

            if (button == playButton) {
                button.setBackground(theme.accent());
                button.setForeground(Color.WHITE);
                button.setFont(new Font("Dialog", Font.BOLD, 16));
            }
        } else if (component instanceof JCheckBox checkBox) {
            checkBox.setBackground(theme.panel());
            checkBox.setForeground(theme.text());
            checkBox.setOpaque(true);
            checkBox.setFont(new Font("Dialog", Font.PLAIN, 12));
        } else if (component instanceof JLabel label) {
            label.setBackground(theme.panel());
            label.setForeground(theme.text());
            label.setOpaque(false);
        } else if (component instanceof JTextField textField) {
            textField.setBackground(theme.background());
            textField.setForeground(theme.text());
            textField.setCaretColor(theme.text());
            textField.setBorder(BorderFactory.createLineBorder(theme.text(), 1));
        } else if (component instanceof JTextArea textArea) {
            textArea.setBackground(theme.background());
            textArea.setForeground(theme.text());
            textArea.setCaretColor(theme.text());
            textArea.setBorder(BorderFactory.createLineBorder(theme.text(), 1));
        } else if (component instanceof JList<?> list) {
            list.setBackground(theme.background());
            list.setForeground(theme.text());
            list.setSelectionBackground(theme.accent());
            list.setSelectionForeground(Color.WHITE);
        } else if (component instanceof JProgressBar progressBar) {
            progressBar.setBackground(theme.background());
            progressBar.setForeground(theme.accent());
        } else if (component instanceof JTabbedPane tabbedPane) {
            tabbedPane.setBackground(theme.panel());
            tabbedPane.setForeground(theme.text());
        } else if (component instanceof JPanel panel) {
            panel.setBackground(theme.panel());
            panel.setForeground(theme.text());
            if (panel.getBorder() instanceof TitledBorder titledBorder) {
                titledBorder.setTitleColor(theme.text());
            }
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.setBackground(theme.background());
            scrollPane.setForeground(theme.text());
            scrollPane.getViewport().setBackground(theme.background());
        } else {
            component.setBackground(theme.panel());
            component.setForeground(theme.text());
        }

        // Recursión en contenedores
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyThemeRecursively(child, theme);
            }
        }
    }

    private static final class ProfileCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MinecraftProfile profile) {
                setText(profile.displayName() + " [" + profile.id() + "] - " + profile.gameVersion());
            }
            return this;
        }
    }

    private record Theme(Color background, Color panel, Color accent, Color text, Color button) {
    }
}



