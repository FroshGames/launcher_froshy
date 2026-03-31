package am.froshy.mialu.launcher.ui;
import am.froshy.mialu.launcher.api.internal.InternalApiClient;
import am.froshy.mialu.launcher.application.MojangSkinProvider;
import am.froshy.mialu.launcher.domain.LaunchRequest;
import am.froshy.mialu.launcher.domain.LauncherUpdateStatus;
import am.froshy.mialu.launcher.domain.MinecraftProfile;
import am.froshy.mialu.launcher.domain.PreparedLaunchStatus;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
/** Launcher UI - diseño cyberpunk/neon inspirado en imagen de referencia. */
public final class LauncherFrame extends JFrame {
    private static final int MAX_CONSOLE_LINES = 2500;
    private static final int TRIM_TO_LINES = 1800;

    static final Color C_BG      = new Color(0x08, 0x08, 0x1a);
    static final Color C_SIDEBAR = new Color(0x09, 0x09, 0x1c);
    static final Color C_CYAN    = new Color(0x00, 0xe5, 0xff);
    static final Color C_MAGENTA = new Color(0xe0, 0x40, 0xfb);
    static final Color C_BORDER  = new Color(0x00, 0xb8, 0xcc);
    static final Color C_TEXT    = new Color(0xe0, 0xe0, 0xff);
    static final Color C_DIM     = new Color(0x60, 0x60, 0x90);
    static final Color C_GREEN   = new Color(0x00, 0xff, 0x88);
    static final Color C_CONSOLE = new Color(0x03, 0x03, 0x12);
    private final InternalApiClient apiClient;
    private final Runnable onClose;
    private final String launcherVersion;
    private final MojangSkinProvider mojangSkinProvider;
    private final ExecutorService          executor  = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService uiSched   = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> skinLoadingTask = null;
    private final DefaultListModel<MinecraftProfile> profilesModel = new DefaultListModel<>();
    private final JList<MinecraftProfile> profilesList = new JList<>(profilesModel);
    private final JTextField idField       = new JTextField();
    private final JTextField nameField     = new JTextField();
    private final JTextField usernameField = new JTextField("Steve");
    private final JTextField oauthClientIdField = new JTextField("");
    private final JTextField oauthTenantField = new JTextField("consumers");
    private final JComboBox<String> profileModeField = new JComboBox<>(new String[]{"INSTANCIA MANUAL", "IMPORTAR MODPACK"});
    private final JComboBox<String> versionField = new JComboBox<>(new String[]{"1.21", "1.20.6", "1.20.4", "1.20.1", "1.19.2", "1.18.2", "1.16.5", "1.12.2"});
    private final JComboBox<String> loaderTypeField = new JComboBox<>(new String[]{"VANILLA", "FORGE", "NEOFORGE", "FABRIC", "QUILT"});
    private final JComboBox<String> loaderVersionField = new JComboBox<>(new String[]{"", "latest"});
    private final JTextField modpackPathField = new JTextField("");
    private final JComboBox<String> modpackCompatField = new JComboBox<>(new String[]{"BOTH", "MODRINTH_ONLY", "CURSEFORGE_ONLY"});
    private final JList<MinecraftProfile> profilesEditorList = new JList<>(profilesModel);
    private final JTextField jvmArgsField  = new JTextField("-Xmx2G");
    private final JTextField gameArgsField = new JTextField("");
    private final JTextArea  outputArea    = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JProgressBar phaseProgressBar = new JProgressBar(0, 100);
    private final JLabel statusLbl = new JLabel("Listo");
    private final JLabel phaseLbl = new JLabel("IDLE");
    private final JLabel healthLbl = new JLabel("\u25CF API");
    private final JLabel updateLbl = new JLabel("");
    private final JLabel microsoftStatusLbl = new JLabel("Premium: offline");
    // Estado del juego en ejecución
    private volatile boolean         gameRunning     = false;
    private volatile String          currentLaunchId = null;
    private volatile int             lastOutputIdx   = 0;
    private          ScheduledFuture<?> outputPoll  = null;
    private JButton    launchBtn;
    private JButton    saveBtn;
    private JButton    modpackBrowseBtn;
    private boolean    suppressComboEvents = false;
    private String     editingProfileId = null;
    private JPanel     contentStack;
    private CardLayout contentCards;
    private Point      dragOrigin;
    private JButton    deleteBtn;
    public LauncherFrame(InternalApiClient apiClient, int apiPort, String launcherVersion, Runnable onClose) {
        super();
        this.apiClient       = apiClient;
        this.onClose         = onClose;
        this.launcherVersion = launcherVersion != null ? launcherVersion : "?";
        this.mojangSkinProvider = new MojangSkinProvider();
        buildUi();
        refreshProfiles();
        refreshHealth();
        refreshUpdates();
        refreshMicrosoftSession();
    }
    private void buildUi() {
        setUndecorated(true);
        setSize(1060, 620);
        setMinimumSize(new Dimension(900, 540));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(C_BG);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
        root.add(buildTitleBar(),    BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildRightPanel(),  BorderLayout.EAST);
        setContentPane(root);
        
        // Agregar listener para cargar skins automáticamente
        usernameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleLoadSkin(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleLoadSkin(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleLoadSkin(); }
        });
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { syncIdPreviewFromName(); }
            @Override public void removeUpdate(DocumentEvent e) { syncIdPreviewFromName(); }
            @Override public void changedUpdate(DocumentEvent e) { syncIdPreviewFromName(); }
        });
        
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                executor.shutdownNow();
                onClose.run();
            }
        });
    }
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(0x05, 0x05, 0x16));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(C_BORDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(0, 34));
        JLabel title = new JLabel("MINECRAFT LAUNCHER", JLabel.CENTER);
        title.setFont(new Font("Dialog", Font.BOLD, 13));
        title.setForeground(C_TEXT);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        btnRow.setOpaque(false);
        btnRow.add(makeTitleBtn("MINIMIZE", e -> setState(Frame.ICONIFIED)));
        btnRow.add(makeTitleBtn("MAXIMIZE", e -> {
            if (getExtendedState() == Frame.MAXIMIZED_BOTH) setExtendedState(Frame.NORMAL);
            else setExtendedState(Frame.MAXIMIZED_BOTH);
        }));
        btnRow.add(makeTitleBtn("X", e -> dispose()));
        bar.add(title,  BorderLayout.CENTER);
        bar.add(btnRow, BorderLayout.EAST);
        MouseAdapter drag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { dragOrigin = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOrigin != null) {
                    Point loc = getLocation();
                    setLocation(loc.x + e.getX() - dragOrigin.x, loc.y + e.getY() - dragOrigin.y);
                }
            }
        };
        title.addMouseListener(drag); title.addMouseMotionListener(drag);
        bar.addMouseListener(drag);   bar.addMouseMotionListener(drag);
        return bar;
    }
    private JButton makeTitleBtn(String text, ActionListener al) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(0x1a,0x1a,0x3a) : new Color(0x0a,0x0a,0x22));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.setColor(new Color(0x40, 0x40, 0x66));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 4, 4);
                g2.setColor(C_TEXT); g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension("X".equals(text) ? 24 : 82, 22));
        btn.setFont(new Font("Dialog", Font.PLAIN, 10));
        btn.setFocusPainted(false); btn.setBorderPainted(false); btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(al);
        return btn;
    }
    private JPanel buildCenterPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        JLabel logoRow = new JLabel("\u2B21  Launcher_Mialu  |  MINECRAFT LAUNCHER ECOSYSTEM", JLabel.CENTER);
        logoRow.setFont(new Font("Dialog", Font.PLAIN, 10)); logoRow.setForeground(C_DIM);
        JLabel mainTitle = new JLabel("MINECRAFT LAUNCHER", JLabel.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics fm = g2.getFontMetrics(getFont());
                int x = (getWidth()-fm.stringWidth(getText()))/2;
                int y = getHeight()-fm.getDescent()-2;
                g2.setColor(new Color(0, 229, 255, 35));
                Font gf = getFont().deriveFont(getFont().getSize2D()+3);
                g2.setFont(gf); FontMetrics fmg = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth()-fmg.stringWidth(getText()))/2, y+2);
                g2.setColor(C_CYAN); g2.setFont(getFont()); g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        mainTitle.setFont(new Font("Dialog", Font.BOLD, 26)); mainTitle.setForeground(C_CYAN);
        mainTitle.setPreferredSize(new Dimension(0, 38));
        launchBtn = buildNeonBtn("PLAY MINECRAFT", C_MAGENTA, 224, 38);
        launchBtn.addActionListener(e -> launchSelectedProfile());
        JPanel launchRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        launchRow.setOpaque(false); launchRow.add(launchBtn);
        header.add(logoRow,   BorderLayout.NORTH);
        header.add(mainTitle, BorderLayout.CENTER);
        header.add(launchRow, BorderLayout.SOUTH);
        contentCards = new CardLayout();
        contentStack = new JPanel(contentCards) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(0x06, 0x06, 0x18)); g2.fillRect(0,0,getWidth(),getHeight());
                drawCircuit(g2, getWidth(), getHeight()); g2.dispose();
            }
        };
        contentStack.setOpaque(false);
        contentStack.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
        contentStack.add(buildHomeCard(),     "HOME");
        contentStack.add(buildProfilesCard(), "PROFILES");
        contentStack.add(buildSettingsCard(), "SETTINGS");
        contentStack.add(buildConsoleCard(),  "CONSOLE");
        contentCards.show(contentStack, "HOME");
        outer.add(header,           BorderLayout.NORTH);
        outer.add(contentStack,     BorderLayout.CENTER);
        outer.add(buildBottomBar(), BorderLayout.SOUTH);
        return outer;
    }
    private JPanel buildBottomBar() {
        JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navBtns.setOpaque(false);
        navBtns.add(makeNavBtn("Inicio",   () -> contentCards.show(contentStack, "HOME")));
        navBtns.add(makeNavBtn("Consola",  () -> contentCards.show(contentStack, "CONSOLE")));
        JPanel social = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        social.setOpaque(false);
        addSocialIcon(social, "\u2B21", "GitHub");
        addSocialIcon(social, "X",      "Twitter");
        addSocialIcon(social, "in",     "LinkedIn");
        JLabel email = new JLabel("support@mialustudio.am");
        email.setFont(new Font("Dialog", Font.PLAIN, 9)); email.setForeground(C_DIM); social.add(email);
        JLabel supportLink = new JLabel("Support & Feedback");
        supportLink.setFont(new Font("Dialog", Font.PLAIN, 9)); supportLink.setForeground(C_BORDER);
        supportLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        supportLink.setBorder(BorderFactory.createEmptyBorder(0,0,0,2));
        JPanel statusRow = new JPanel(new BorderLayout()); statusRow.setOpaque(false);
        JLabel verLbl = new JLabel("  Launcher_Mialu v" + launcherVersion);
        verLbl.setFont(new Font("Dialog", Font.PLAIN, 9)); verLbl.setForeground(C_DIM);
        statusLbl.setFont(new Font("Dialog", Font.PLAIN, 9)); statusLbl.setForeground(C_GREEN);
        statusLbl.setBorder(BorderFactory.createEmptyBorder(0,0,0,4));
        statusRow.add(verLbl, BorderLayout.WEST); statusRow.add(statusLbl, BorderLayout.EAST);
        JPanel main = new JPanel(new BorderLayout(4, 0)); main.setOpaque(false);
        main.add(navBtns, BorderLayout.WEST); main.add(social, BorderLayout.CENTER); main.add(supportLink, BorderLayout.EAST);
        JPanel group = new JPanel(new BorderLayout(0, 2)); group.setOpaque(false);
        group.setBorder(BorderFactory.createEmptyBorder(4,0,0,0));
        group.add(main, BorderLayout.NORTH); group.add(statusRow, BorderLayout.SOUTH);
        return group;
    }
    private void addSocialIcon(JPanel panel, String icon, String tooltip) {
        JLabel l = new JLabel(icon, JLabel.CENTER);
        l.setFont(new Font("Dialog", Font.BOLD, 13)); l.setForeground(C_CYAN); l.setToolTipText(tooltip);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); panel.add(l);
    }
    private JButton makeNavBtn(String text, Runnable action) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(0x18,0x18,0x38) : new Color(0x0e,0x0e,0x28));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),4,4);
                g2.setColor(C_BORDER); g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,4,4);
                g2.setColor(C_TEXT); g2.setFont(getFont()); FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Dialog", Font.PLAIN, 10)); btn.setPreferredSize(new Dimension(72, 22));
        btn.setFocusPainted(false); btn.setBorderPainted(false); btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); btn.addActionListener(e -> action.run());
        return btn;
    }
    private JPanel buildHomeCard() {
        JPanel panel = new JPanel(new BorderLayout(6, 6)); panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        JLabel selTitle = new JLabel("\u25BA Perfil seleccionado");
        selTitle.setFont(new Font("Dialog", Font.BOLD, 11)); selTitle.setForeground(C_CYAN);
        profilesList.setOpaque(true);
        profilesList.setBackground(new Color(0x08, 0x08, 0x1e));
        profilesList.setForeground(C_TEXT);
        profilesList.setSelectionBackground(new Color(0x18, 0x18, 0x40));
        profilesList.setSelectionForeground(C_CYAN);
        profilesList.setFont(new Font("Dialog", Font.PLAIN, 12));
        profilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profilesList.setCellRenderer(new ProfileRenderer());
        installDeleteContextMenu(profilesList);
        profilesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                MinecraftProfile p = profilesList.getSelectedValue();
                if (p != null) selTitle.setText("\u25BA " + p.displayName() + "  \u2014  v" + p.gameVersion());
            }
        });
        JScrollPane scroll = new JScrollPane(profilesList);
        scroll.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
        scroll.setOpaque(false); scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(new Color(0x08, 0x08, 0x1e));
        scroll.setBackground(new Color(0x08, 0x08, 0x1e));
        progressBar.setStringPainted(true); progressBar.setValue(0);
        progressBar.setOpaque(true); progressBar.setBackground(new Color(0x0a, 0x0a, 0x20));
        progressBar.setForeground(C_CYAN); progressBar.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
        progressBar.setPreferredSize(new Dimension(0, 18));

        phaseProgressBar.setStringPainted(true); phaseProgressBar.setValue(0);
        phaseProgressBar.setString("Etapa: 0%");
        phaseProgressBar.setOpaque(true); phaseProgressBar.setBackground(new Color(0x0a, 0x0a, 0x20));
        phaseProgressBar.setForeground(C_MAGENTA); phaseProgressBar.setBorder(BorderFactory.createLineBorder(new Color(0x66, 0x33, 0x99), 1));
        phaseProgressBar.setPreferredSize(new Dimension(0, 14));

        phaseLbl.setOpaque(true);
        phaseLbl.setBackground(new Color(0x1a, 0x1a, 0x33));
        phaseLbl.setForeground(C_DIM);
        phaseLbl.setFont(new Font("Dialog", Font.BOLD, 10));
        phaseLbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x33, 0x33, 0x66), 1),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)
        ));

        healthLbl.setFont(new Font("Dialog", Font.PLAIN, 10)); healthLbl.setForeground(C_GREEN);
        updateLbl.setFont(new Font("Dialog", Font.PLAIN, 10)); updateLbl.setForeground(C_DIM);
        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); infoRow.setOpaque(false);
        infoRow.add(healthLbl); infoRow.add(updateLbl); infoRow.add(phaseLbl);
        JPanel bottomGroup = new JPanel(new BorderLayout(4, 2)); bottomGroup.setOpaque(false);
        bottomGroup.add(progressBar, BorderLayout.NORTH);
        bottomGroup.add(phaseProgressBar, BorderLayout.CENTER);
        bottomGroup.add(infoRow, BorderLayout.SOUTH);
        panel.add(selTitle,    BorderLayout.NORTH);
        panel.add(scroll,      BorderLayout.CENTER);
        panel.add(bottomGroup, BorderLayout.SOUTH);
        return panel;
    }
    private JPanel buildProfilesCard() {
        JPanel panel = new JPanel(new BorderLayout(10, 8)); panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));

        JLabel title = new JLabel("GESTION DE PERFILES");
        title.setFont(new Font("Dialog", Font.BOLD, 13)); title.setForeground(C_CYAN);

        JPanel split = new JPanel(new GridLayout(1, 2, 10, 0));
        split.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        profileModeField.setEditable(false);
        versionField.setEditable(false);
        loaderTypeField.setEditable(false);
        loaderVersionField.setEditable(true);
        modpackCompatField.setEditable(false);
        idField.setEditable(false);
        idField.setToolTipText("Se genera automaticamente desde el nombre del perfil");
        addFormField(form, "ID (auto):", idField);
        addFormField(form, "Nombre:", nameField);
        addFormField(form, "Modo:", profileModeField);
        addFormField(form, "Version:", versionField);
        addFormField(form, "Loader:", loaderTypeField);
        addFormField(form, "Ver. Loader:", loaderVersionField);
        addFormField(form, "Modpack (.mrpack/.zip):", buildModpackPickerField());
        addFormField(form, "JVM Args:", jvmArgsField);
        addFormField(form, "Game Args:", gameArgsField);
        addFormField(form, "Compat. modpacks:", modpackCompatField);
        left.add(form, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setOpaque(false);
        right.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
        profilesEditorList.setOpaque(true);
        profilesEditorList.setBackground(new Color(0x08, 0x08, 0x1e));
        profilesEditorList.setForeground(C_TEXT);
        profilesEditorList.setSelectionBackground(new Color(0x18, 0x18, 0x40));
        profilesEditorList.setSelectionForeground(C_CYAN);
        profilesEditorList.setFont(new Font("Dialog", Font.PLAIN, 11));
        profilesEditorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profilesEditorList.setFixedCellHeight(58);
        profilesEditorList.setCellRenderer(new ProfileEditorRenderer());
        installDeleteContextMenu(profilesEditorList);
        profilesEditorList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                MinecraftProfile p = profilesEditorList.getSelectedValue();
                if (p != null) {
                    loadProfileIntoForm(p);
                    profilesList.setSelectedValue(p, true);
                }
            }
        });
        JScrollPane editorScroll = new JScrollPane(profilesEditorList);
        editorScroll.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        editorScroll.getViewport().setBackground(new Color(0x08, 0x08, 0x1e));
        right.add(editorScroll, BorderLayout.CENTER);

        split.add(left);
        split.add(right);

        JButton newBtn = buildNeonBtn("Nuevo perfil", C_CYAN, 140, 30);
        saveBtn = buildNeonBtn("GUARDAR CAMBIOS", C_MAGENTA, 210, 38);
        deleteBtn = buildNeonBtn("Eliminar instancia", new Color(0xff, 0x44, 0x44), 190, 34);
        JButton refreshBtn = buildNeonBtn("Refrescar", C_BORDER, 110, 30);
        newBtn.addActionListener(e -> startCreateProfileMode());
        saveBtn.addActionListener(e -> upsertProfile());
        deleteBtn.addActionListener(e -> deleteSelectedProfile());
        refreshBtn.addActionListener(e -> refreshProfiles());
        profileModeField.addActionListener(e -> applyProfileModeUi());
        modpackCompatField.addActionListener(e -> updateModpackCompatibilityFromUi());
        versionField.addActionListener(e -> refreshLoaderSuggestions());
        loaderTypeField.addActionListener(e -> refreshLoaderSuggestions());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.add(refreshBtn);
        btnRow.add(newBtn);
        btnRow.add(deleteBtn);
        btnRow.add(saveBtn);

        panel.add(title, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        panel.add(btnRow, BorderLayout.SOUTH);

        refreshLoaderSuggestions();
        applyProfileModeUi();
        loadModpackCompatibility();
        return panel;
    }

    private JPanel buildSettingsCard() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));

        JLabel title = new JLabel("CONFIGURACION GLOBAL");
        title.setFont(new Font("Dialog", Font.BOLD, 13));
        title.setForeground(C_CYAN);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        addFormField(form, "Nickname global:", usernameField);
        addFormField(form, "Microsoft Client ID (opcional):", oauthClientIdField);
        addFormField(form, "Microsoft Tenant:", oauthTenantField);
        JCheckBox preferPremiumCheck = new JCheckBox("Usar sesion premium si esta disponible");
        preferPremiumCheck.setOpaque(false);
        preferPremiumCheck.setForeground(C_TEXT);
        preferPremiumCheck.setFont(new Font("Dialog", Font.PLAIN, 11));
        addFormField(form, "Cuenta:", preferPremiumCheck);

        JButton saveSettingsBtn = buildNeonBtn("Guardar configuracion", C_BORDER, 220, 34);
        JButton msLoginBtn = buildNeonBtn("Login Microsoft", C_GREEN, 170, 30);
        JButton msLogoutBtn = buildNeonBtn("Logout Premium", new Color(0xff, 0x99, 0x33), 165, 30);

        saveSettingsBtn.addActionListener(e -> saveGlobalUserSettings(preferPremiumCheck.isSelected()));
        msLoginBtn.addActionListener(e -> startMicrosoftLogin());
        msLogoutBtn.addActionListener(e -> logoutMicrosoft());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        microsoftStatusLbl.setFont(new Font("Dialog", Font.PLAIN, 10));
        microsoftStatusLbl.setForeground(C_DIM);
        actions.add(microsoftStatusLbl);
        actions.add(msLoginBtn);
        actions.add(msLogoutBtn);
        actions.add(saveSettingsBtn);

        panel.add(title, BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);

        loadGlobalUserSettings(preferPremiumCheck);
        return panel;
    }
    private JPanel buildConsoleCard() {
        JPanel panel = new JPanel(new BorderLayout(6, 6)); panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        outputArea.setEditable(false); outputArea.setOpaque(true);
        outputArea.setBackground(C_CONSOLE); outputArea.setForeground(C_GREEN);
        outputArea.setCaretColor(C_GREEN); outputArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        outputArea.setLineWrap(true);
        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0, 34, 0), 1));
        scroll.getViewport().setBackground(C_CONSOLE); scroll.setBackground(C_CONSOLE);
        JButton clearBtn = buildNeonBtn("Limpiar",   C_DIM,    90, 26);
        JButton apiBtn   = buildNeonBtn("Check API", C_BORDER, 90, 26);
        clearBtn.addActionListener(e -> outputArea.setText(""));
        apiBtn.addActionListener(e   -> refreshHealth());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); btnRow.setOpaque(false);
        btnRow.add(clearBtn); btnRow.add(apiBtn);
        panel.add(scroll, BorderLayout.CENTER); panel.add(btnRow, BorderLayout.SOUTH);
        return panel;
    }
    private JPanel buildRightPanel() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(C_SIDEBAR); g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(C_BORDER); g.fillRect(0,0,1,getHeight());
                drawWave((Graphics2D)g, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false); panel.setPreferredSize(new Dimension(110, 0));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(Box.createVerticalStrut(10));
        panel.add(makeIconBtn("\u2699", "SETTINGS", e -> contentCards.show(contentStack, "SETTINGS")));
        panel.add(Box.createVerticalStrut(10));
        panel.add(makeIconBtn("\u25CE", "PROFILE",  e -> contentCards.show(contentStack, "PROFILES")));
        panel.add(Box.createVerticalGlue());
        panel.add(Box.createVerticalStrut(80));
        JLabel sparkle = new JLabel("\u2726", JLabel.CENTER);
        sparkle.setFont(new Font("Dialog", Font.BOLD, 16)); sparkle.setForeground(new Color(0x80, 0xc0, 0xd0));
        sparkle.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(sparkle); panel.add(Box.createVerticalStrut(8));
        return panel;
    }
    private JPanel makeIconBtn(String icon, String label, ActionListener al) {
        JPanel wrapper = new JPanel(); wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setMaximumSize(new Dimension(110, 58));
        JButton btn = new JButton(icon) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(0x1a,0x1a,0x3a) : new Color(0x0e,0x0e,0x28));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.setColor(C_BORDER); g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);
                g2.setColor(C_TEXT); g2.setFont(getFont()); FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Dialog", Font.PLAIN, 18)); btn.setPreferredSize(new Dimension(46, 38));
        btn.setMaximumSize(new Dimension(46, 38)); btn.setFocusPainted(false); btn.setBorderPainted(false);
        btn.setContentAreaFilled(false); btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(al); btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel lbl = new JLabel(label, JLabel.CENTER);
        lbl.setFont(new Font("Dialog", Font.BOLD, 9)); lbl.setForeground(C_DIM);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        wrapper.add(btn); wrapper.add(lbl);
        return wrapper;
    }
    private JButton buildNeonBtn(String text, Color accent, int w, int h) {
        // Para botones de lanzamiento: refleja estado gameRunning
        boolean isPlayBtn = "PLAY".equals(text) || "PLAY MINECRAFT".equals(text);
        boolean isStrongCta = "PLAY".equals(text) || "GUARDAR CAMBIOS".equals(text) || "CREAR PERFIL".equals(text);
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color eff = (isPlayBtn && gameRunning) ? C_GREEN : accent;
                int r = eff.getRed(), gn = eff.getGreen(), b = eff.getBlue();
                Color base = getModel().isPressed()
                        ? new Color(r/5, gn/5, b/5)
                        : getModel().isRollover()
                                ? new Color(Math.min(255,r/3), Math.min(255,gn/3), Math.min(255,b/3))
                                : new Color(r/7, gn/7, b/7);
                int radius = isStrongCta ? 10 : 8;
                if (isStrongCta) {
                    GradientPaint gp = new GradientPaint(0, 0,
                            new Color(Math.max(0, r / 8), Math.max(0, gn / 8), Math.max(0, b / 8)),
                            getWidth(), getHeight(),
                            new Color(Math.max(0, r / 4), Math.max(0, gn / 4), Math.max(0, b / 4)));
                    g2.setPaint(gp);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                } else {
                    g2.setColor(base);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                }

                g2.setColor(new Color(r,gn,b,isStrongCta ? 75 : 50)); g2.setStroke(new BasicStroke(isStrongCta ? 6f : 5f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,radius + 2,radius + 2);
                g2.setColor(eff); g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1,1,getWidth()-3,getHeight()-3,radius,radius);
                // Texto dinámico
                String displayText = (isPlayBtn && gameRunning) ? "RUNNING" : getText();
                g2.setColor(C_TEXT); g2.setFont(getFont()); FontMetrics fm = g2.getFontMetrics();
                g2.drawString(displayText, (getWidth()-fm.stringWidth(displayText))/2,
                        (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Dialog", Font.BOLD, 12)); btn.setPreferredSize(new Dimension(w, h));
        btn.setFocusPainted(false); btn.setBorderPainted(false); btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
    private void drawCircuit(Graphics2D g2, int w, int h) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0, 80, 110, 22)); g2.setStroke(new BasicStroke(0.8f));
        int sp = 38;
        for (int y = sp; y < h; y += sp) g2.drawLine(0, y, w, y);
        for (int x = sp; x < w; x += sp) g2.drawLine(x, 0, x, h);
        g2.setColor(new Color(0, 160, 200, 45));
        for (int y = sp; y < h; y += sp) for (int x = sp; x < w; x += sp) g2.fillOval(x-2, y-2, 4, 4);
        g2.setColor(new Color(0, 200, 240, 18)); g2.setStroke(new BasicStroke(2f));
        int cx = w/2, cy = h/2;
        g2.drawLine(cx-90, cy, cx+90, cy); g2.drawLine(cx, cy-70, cx, cy+70);
        g2.drawLine(cx-90, cy, cx-90, cy-sp); g2.drawLine(cx+90, cy, cx+90, cy+sp);
        g2.drawLine(cx-sp, cy-70, cx+sp, cy-70);
        g2.setColor(new Color(0, 200, 240, 55));
        int[] nx = {cx-90, cx+90, cx, cx}; int[] ny = {cy, cy, cy-70, cy+70};
        for (int i = 0; i < nx.length; i++) g2.fillOval(nx[i]-4, ny[i]-4, 8, 8);
    }
    private void drawWave(Graphics2D g2, int w, int h) {
        int startY = h - 88;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int vx = w/2, vy = startY+8;
        g2.setStroke(new BasicStroke(0.7f));
        for (int i = 0; i <= 10; i++) {
            g2.setColor(new Color(0, 210, 240, 15+i*2));
            g2.drawLine(vx, vy, (int)((double)i/10*w), h);
        }
        for (int row = 0; row < 5; row++) {
            int py = startY+18+row*14;
            g2.setColor(new Color(0, 200, 230, 18+row*6)); g2.setStroke(new BasicStroke(0.9f));
            GeneralPath path = new GeneralPath();
            path.moveTo(0, py+Math.sin(0)*(3+row*1.2));
            for (int x = 1; x <= w; x++) path.lineTo(x, py+Math.sin(x*0.14+row*0.7)*(3+row*1.2));
            g2.draw(path);
        }
    }
    private void refreshProfiles() {
        runAsync(() -> {
            List<MinecraftProfile> profiles = apiClient.listProfiles();
            SwingUtilities.invokeLater(() -> {
                profilesModel.clear();
                profiles.forEach(profilesModel::addElement);
                if (!profiles.isEmpty()) {
                    profilesList.setSelectedIndex(0);
                    profilesEditorList.setSelectedIndex(0);
                } else {
                    startCreateProfileMode();
                }
                appendOutput("Perfiles cargados: " + profiles.size());
            });
        });
    }
    private void refreshHealth() {
        runAsync(() -> {
            Object status = apiClient.health().get("status");
            SwingUtilities.invokeLater(() -> {
                boolean ok = "UP".equals(status);
                healthLbl.setText(ok ? "\u25CF API OK" : "\u25CF API " + status);
                healthLbl.setForeground(ok ? C_GREEN : new Color(0xff, 0x44, 0x44));
            });
        });
    }
    private void refreshUpdates() {
        runAsync(() -> {
            LauncherUpdateStatus status = apiClient.checkUpdates();
            SwingUtilities.invokeLater(() -> {
                if ("UPDATE_AVAILABLE".equals(status.state())) {
                    updateLbl.setText("\u2191 Update: " + status.latestVersion());
                    updateLbl.setForeground(new Color(0xff, 0xcc, 0x00));
                } else {
                    updateLbl.setText("\u2713 v" + status.currentVersion());
                    updateLbl.setForeground(C_DIM);
                }
            });
        });
    }
    private void upsertProfile() {
        boolean creating = editingProfileId == null;
        String name = nameField.getText().trim();
        String generatedId = deriveProfileIdPreview(name);
        String effectiveId = creating ? generatedId : editingProfileId;
        boolean modpackMode = isModpackImportMode();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El nombre es obligatorio", "Validacion", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (creating && generatedId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El nombre del perfil no genera un ID valido", "Validacion", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String modpackPath = modpackMode ? modpackPathField.getText().trim() : "";
        if (modpackMode && modpackPath.isBlank()) {
            JOptionPane.showMessageDialog(this, "Selecciona un modpack existente para importarlo", "Validacion", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String loaderType = modpackMode ? "VANILLA" : selectedComboValue(loaderTypeField, "VANILLA");
        String loaderVersion = modpackMode ? "" : selectedComboValue(loaderVersionField, "");

        List<String> effectiveGameArgs = splitArgs(gameArgsField.getText());

        MinecraftProfile profile = new MinecraftProfile(effectiveId, name, "java", selectedComboValue(versionField, "1.20.1"),
                splitArgs(jvmArgsField.getText()), effectiveGameArgs,
                loaderType,
                loaderVersion,
                modpackPath);
        runAsync(() -> {
            MinecraftProfile saved = creating
                    ? apiClient.createProfile(profile)
                    : apiClient.updateProfile(editingProfileId, profile);
            SwingUtilities.invokeLater(() -> {
                appendOutput((creating ? "Perfil creado: " : "Perfil actualizado: ") + saved.id());
                refreshProfiles();
                contentCards.show(contentStack, "HOME");
            });
        });
    }

    private void deleteSelectedProfile() {
        MinecraftProfile selected = profilesEditorList.getSelectedValue();
        if (selected == null) {
            selected = profilesList.getSelectedValue();
        }
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecciona un perfil para eliminar.",
                    "Eliminar instancia",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Se eliminara el perfil '" + selected.displayName() + "' y su instancia completa.\nEsta accion no se puede deshacer.",
                "Confirmar eliminacion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        String profileId = selected.id();
        runAsync(() -> {
            apiClient.deleteProfile(profileId);
            SwingUtilities.invokeLater(() -> {
                appendOutput("Perfil e instancia eliminados: " + profileId);
                refreshProfiles();
                contentCards.show(contentStack, "HOME");
            });
        });
    }

    private void installDeleteContextMenu(JList<MinecraftProfile> list) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Eliminar instancia");
        deleteItem.addActionListener(e -> deleteSelectedProfile());
        menu.add(deleteItem);

        MouseAdapter popupListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                Rectangle cellBounds = list.getCellBounds(index, index);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) {
                    return;
                }
                list.setSelectedIndex(index);
                menu.show(list, e.getX(), e.getY());
            }
        };

        list.addMouseListener(popupListener);
    }

    private JPanel buildModpackPickerField() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        modpackPathField.setBackground(new Color(0x08, 0x08, 0x22));
        modpackPathField.setForeground(C_TEXT);
        modpackPathField.setCaretColor(C_TEXT);
        modpackPathField.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));

        modpackBrowseBtn = buildNeonBtn("...", C_BORDER, 36, 24);
        modpackBrowseBtn.addActionListener(e -> chooseModpackFile());
        row.add(modpackPathField, BorderLayout.CENTER);
        row.add(modpackBrowseBtn, BorderLayout.EAST);
        return row;
    }

    private void chooseModpackFile() {
        ModpackFileChooser chooser = new ModpackFileChooser(this);
        File selected = chooser.showDialog();
        if (selected != null) {
            profileModeField.setSelectedItem("IMPORTAR MODPACK");
            modpackPathField.setText(selected.getAbsolutePath());
        }
    }

    private void loadProfileIntoForm(MinecraftProfile p) {
        editingProfileId = p.id();
        idField.setText(p.id());
        nameField.setText(p.displayName());
        profileModeField.setSelectedItem(inferProfileMode(p));
        versionField.setSelectedItem(p.gameVersion());
        loaderTypeField.setSelectedItem(p.loaderType());
        loaderVersionField.setEditable(true);
        loaderVersionField.setSelectedItem(p.loaderVersion());
        jvmArgsField.setText(String.join(" ", p.jvmArgs()));
        gameArgsField.setText(String.join(" ", p.gameArgs()));
        modpackPathField.setText(p.modpackPath());
        refreshLoaderSuggestions();
        applyProfileModeUi();
        if (saveBtn != null) { saveBtn.setText("GUARDAR CAMBIOS"); saveBtn.repaint(); }
    }

    private void startCreateProfileMode() {
        editingProfileId = null;
        profilesEditorList.clearSelection();
        idField.setText("");
        nameField.setText("");
        profileModeField.setSelectedItem("INSTANCIA MANUAL");
        versionField.setSelectedItem("1.20.1");
        loaderTypeField.setSelectedItem("FORGE");
        loaderVersionField.setSelectedItem("");
        jvmArgsField.setText("-Xmx2G");
        gameArgsField.setText("");
        modpackPathField.setText("");
        refreshLoaderSuggestions();
        applyProfileModeUi();
        if (saveBtn != null) { saveBtn.setText("CREAR INSTANCIA"); saveBtn.repaint(); }
    }

    private void applyProfileModeUi() {
        boolean manualMode = !isModpackImportMode();
        versionField.setEnabled(manualMode);
        loaderTypeField.setEnabled(manualMode);
        loaderVersionField.setEnabled(manualMode);
        modpackPathField.setEnabled(!manualMode);
        modpackCompatField.setEnabled(!manualMode);
        if (modpackBrowseBtn != null) {
            modpackBrowseBtn.setEnabled(!manualMode);
            modpackBrowseBtn.repaint();
        }
        applyFieldStyle(idField, false);
        applyFieldStyle(nameField, true);
        applyFieldStyle(versionField, manualMode);
        applyFieldStyle(loaderTypeField, manualMode);
        applyFieldStyle(loaderVersionField, manualMode);
        applyFieldStyle(modpackPathField, !manualMode);
        applyFieldStyle(modpackCompatField, !manualMode);
        applyFieldStyle(jvmArgsField, true);
        applyFieldStyle(gameArgsField, true);

        if (saveBtn != null && editingProfileId == null) {
            saveBtn.setText(manualMode ? "CREAR INSTANCIA" : "IMPORTAR MODPACK");
            saveBtn.repaint();
        }
    }

    private boolean isModpackImportMode() {
        Object selected = profileModeField.getSelectedItem();
        return selected != null && "IMPORTAR MODPACK".equalsIgnoreCase(selected.toString());
    }

    private String inferProfileMode(MinecraftProfile profile) {
        return profile != null && profile.hasModpack() ? "IMPORTAR MODPACK" : "INSTANCIA MANUAL";
    }

    private void applyFieldStyle(JComponent component, boolean enabled) {
        Color bg = enabled ? new Color(0x08, 0x08, 0x22) : new Color(0x11, 0x11, 0x1b);
        Color fg = enabled ? C_TEXT : C_DIM;
        component.setBackground(bg);
        component.setForeground(fg);
        component.setEnabled(enabled);
        if (component instanceof JTextField field) {
            field.setCaretColor(enabled ? C_TEXT : C_DIM);
        }
    }

    private void refreshLoaderSuggestions() {
        String version = selectedComboValue(versionField, "1.20.1");
        String selectedLoader = selectedComboValue(loaderTypeField, "VANILLA");
        String selectedLoaderVersion = selectedComboValue(loaderVersionField, "");

        setComboItems(loaderTypeField, suggestedLoaders(version), selectedLoader);
        setComboItems(loaderVersionField,
                suggestedLoaderVersions(version, selectedComboValue(loaderTypeField, "VANILLA")),
                selectedLoaderVersion);
    }

    private List<String> suggestedLoaders(String gameVersion) {
        if (gameVersion.startsWith("1.12") || gameVersion.startsWith("1.8")) {
            return List.of("VANILLA", "FORGE");
        }
        if (gameVersion.startsWith("1.16")) {
            return List.of("VANILLA", "FORGE", "FABRIC");
        }
        if (gameVersion.startsWith("1.18") || gameVersion.startsWith("1.19")) {
            return List.of("VANILLA", "FORGE", "FABRIC", "QUILT");
        }
        return List.of("VANILLA", "FORGE", "NEOFORGE", "FABRIC", "QUILT");
    }

    private List<String> suggestedLoaderVersions(String gameVersion, String loader) {
        String norm = loader == null ? "VANILLA" : loader.toUpperCase();
        if ("FORGE".equals(norm)) {
            if (gameVersion.startsWith("1.20.1")) return List.of("47.3.0", "47.2.20", "latest");
            if (gameVersion.startsWith("1.19.2")) return List.of("43.4.0", "latest");
            if (gameVersion.startsWith("1.16.5")) return List.of("36.2.39", "latest");
            return List.of("latest", "recommended");
        }
        if ("NEOFORGE".equals(norm)) {
            if (gameVersion.startsWith("1.21")) return List.of("21.1.65", "latest");
            if (gameVersion.startsWith("1.20.6") || gameVersion.startsWith("1.20.4")) return List.of("20.6.120", "latest");
            return List.of("latest");
        }
        if ("FABRIC".equals(norm)) {
            return List.of("0.15.11", "0.15.10", "latest");
        }
        if ("QUILT".equals(norm)) {
            return List.of("0.26.4", "0.26.3", "latest");
        }
        return List.of("");
    }

    private void setComboItems(JComboBox<String> combo, List<String> suggestions, String preferred) {
        Set<String> merged = new LinkedHashSet<>();
        if (suggestions != null) merged.addAll(suggestions);
        if (preferred != null && !preferred.isBlank()) merged.add(preferred);

        combo.removeAllItems();
        for (String item : merged) {
            combo.addItem(item);
        }

        String target = (preferred == null || preferred.isBlank())
                ? (merged.isEmpty() ? "" : new ArrayList<>(merged).get(0))
                : preferred;
        combo.setSelectedItem(target);
        combo.setEditable(true);
    }

    private void loadModpackCompatibility() {
        runAsync(() -> {
            String mode = apiClient.getModpackCompatibilityMode();
            SwingUtilities.invokeLater(() -> modpackCompatField.setSelectedItem(mode));
        });
    }

    private void updateModpackCompatibilityFromUi() {
        if (suppressComboEvents) return;

        Object selected = modpackCompatField.getSelectedItem();
        if (selected == null) return;
        runAsync(() -> {
            String applied = apiClient.setModpackCompatibilityMode(selected.toString());
            SwingUtilities.invokeLater(() -> {
                suppressComboEvents = true;
                modpackCompatField.setSelectedItem(applied);
                suppressComboEvents = false;
                appendOutput("Compatibilidad de modpacks: " + applied);
            });
        });
    }

    private String selectedComboValue(JComboBox<String> combo, String fallback) {
        Object selected = combo.getSelectedItem();
        if (selected == null) return fallback;
        String raw = selected.toString().trim();
        return raw.isBlank() ? fallback : raw;
    }
    private void launchSelectedProfile() {
        if (gameRunning) return;   // ya hay un juego corriendo
        MinecraftProfile sel = profilesList.getSelectedValue();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un perfil primero",
                    "Launch", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        setLaunchEnabled(false);
        setPhaseState("PREPARING");
        updatePhaseProgress(0, "Etapa: preparando");
        updateStatus("Preparando y lanzando v" + sel.gameVersion() + "...", 0);
        appendOutput("=== Iniciando: " + sel.displayName() + " [" + sel.gameVersion() + "] ===");
        // Mostrar consola para ver el progreso en tiempo real
        contentCards.show(contentStack, "CONSOLE");

        runAsync(() -> {
            SwingUtilities.invokeLater(() -> appendOutput("[Launcher] Preparando archivos y lanzando Minecraft " + sel.gameVersion() + "..."));
            PreparedLaunchStatus operation = apiClient.startLaunchPreparedAsync(new LaunchRequest(sel.id(), false));

            String launchId;
            try {
                launchId = waitForPreparedLaunch(operation.operationId(), sel);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido", ex);
            }

            SwingUtilities.invokeLater(() -> {
                gameRunning      = true;
                currentLaunchId  = launchId;
                lastOutputIdx    = 0;
                setPhaseState("RUNNING");
                updatePhaseProgress(100, "Etapa: juego en ejecucion");
                updateStatus("Minecraft en ejecucion \u25CF", 100);
                appendOutput(">>> Minecraft lanzado! ID=" + launchId);
                appendOutput("--------------------------------------------------");
                // Repintar botones para mostrar "RUNNING"
                if (launchBtn != null) launchBtn.repaint();
                // Iniciar polling de output
                startOutputPolling(launchId);
            });
        });
    }

    private String waitForPreparedLaunch(String operationId, MinecraftProfile profile) throws InterruptedException {
        int lastProgress = -1;
        String lastMessage = "";

        while (true) {
            PreparedLaunchStatus status = apiClient.getLaunchPreparedStatus(operationId);
            boolean progressChanged = status.progress() != lastProgress;
            boolean messageChanged = status.message() != null && !status.message().equals(lastMessage);

            if (progressChanged || messageChanged) {
                lastProgress = status.progress();
                lastMessage = status.message() == null ? "" : status.message();
                String text = lastMessage.isBlank()
                        ? ("Preparando y lanzando " + profile.gameVersion() + "...")
                        : lastMessage;
                int progress = Math.max(0, Math.min(status.progress(), 100));
                int phaseProgress = mapPhaseProgress(status.state(), progress);
                SwingUtilities.invokeLater(() -> {
                    setPhaseState(status.state());
                    updatePhaseProgress(phaseProgress, "Etapa: " + normalizePhase(status.state()) + " " + phaseProgress + "%");
                    updateStatus(text, progress);
                    if (messageChanged && !text.isBlank()) {
                        appendOutput("[Launcher] " + text);
                    }
                });
            }

            if ("DONE".equals(status.state())) {
                if (status.launchId() == null || status.launchId().isBlank()) {
                    throw new IllegalStateException("La operacion termino sin launchId");
                }
                return status.launchId();
            }

            if ("FAILED".equals(status.state())) {
                throw new IllegalStateException(status.message() == null ? "Fallo la preparacion" : status.message());
            }

            Thread.sleep(300);
        }
    }

    @SuppressWarnings("unchecked")
    private void startOutputPolling(String launchId) {
        stopOutputPolling();
        outputPoll = uiSched.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> resp = apiClient.getGameOutput(launchId, lastOutputIdx);
                List<String> lines = (List<String>) resp.get("lines");
                int total  = ((Number) resp.get("total")).intValue();
                boolean alive = Boolean.TRUE.equals(resp.get("alive"));

                if (lines != null && !lines.isEmpty()) {
                    lastOutputIdx = total;
                    SwingUtilities.invokeLater(() -> {
                        appendOutputBatch(lines);
                        // Auto-scroll al final
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                    });
                }

                // Detectar fin del proceso
                if (!alive && total >= 0) {
                    stopOutputPolling();
                    SwingUtilities.invokeLater(() -> {
                        gameRunning     = false;
                        currentLaunchId = null;
                            setPhaseState("IDLE");
                            updatePhaseProgress(0, "Etapa: inactiva");
                        appendOutput("=== Minecraft ha terminado ===");
                        updateStatus("Listo", 0);
                        setLaunchEnabled(true);
                        if (launchBtn != null) launchBtn.repaint();
                    });
                }
            } catch (Exception ignored) {}
        }, 600, 600, TimeUnit.MILLISECONDS);
    }

    private void stopOutputPolling() {
        if (outputPoll != null) {
            outputPoll.cancel(false);
            outputPoll = null;
        }
    }
    private void setLaunchEnabled(boolean enabled) {
        if (launchBtn != null) launchBtn.setEnabled(enabled);
    }
    private void updateStatus(String text, int prog) {
        statusLbl.setText(text);
        progressBar.setValue(Math.max(0, Math.min(prog, 100)));
    }

    private void updatePhaseProgress(int progress, String text) {
        phaseProgressBar.setValue(Math.max(0, Math.min(progress, 100)));
        phaseProgressBar.setString(text == null || text.isBlank() ? ("Etapa: " + phaseProgressBar.getValue() + "%") : text);
    }

    private void setPhaseState(String state) {
        String norm = normalizePhase(state);
        phaseLbl.setText(norm);
        switch (norm) {
            case "PREPARING" -> {
                phaseLbl.setForeground(C_CYAN);
                phaseLbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x00, 0x77, 0x99), 1),
                        BorderFactory.createEmptyBorder(1, 6, 1, 6)
                ));
            }
            case "STARTING" -> {
                phaseLbl.setForeground(C_MAGENTA);
                phaseLbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x88, 0x33, 0xaa), 1),
                        BorderFactory.createEmptyBorder(1, 6, 1, 6)
                ));
            }
            case "RUNNING", "DONE" -> {
                phaseLbl.setForeground(C_GREEN);
                phaseLbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x00, 0x66, 0x33), 1),
                        BorderFactory.createEmptyBorder(1, 6, 1, 6)
                ));
            }
            case "FAILED" -> {
                phaseLbl.setForeground(new Color(0xff, 0x66, 0x66));
                phaseLbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0xaa, 0x22, 0x22), 1),
                        BorderFactory.createEmptyBorder(1, 6, 1, 6)
                ));
            }
            default -> {
                phaseLbl.setForeground(C_DIM);
                phaseLbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x33, 0x33, 0x66), 1),
                        BorderFactory.createEmptyBorder(1, 6, 1, 6)
                ));
            }
        }
    }

    private String normalizePhase(String phase) {
        if (phase == null || phase.isBlank()) return "IDLE";
        return phase.trim().toUpperCase();
    }

    private int mapPhaseProgress(String phase, int totalProgress) {
        String norm = normalizePhase(phase);
        return switch (norm) {
            case "PREPARING" -> Math.max(1, Math.min(100, totalProgress * 100 / 90));
            case "STARTING" -> Math.max(10, Math.min(100, (totalProgress - 90) * 10));
            case "RUNNING", "DONE" -> 100;
            case "FAILED" -> 0;
            default -> Math.max(0, Math.min(100, totalProgress));
        };
    }
    private void runAsync(Runnable r) {
        if (executor.isShutdown() || executor.isTerminated()) {
            System.err.println("[UI] Executor está terminado, saltando tarea async");
            return;
        }
        executor.submit(() -> {
            try { r.run(); }
            catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setPhaseState("FAILED");
                    updatePhaseProgress(0, "Etapa: error");
                    updateStatus("Error: " + ex.getMessage(), 0);
                    appendOutput("ERROR: " + ex.getMessage());
                    setLaunchEnabled(true);
                });
            }
        });
    }

    private void appendOutput(String msg) {
        if (SwingUtilities.isEventDispatchThread()) {
            outputArea.append(msg + System.lineSeparator());
            trimConsoleIfNeeded();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            outputArea.append(msg + System.lineSeparator());
            trimConsoleIfNeeded();
        });
    }

    private void appendOutputBatch(List<String> lines) {
        if (lines == null || lines.isEmpty()) return;
        StringBuilder sb = new StringBuilder(lines.size() * 40);
        for (String line : lines) {
            sb.append(line).append(System.lineSeparator());
        }
        outputArea.append(sb.toString());
        trimConsoleIfNeeded();
    }

    private void trimConsoleIfNeeded() {
        int lineCount = outputArea.getLineCount();
        if (lineCount <= MAX_CONSOLE_LINES) return;
        try {
            int cutLine = Math.max(1, lineCount - TRIM_TO_LINES);
            int endOffset = outputArea.getLineEndOffset(cutLine - 1);
            outputArea.replaceRange("", 0, endOffset);
        } catch (Exception ignored) {
            // Si falla el recorte por offsets, no interrumpimos la UI.
        }
    }

    private List<String> splitArgs(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.trim().split("\\s+")).filter(t -> !t.isBlank()).toList();
    }

    private List<String> ensureUsernameArg(List<String> args, String username) {
        List<String> cleaned = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if ("--username".equals(token)) {
                i = Math.min(i + 1, args.size());
                continue;
            }
            cleaned.add(token);
        }
        List<String> effective = new ArrayList<>(cleaned.size() + 2);
        effective.add("--username");
        effective.add(username);
        effective.addAll(cleaned);
        return List.copyOf(effective);
    }

    private String extractUsernameFromArgs(List<String> args, String displayName) {
        for (int i = 0; i < args.size() - 1; i++) {
            if ("--username".equals(args.get(i))) {
                String found = args.get(i + 1);
                if (isValidMinecraftUsername(found)) {
                    return found;
                }
            }
        }
        String fallback = displayName == null ? "Steve" : displayName.replaceAll("[^A-Za-z0-9_]", "_");
        if (fallback.length() < 3) fallback = "Steve";
        if (fallback.length() > 16) fallback = fallback.substring(0, 16);
        return fallback;
    }

    private boolean isValidMinecraftUsername(String username) {
        return username != null && username.matches("^[A-Za-z0-9_]{3,16}$");
    }

    private String deriveProfileIdPreview(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        return displayName.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase();
    }

    private void syncIdPreviewFromName() {
        if (editingProfileId != null) {
            return;
        }
        idField.setText(deriveProfileIdPreview(nameField.getText()));
    }

    private void refreshMicrosoftSession() {
        runAsync(() -> {
            var status = apiClient.getMicrosoftSessionStatus();
            Map<String, Object> settings = apiClient.getGlobalUserSettings();

            if (status.connected()) {
                boolean preferPremium = !Boolean.FALSE.equals(settings.get("preferPremium"));
                String currentUsername = settings.get("username") == null ? "Steve" : settings.get("username").toString();
                if (preferPremium && !status.playerName().equalsIgnoreCase(currentUsername)) {
                    settings = apiClient.setGlobalUserSettings(status.playerName(), true);
                }
            }

            Map<String, Object> effectiveSettings = settings;
            SwingUtilities.invokeLater(() -> {
                if (status.connected()) {
                    microsoftStatusLbl.setText("Premium: " + status.playerName());
                    microsoftStatusLbl.setForeground(C_GREEN);
                } else {
                    microsoftStatusLbl.setText("Premium: offline");
                    microsoftStatusLbl.setForeground(C_DIM);
                }

                Object username = effectiveSettings.get("username");
                if (username != null && isValidMinecraftUsername(username.toString())) {
                    usernameField.setText(username.toString());
                }
            });
        });
    }

    private void loadGlobalUserSettings(JCheckBox preferPremiumCheck) {
        runAsync(() -> {
            Map<String, Object> settings = apiClient.getGlobalUserSettings();
            SwingUtilities.invokeLater(() -> {
                Object username = settings.get("username");
                Object preferPremium = settings.get("preferPremium");
                Object oauthClientId = settings.get("oauthClientId");
                Object oauthTenant = settings.get("oauthTenant");
                usernameField.setText(username == null ? "Steve" : username.toString());
                preferPremiumCheck.setSelected(!Boolean.FALSE.equals(preferPremium));
                oauthClientIdField.setText(oauthClientId == null ? "" : oauthClientId.toString());
                oauthTenantField.setText(oauthTenant == null ? "consumers" : oauthTenant.toString());
            });
        });
    }

    private void saveGlobalUserSettings(boolean preferPremium) {
        String username = usernameField.getText().trim();
        if (!isValidMinecraftUsername(username)) {
            JOptionPane.showMessageDialog(this,
                    "Nickname global invalido. Usa 3-16 caracteres: letras, numeros o _",
                    "Configuracion",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAsync(() -> {
            apiClient.setGlobalUserSettings(
                    username,
                    preferPremium,
                    oauthClientIdField.getText().trim(),
                    oauthTenantField.getText().trim()
            );
            SwingUtilities.invokeLater(() -> appendOutput("[Config] Nickname global guardado: " + username));
        });
    }

    private void startMicrosoftLogin() {
        runAsync(() -> {
            int maxAttempts = 2;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                var login = apiClient.startMicrosoftBrowserLogin();
                openMicrosoftVerificationUrl(login.authorizationUrl());

                int currentAttempt = attempt;
                SwingUtilities.invokeLater(() -> appendOutput("[Premium] Esperando inicio de sesion en el navegador... (intento "
                        + currentAttempt + "/" + maxAttempts + ")"));

                try {
                    var session = apiClient.completeMicrosoftBrowserLogin(login.operationId());
                    SwingUtilities.invokeLater(() -> {
                        appendOutput("[Premium] Login completado: " + session.playerName());
                        refreshMicrosoftSession();
                    });
                    return;
                } catch (Exception ex) {
                    String message = ex.getMessage() == null ? "" : ex.getMessage();
                    if (message.contains("MS_RETRY_AUTOMATIC") && attempt < maxAttempts) {
                        SwingUtilities.invokeLater(() -> appendOutput(
                                "[Premium] Reintentando login automaticamente con cliente alternativo..."));
                        continue;
                    }

                    // Fallback 100% automatico: si Microsoft bloquea consentimiento, seguir en modo offline.
                    if (isMicrosoftConsentBlocked(message)) {
                        applyAutomaticOfflineFallback();
                        return;
                    }
                    throw ex;
                }
            }
        });
    }

    private boolean isMicrosoftConsentBlocked(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase();
        return m.contains("first party")
                || m.contains("users are not permitted to consent")
                || m.contains("pre-authorization")
                || m.contains("consentimiento")
                || m.contains("aadsts65001");
    }

    private void applyAutomaticOfflineFallback() {
        runAsync(() -> {
            try {
                String username = usernameField.getText().trim();
                if (!isValidMinecraftUsername(username)) {
                    username = "Steve";
                }
                String oauthClientId = oauthClientIdField.getText().trim();
                String oauthTenant = oauthTenantField.getText().trim();
                apiClient.setGlobalUserSettings(username, false, oauthClientId, oauthTenant);
                SwingUtilities.invokeLater(() -> {
                    appendOutput("[Premium] Microsoft bloquea esta cuenta para consentimiento. ");
                    appendOutput("[Premium] Se activo automaticamente modo offline para que puedas seguir usando el launcher.");
                    microsoftStatusLbl.setText("Premium: offline (auto)");
                    microsoftStatusLbl.setForeground(C_DIM);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendOutput("[Premium] No se pudo activar fallback offline automatico: " + ex.getMessage()));
            }
        });
    }

    private void openMicrosoftVerificationUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                appendOutput("[Premium] No se pudo abrir navegador automaticamente. URL: " + url);
            }
        } catch (Exception ex) {
            appendOutput("[Premium] No se pudo abrir el navegador automaticamente: " + ex.getMessage());
            appendOutput("[Premium] Abre manualmente esta URL: " + url);
        }
    }

    private void logoutMicrosoft() {
        runAsync(() -> {
            apiClient.logoutMicrosoftSession();
            SwingUtilities.invokeLater(() -> {
                appendOutput("[Premium] Sesion Microsoft cerrada.");
                refreshMicrosoftSession();
            });
        });
    }

    private static JPanel fixedHeight(JPanel p, int h) {
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        p.setMinimumSize(new Dimension(0, h));
        return p;
    }
    private JPanel hLine() {
        JPanel line = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(C_BORDER); g.fillRect(0, 0, getWidth(), 1);
            }
        };
        line.setOpaque(false);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setPreferredSize(new Dimension(0, 1));
        return line;
    }
    private void addFormField(JPanel panel, String labelText, JTextField field) {
        JLabel lbl = new JLabel(labelText); lbl.setFont(new Font("Dialog", Font.PLAIN, 11)); lbl.setForeground(C_TEXT);
        field.setBackground(new Color(0x08, 0x08, 0x22)); field.setForeground(C_TEXT); field.setCaretColor(C_TEXT);
        field.setBorder(BorderFactory.createLineBorder(C_BORDER, 1)); field.setFont(new Font("Dialog", Font.PLAIN, 11));
        panel.add(lbl); panel.add(field);
    }

    private void addFormField(JPanel panel, String labelText, JComboBox<String> combo) {
        JLabel lbl = new JLabel(labelText); lbl.setFont(new Font("Dialog", Font.PLAIN, 11)); lbl.setForeground(C_TEXT);
        combo.setBackground(new Color(0x08, 0x08, 0x22));
        combo.setForeground(C_TEXT);
        combo.setFont(new Font("Dialog", Font.PLAIN, 11));
        combo.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
        panel.add(lbl); panel.add(combo);
    }

    private void addFormField(JPanel panel, String labelText, JComponent component) {
        JLabel lbl = new JLabel(labelText); lbl.setFont(new Font("Dialog", Font.PLAIN, 11)); lbl.setForeground(C_TEXT);
        panel.add(lbl); panel.add(component);
    }
    private static final class ProfileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MinecraftProfile p) {
                String icon = profileIcon(p);
                String mode = p.hasModpack() ? "MODPACK IMPORTADO" : "INSTANCIA " + ((p.loaderType() == null || p.loaderType().isBlank()) ? "VANILLA" : p.loaderType());
                setText("<html><div style='font-size: 11pt'>" + icon + " <b style='color: #00e5ff; font-size: 12pt'>" + p.displayName() 
                        + "</b><br><span style='color: #9090bb; font-size: 10pt'>&nbsp;&nbsp;" + p.gameVersion() + " • " + mode + "</span></div></html>");
            }
            setBackground(isSelected ? new Color(0x18, 0x18, 0x44) : new Color(0x08, 0x08, 0x1e));
            setForeground(isSelected ? C_CYAN : C_TEXT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isSelected ? C_MAGENTA : new Color(0x00, 0x99, 0xaa), 2),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)
            ));
            setOpaque(true);
            return this;
        }
    }

    private static final class ProfileEditorRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MinecraftProfile p) {
                String icon = profileIcon(p);
                String mode = p.hasModpack() ? "MODPACK IMPORTADO" : "INSTANCIA " + ((p.loaderType() == null || p.loaderType().isBlank()) ? "VANILLA" : p.loaderType());
                setText("<html><div style='font-size: 11pt'>" + icon + " <b style='color: #00e5ff; font-size: 12pt'>" + p.displayName() 
                        + "</b><br><span style='color: #9090bb; font-size: 10pt'>&nbsp;&nbsp;" + p.gameVersion() + " • " + mode + "</span></div></html>");
            }
            setBackground(isSelected ? new Color(0x18, 0x18, 0x44) : new Color(0x08, 0x08, 0x1e));
            setForeground(isSelected ? C_CYAN : C_TEXT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isSelected ? C_MAGENTA : new Color(0x00, 0x99, 0xaa), 2),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
            setOpaque(true);
            return this;
        }
    }

    private static String profileIcon(MinecraftProfile p) {
        if (p.modpackPath() != null && !p.modpackPath().isBlank()) return "\u25c9";
        String loader = p.loaderType() == null ? "" : p.loaderType().toUpperCase();
        return switch (loader) {
            case "FORGE" -> "\ud83d\udde1";
            case "NEOFORGE" -> "\u26a1";
            case "FABRIC" -> "\ud83e\uddf8";
            case "QUILT" -> "\ud83e\udd7a";
            default -> "\ud83d\udcc4";
        };
    }
    
    /**
     * Programa la carga de skin con debounce (espera a que el usuario termine de escribir)
     */
    private void scheduleLoadSkin() {
        if (skinLoadingTask != null) {
            skinLoadingTask.cancel(false);
        }
        skinLoadingTask = uiSched.schedule(this::loadSkinForCurrentUsername, 800, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Carga la skin del username actual si es válido y es un jugador premium
     */
    private void loadSkinForCurrentUsername() {
        String username = usernameField.getText().trim();
        if (!isValidMinecraftUsername(username)) {
            return;
        }
        
        runAsync(() -> {
            // Intentar obtener la skin del jugador
            java.nio.file.Path cacheDir = java.nio.file.Paths.get(
                    System.getProperty("user.home"), 
                    ".mialustudio", "launcher", "skins"
            );
            
            var skinPath = mojangSkinProvider.getSkinByUsername(username, cacheDir);
            if (skinPath.isPresent()) {
                SwingUtilities.invokeLater(() -> {
                    appendOutput("[Skin] Skin cargada para: " + username);
                });
            }
        });
    }
}

