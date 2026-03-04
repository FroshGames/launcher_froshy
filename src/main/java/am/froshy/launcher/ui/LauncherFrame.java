package am.froshy.launcher.ui;

import am.froshy.launcher.api.internal.InternalApiClient;
import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
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
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LauncherFrame extends JFrame {
    private final InternalApiClient apiClient;
    private final Runnable onClose;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Paleta de colores
    private static final Color PRIMARY_COLOR = new Color(25, 118, 210);
    private static final Color SECONDARY_COLOR = new Color(56, 142, 60);
    private static final Color ACCENT_COLOR = new Color(255, 111, 0);
    private static final Color BG_COLOR = new Color(240, 240, 240);
    private static final Color DARK_BG = new Color(50, 50, 50);
    private static final Color TEXT_COLOR = new Color(30, 30, 30);

    private final DefaultListModel<MinecraftProfile> profilesModel = new DefaultListModel<>();
    private final JList<MinecraftProfile> profilesList = new JList<>(profilesModel);
    private final JTextField idField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField versionField = new JTextField("1.20.1");
    private final JTextField javaField = new JTextField("java");
    private final JTextField jvmArgsField = new JTextField("-Xmx2G");
    private final JTextField gameArgsField = new JTextField("--username Steve");
    private final JCheckBox demoCheck = new JCheckBox("Demo mode");
    private final JTextField downloadTargetField = new JTextField("assets");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel healthLabel = new JLabel("Health: cargando...");
    private final JTextArea outputArea = new JTextArea(8, 40);

    public LauncherFrame(InternalApiClient apiClient, int apiPort, Runnable onClose) {
        super("Froshy Launcher - v1.0");
        this.apiClient = apiClient;
        this.onClose = onClose;
        setPreferredSize(new Dimension(1200, 700));
        initUi();
        refreshProfiles();
        refreshHealth();
    }

    private void initUi() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);

        // Configurar tema
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignorar errores de tema
        }

        profilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profilesList.setCellRenderer(new ProfileCellRenderer());
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // Configurar barra de progreso
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Arial", Font.BOLD, 12));

        // Panel principal con pestañas
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Perfiles", createProfilesPanel());
        tabbedPane.addTab("Configuración", createConfigPanel());
        tabbedPane.addTab("Descargas", createDownloadsPanel());
        tabbedPane.addTab("Consola", createConsolePanel());

        setContentPane(tabbedPane);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                executor.shutdownNow();
                onClose.run();
            }
        });
    }

    private JPanel createProfilesPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton refreshButton = new JButton("🔄 Refrescar");
        refreshButton.addActionListener(e -> refreshProfiles());

        JButton launchButton = new JButton("▶️ Jugar");
        launchButton.setFont(new Font("Arial", Font.BOLD, 14));
        launchButton.setBackground(SECONDARY_COLOR);
        launchButton.setForeground(Color.WHITE);
        launchButton.addActionListener(e -> launchSelectedProfile());

        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.add(refreshButton, BorderLayout.WEST);
        topPanel.add(launchButton, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(profilesList), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton createButton = new JButton("✚ Crear perfil");
        createButton.addActionListener(e -> createProfile());

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createTitledBorder("Nuevo perfil"));

        addLabeledField(formPanel, "ID:", idField);
        addLabeledField(formPanel, "Nombre:", nameField);
        addLabeledField(formPanel, "Versión:", versionField);
        addLabeledField(formPanel, "Java:", javaField);
        addLabeledField(formPanel, "JVM Args:", jvmArgsField);
        addLabeledField(formPanel, "Game Args:", gameArgsField);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(createButton, BorderLayout.WEST);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDownloadsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton downloadButton = new JButton("⬇️ Iniciar descarga");
        downloadButton.addActionListener(e -> startDownload());

        JPanel controlPanel = new JPanel(new BorderLayout(8, 0));
        controlPanel.add(new JLabel("Target:"), BorderLayout.WEST);
        controlPanel.add(downloadTargetField, BorderLayout.CENTER);
        controlPanel.add(downloadButton, BorderLayout.EAST);

        progressBar.setPreferredSize(new Dimension(0, 30));

        JPanel statusPanel = new JPanel(new BorderLayout(8, 8));
        statusPanel.add(new JLabel("Progreso:"), BorderLayout.NORTH);
        statusPanel.add(progressBar, BorderLayout.CENTER);

        panel.add(controlPanel, BorderLayout.NORTH);
        panel.add(statusPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton healthButton = new JButton("🏥 Verificar salud");
        healthButton.addActionListener(e -> refreshHealth());

        panel.add(healthLabel, BorderLayout.NORTH);
        panel.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        panel.add(healthButton, BorderLayout.SOUTH);

        return panel;
    }

    private void addLabeledField(JPanel panel, String label, JTextField field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private void refreshProfiles() {
        runAsync(() -> {
            List<MinecraftProfile> profiles = apiClient.listProfiles();
            SwingUtilities.invokeLater(() -> {
                profilesModel.clear();
                profiles.forEach(profilesModel::addElement);
                appendOutput("Perfiles cargados: " + profiles.size());
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

        runAsync(() -> {
            LaunchResult result = apiClient.launch(new LaunchRequest(selected.id(), demoCheck.isSelected()));
            SwingUtilities.invokeLater(() -> appendOutput("Launch: " + result.commandLine()));
        });
    }

    private void startDownload() {
        String target = downloadTargetField.getText().trim();
        if (target.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Target no puede estar vacio", "Descarga", JOptionPane.WARNING_MESSAGE);
            return;
        }

        runAsync(() -> {
            DownloadStatus queued = apiClient.startDownload(target);
            SwingUtilities.invokeLater(() -> {
                appendOutput("Descarga iniciada: " + queued.downloadId());
                pollDownload(queued.downloadId());
            });
        });
    }

    private void pollDownload(String downloadId) {
        Timer timer = new Timer(400, null);
        timer.addActionListener(e -> runAsync(() -> {
            DownloadStatus status = apiClient.getDownloadStatus(downloadId);
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(status.progress());
                appendOutput("Download " + status.downloadId() + ": " + status.state() + " (" + status.progress() + "%)");
                if ("DONE".equals(status.state())) {
                    timer.stop();
                }
            });
        }));
        timer.start();
    }

    private void refreshHealth() {
        runAsync(() -> {
            Object status = apiClient.health().get("status");
            SwingUtilities.invokeLater(() -> healthLabel.setText("Health: " + status));
        });
    }

    private void runAsync(Runnable action) {
        executor.submit(() -> {
            try {
                action.run();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendOutput("Error: " + ex.getMessage()));
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
}

