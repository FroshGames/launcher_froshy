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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
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
        super("Froshy Launcher - API " + apiPort);
        this.apiClient = apiClient;
        this.onClose = onClose;
        initUi();
        refreshProfiles();
        refreshHealth();
    }

    private void initUi() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(980, 600);
        setLocationRelativeTo(null);

        profilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profilesList.setCellRenderer(new ProfileCellRenderer());
        outputArea.setEditable(false);

        JButton refreshButton = new JButton("Refrescar perfiles");
        refreshButton.addActionListener(e -> refreshProfiles());

        JButton createButton = new JButton("Crear perfil");
        createButton.addActionListener(e -> createProfile());

        JButton launchButton = new JButton("Launch");
        launchButton.addActionListener(e -> launchSelectedProfile());

        JButton downloadButton = new JButton("Descargar");
        downloadButton.addActionListener(e -> startDownload());

        JButton healthButton = new JButton("Health");
        healthButton.addActionListener(e -> refreshHealth());

        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Perfiles"));
        leftPanel.add(new JScrollPane(profilesList), BorderLayout.CENTER);
        leftPanel.add(refreshButton, BorderLayout.SOUTH);

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createTitledBorder("Nuevo perfil"));
        formPanel.add(new JLabel("ID"));
        formPanel.add(idField);
        formPanel.add(new JLabel("Nombre"));
        formPanel.add(nameField);
        formPanel.add(new JLabel("Version"));
        formPanel.add(versionField);
        formPanel.add(new JLabel("Java"));
        formPanel.add(javaField);
        formPanel.add(new JLabel("JVM args"));
        formPanel.add(jvmArgsField);
        formPanel.add(new JLabel("Game args"));
        formPanel.add(gameArgsField);

        JPanel actionsPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Acciones"));
        actionsPanel.add(createButton);
        actionsPanel.add(demoCheck);
        actionsPanel.add(launchButton);
        actionsPanel.add(healthButton);
        actionsPanel.add(new JLabel("Target descarga"));
        actionsPanel.add(downloadTargetField);
        actionsPanel.add(downloadButton);
        actionsPanel.add(progressBar);

        JPanel rightTop = new JPanel(new BorderLayout(8, 8));
        rightTop.add(formPanel, BorderLayout.CENTER);
        rightTop.add(actionsPanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout(8, 8));
        rightPanel.add(healthLabel, BorderLayout.NORTH);
        rightPanel.add(rightTop, BorderLayout.CENTER);
        rightPanel.add(new JScrollPane(outputArea), BorderLayout.SOUTH);

        JPanel content = new JPanel(new GridLayout(1, 2, 8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(leftPanel);
        content.add(rightPanel);

        setContentPane(content);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                executor.shutdownNow();
                onClose.run();
            }
        });
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

