package am.froshy.launcher.ui;
import am.froshy.launcher.api.internal.InternalApiClient;
import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.LauncherUpdateStatus;
import am.froshy.launcher.domain.MinecraftProfile;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
/** Launcher UI - diseño cyberpunk/neon inspirado en imagen de referencia. */
public final class LauncherFrame extends JFrame {
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
    private final ExecutorService          executor  = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService uiSched   = Executors.newSingleThreadScheduledExecutor();
    private final DefaultListModel<MinecraftProfile> profilesModel = new DefaultListModel<>();
    private final JList<MinecraftProfile> profilesList = new JList<>(profilesModel);
    private final JTextField idField       = new JTextField();
    private final JTextField nameField     = new JTextField();
    private final JTextField versionField  = new JTextField("1.20.1");
    private final JTextField jvmArgsField  = new JTextField("-Xmx2G");
    private final JTextField gameArgsField = new JTextField("--username Steve");
    private final JTextArea  outputArea    = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLbl = new JLabel("Listo");
    private final JLabel healthLbl = new JLabel("\u25CF API");
    private final JLabel updateLbl = new JLabel("");
    // Estado del juego en ejecución
    private volatile boolean         gameRunning     = false;
    private volatile String          currentLaunchId = null;
    private volatile int             lastOutputIdx   = 0;
    private          ScheduledFuture<?> outputPoll  = null;
    private JButton    launchBtn;
    private JButton    playBtn;
    private JPanel     contentStack;
    private CardLayout contentCards;
    private Point      dragOrigin;
    public LauncherFrame(InternalApiClient apiClient, int apiPort, Runnable onClose) {
        super();
        this.apiClient = apiClient;
        this.onClose   = onClose;
        buildUi();
        refreshProfiles();
        refreshHealth();
        refreshUpdates();
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
        JLabel logoRow = new JLabel("\u2B21  FroshyCorp  |  INNOVATIVE MINECRAFT ECOSYSTEM", JLabel.CENTER);
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
        launchBtn = buildNeonBtn("LAUNCH MINECRAFT", C_MAGENTA, 224, 38);
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
        JLabel email = new JLabel("support@froshycorp.io");
        email.setFont(new Font("Dialog", Font.PLAIN, 9)); email.setForeground(C_DIM); social.add(email);
        JLabel supportLink = new JLabel("Support & Feedback");
        supportLink.setFont(new Font("Dialog", Font.PLAIN, 9)); supportLink.setForeground(C_BORDER);
        supportLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        supportLink.setBorder(BorderFactory.createEmptyBorder(0,0,0,2));
        JPanel statusRow = new JPanel(new BorderLayout()); statusRow.setOpaque(false);
        JLabel verLbl = new JLabel("  Froshy Launcher v1.0.0");
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
        healthLbl.setFont(new Font("Dialog", Font.PLAIN, 10)); healthLbl.setForeground(C_GREEN);
        updateLbl.setFont(new Font("Dialog", Font.PLAIN, 10)); updateLbl.setForeground(C_DIM);
        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); infoRow.setOpaque(false);
        infoRow.add(healthLbl); infoRow.add(updateLbl);
        JPanel bottomGroup = new JPanel(new BorderLayout(4, 2)); bottomGroup.setOpaque(false);
        bottomGroup.add(progressBar, BorderLayout.CENTER); bottomGroup.add(infoRow, BorderLayout.SOUTH);
        panel.add(selTitle,    BorderLayout.NORTH);
        panel.add(scroll,      BorderLayout.CENTER);
        panel.add(bottomGroup, BorderLayout.SOUTH);
        return panel;
    }
    private JPanel buildProfilesCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8)); panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        JLabel title = new JLabel("GESTION DE PERFILES");
        title.setFont(new Font("Dialog", Font.BOLD, 13)); title.setForeground(C_CYAN);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6)); form.setOpaque(false);
        addFormField(form, "ID:",        idField);
        addFormField(form, "Nombre:",    nameField);
        addFormField(form, "Version:",   versionField);
        addFormField(form, "JVM Args:",  jvmArgsField);
        addFormField(form, "Game Args:", gameArgsField);
        JButton createBtn  = buildNeonBtn("Crear Perfil",  C_CYAN,   120, 28);
        JButton refreshBtn = buildNeonBtn("Refrescar",     C_BORDER, 100, 28);
        createBtn.addActionListener(e  -> createProfile());
        refreshBtn.addActionListener(e -> refreshProfiles());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); btnRow.setOpaque(false);
        btnRow.add(refreshBtn); btnRow.add(createBtn);
        panel.add(title,  BorderLayout.NORTH);
        panel.add(form,   BorderLayout.CENTER);
        panel.add(btnRow, BorderLayout.SOUTH);
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
        panel.add(makeIconBtn("\u2699", "SETTINGS", e -> {}));
        panel.add(Box.createVerticalStrut(10));
        panel.add(makeIconBtn("\u25CE", "PROFILE",  e -> contentCards.show(contentStack, "PROFILES")));
        panel.add(Box.createVerticalGlue());
        playBtn = buildNeonBtn("PLAY", C_MAGENTA, 88, 44);
        playBtn.setFont(new Font("Dialog", Font.BOLD, 18));
        playBtn.addActionListener(e -> launchSelectedProfile());
        JPanel pw = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        pw.setOpaque(false); pw.setMaximumSize(new Dimension(110, 56)); pw.add(playBtn);
        panel.add(pw);
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
        // Para PLAY y LAUNCH: refleja estado gameRunning
        boolean isPlayBtn = "PLAY".equals(text) || "LAUNCH MINECRAFT".equals(text);
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
                g2.setColor(base); g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.setColor(new Color(r,gn,b,50)); g2.setStroke(new BasicStroke(5f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
                g2.setColor(eff); g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1,1,getWidth()-3,getHeight()-3,7,7);
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
                if (!profiles.isEmpty()) profilesList.setSelectedIndex(0);
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
    private void createProfile() {
        String id = idField.getText().trim(), name = nameField.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "ID y nombre son obligatorios", "Validacion", JOptionPane.WARNING_MESSAGE);
            return;
        }
        MinecraftProfile profile = new MinecraftProfile(id, name, "java", versionField.getText().trim(),
                splitArgs(jvmArgsField.getText()), splitArgs(gameArgsField.getText()));
        runAsync(() -> {
            MinecraftProfile created = apiClient.createProfile(profile);
            SwingUtilities.invokeLater(() -> {
                appendOutput("Perfil creado: " + created.id());
                refreshProfiles();
                contentCards.show(contentStack, "HOME");
            });
        });
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
        updateStatus("Preparando v" + sel.gameVersion() + "…", 0);
        appendOutput("=== Iniciando: " + sel.displayName() + " [" + sel.gameVersion() + "] ===");
        // Cambiar a consola para ver el progreso de descarga
        contentCards.show(contentStack, "CONSOLE");

        runAsync(() -> {
            // Fase 1: preparar/descargar versión
            SwingUtilities.invokeLater(() ->
                    appendOutput(">>> Preparando versión " + sel.gameVersion() + "…"));
            DownloadStatus ds = apiClient.prepareVersion(sel.gameVersion());
            try { waitForPrep(ds.downloadId()); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido durante descarga", ex);
            }

            // Fase 2: lanzar el juego
            SwingUtilities.invokeLater(() ->
                    appendOutput(">>> Versión lista. Lanzando Minecraft…"));
            LaunchResult result = apiClient.launch(new LaunchRequest(sel.id(), false));

            SwingUtilities.invokeLater(() -> {
                gameRunning      = true;
                currentLaunchId  = result.launchId();
                lastOutputIdx    = 0;
                updateStatus("Minecraft en ejecución ●", 100);
                appendOutput(">>> ¡Minecraft lanzado! [ID=" + result.launchId() + "]");
                // Repintar botones para mostrar "RUNNING"
                if (launchBtn != null) launchBtn.repaint();
                if (playBtn   != null) playBtn.repaint();
                // Iniciar polling de output del proceso
                startOutputPolling(result.launchId());
            });
        });
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
                        for (String line : lines) appendOutput(line);
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
                        appendOutput("=== Minecraft ha terminado ===");
                        updateStatus("Listo", 0);
                        setLaunchEnabled(true);
                        if (launchBtn != null) launchBtn.repaint();
                        if (playBtn   != null) playBtn.repaint();
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
    private void waitForPrep(String downloadId) throws InterruptedException {
        int last = -1;
        String lastMsg = "";
        while (true) {
            DownloadStatus s = apiClient.getDownloadStatus(downloadId);
            String msg = (s.message() != null && !s.message().isBlank()) ? s.message() : "";
            if (s.progress() != last || !msg.equals(lastMsg)) {
                last    = s.progress();
                lastMsg = msg;
                String txt = msg.isEmpty()
                        ? s.target() + " → " + s.state() + " (" + s.progress() + "%)"
                        : msg + " (" + s.progress() + "%)";
                int prog = s.progress();
                SwingUtilities.invokeLater(() -> updateStatus(txt, prog));
            }
            if ("DONE".equals(s.state()))   return;
            if ("FAILED".equals(s.state())) {
                String reason = msg.isEmpty() ? s.target() : msg;
                throw new IllegalStateException("Descarga fallida: " + reason);
            }
            Thread.sleep(350);
        }
    }
    private void setLaunchEnabled(boolean enabled) {
        if (launchBtn != null) launchBtn.setEnabled(enabled);
        if (playBtn   != null) playBtn.setEnabled(enabled);
    }
    private void updateStatus(String text, int prog) {
        statusLbl.setText(text);
        progressBar.setValue(Math.max(0, Math.min(prog, 100)));
    }
    private void runAsync(Runnable r) {
        executor.submit(() -> {
            try { r.run(); }
            catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Error: " + ex.getMessage(), 0);
                    appendOutput("ERROR: " + ex.getMessage());
                    setLaunchEnabled(true);
                });
            }
        });
    }
    private void appendOutput(String msg) {
        SwingUtilities.invokeLater(() -> outputArea.append(msg + System.lineSeparator()));
    }
    private List<String> splitArgs(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.trim().split("\\s+")).filter(t -> !t.isBlank()).toList();
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
    private static final class ProfileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MinecraftProfile p) setText("  \u25BA " + p.displayName() + "   [" + p.gameVersion() + "]");
            setBackground(isSelected ? new Color(0x18, 0x18, 0x44) : new Color(0x08, 0x08, 0x1e));
            setForeground(isSelected ? C_CYAN : C_TEXT);
            setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            return this;
        }
    }
}