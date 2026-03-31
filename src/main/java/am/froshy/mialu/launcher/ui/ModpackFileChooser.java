package am.froshy.mialu.launcher.ui;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.MouseInputAdapter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class ModpackFileChooser extends JDialog {
    private static final Color C_BG      = new Color(0x08, 0x08, 0x1a);
    private static final Color C_CYAN    = new Color(0x00, 0xe5, 0xff);
    private static final Color C_MAGENTA = new Color(0xe0, 0x40, 0xfb);
    private static final Color C_BORDER  = new Color(0x00, 0xb8, 0xcc);
    private static final Color C_TEXT    = new Color(0xe0, 0xe0, 0xff);

    private Path currentPath;
    private final JTable fileTable;
    private final FileTableModel fileModel;
    private final JTextField pathField;
    private final JTextField nameField;
    private final JComboBox<String> filterCombo;
    private File selectedFile = null;
    private boolean approved = false;

    public ModpackFileChooser(JFrame parent) {
        super(parent, "Selecciona modpack (.mrpack o .zip)", true);
        setSize(1000, 650);
        setLocationRelativeTo(parent);
        setUndecorated(true);

        currentPath = new File(System.getProperty("user.home"), "Documents").toPath();
        fileModel = new FileTableModel();
        fileTable = buildFileTable();
        pathField = new JTextField();
        nameField = new JTextField();
        filterCombo = new JComboBox<>(new String[]{"Modpacks", "Todos"});

        buildUI();
        loadDirectory(currentPath);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(C_BG);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setOpaque(false);
        root.setBorder(new LineBorder(C_BORDER, 2));

        root.add(buildTitleBar(), BorderLayout.NORTH);
        root.add(buildSearchBar(), BorderLayout.BEFORE_FIRST_LINE);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.PAGE_END);
        root.add(buildButtonPanel(), BorderLayout.SOUTH);

        setContentPane(root);
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

        JLabel title = new JLabel("📦 Selecciona modpack (.mrpack o .zip)", JLabel.LEFT);
        title.setFont(new Font("Dialog", Font.BOLD, 12));
        title.setForeground(C_CYAN);
        title.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        btnRow.setOpaque(false);
        JButton closeBtn = makeIconBtn("✕", e -> dispose());
        btnRow.add(closeBtn);

        bar.add(title, BorderLayout.WEST);
        bar.add(btnRow, BorderLayout.EAST);

        MouseInputAdapter drag = new MouseInputAdapter() {
            private Point origin;
            @Override public void mousePressed(MouseEvent e) { origin = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (origin != null) {
                    Point loc = getLocation();
                    setLocation(loc.x + e.getX() - origin.x, loc.y + e.getY() - origin.y);
                }
            }
        };
        title.addMouseListener(drag);
        title.addMouseMotionListener(drag);

        return bar;
    }

    private JPanel buildSearchBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

        JLabel label = new JLabel("Buscar en:");
        label.setFont(new Font("Dialog", Font.PLAIN, 11));
        label.setForeground(C_CYAN);

        pathField.setText(currentPath.toString());
        pathField.setBackground(new Color(0x08, 0x08, 0x22));
        pathField.setForeground(C_CYAN);
        pathField.setCaretColor(C_CYAN);
        pathField.setBorder(new LineBorder(C_CYAN, 2));
        pathField.setFont(new Font("Dialog", Font.PLAIN, 11));
        pathField.addActionListener(e -> navigateToTypedPath());

        JButton browseBtn = makeNavBtn("📁", e -> navigateToTypedPath());
        browseBtn.setToolTipText("Ir a la ruta escrita");
        JButton upBtn = makeNavBtn("⬆", e -> navigateUp());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btns.setOpaque(false);
        btns.add(browseBtn);
        btns.add(upBtn);

        panel.add(label, BorderLayout.WEST);
        panel.add(pathField, BorderLayout.CENTER);
        panel.add(btns, BorderLayout.EAST);

        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));

        JScrollPane scroll = new JScrollPane(fileTable);
        scroll.setBorder(new LineBorder(C_BORDER, 1));
        scroll.getViewport().setBackground(new Color(0x08, 0x08, 0x1e));
        scroll.setBackground(new Color(0x08, 0x08, 0x1e));

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

        JLabel nameLabel = new JLabel("Nombre de archivo:");
        nameLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        nameLabel.setForeground(C_CYAN);

        nameField.setBackground(new Color(0x08, 0x08, 0x22));
        nameField.setForeground(C_CYAN);
        nameField.setCaretColor(C_CYAN);
        nameField.setBorder(new LineBorder(C_BORDER, 1));
        nameField.setFont(new Font("Dialog", Font.PLAIN, 11));

        JPanel nameRow = new JPanel(new BorderLayout());
        nameRow.setOpaque(false);
        nameRow.add(nameLabel, BorderLayout.WEST);
        nameRow.add(nameField, BorderLayout.CENTER);

        JLabel filterLabel = new JLabel("Archivos de tipo:");
        filterLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        filterLabel.setForeground(C_CYAN);

        filterCombo.setBackground(new Color(0x08, 0x08, 0x22));
        filterCombo.setForeground(C_TEXT);
        filterCombo.setBorder(new LineBorder(C_BORDER, 1));
        filterCombo.setFont(new Font("Dialog", Font.PLAIN, 11));
        filterCombo.addActionListener(e -> loadDirectory(currentPath));

        JPanel filterRow = new JPanel(new BorderLayout());
        filterRow.setOpaque(false);
        filterRow.add(filterLabel, BorderLayout.WEST);
        filterRow.add(filterCombo, BorderLayout.CENTER);

        panel.add(nameRow);
        panel.add(filterRow);

        return panel;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 14, 12, 14));

        JButton aceptarBtn = buildNeonBtn("Aceptar", C_CYAN, 120, 32);
        JButton cancelBtn = buildNeonBtn("Cancelar", C_MAGENTA, 120, 32);

        aceptarBtn.addActionListener(e -> acceptSelection());
        cancelBtn.addActionListener(e -> { approved = false; dispose(); });
        getRootPane().setDefaultButton(aceptarBtn);

        panel.add(aceptarBtn);
        panel.add(cancelBtn);

        return panel;
    }

    private JTable buildFileTable() {
        JTable table = new JTable(fileModel) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table.setBackground(new Color(0x08, 0x08, 0x1e));
        table.setForeground(C_TEXT);
        table.setSelectionBackground(new Color(0x18, 0x18, 0x40));
        table.setSelectionForeground(C_CYAN);
        table.setGridColor(new Color(0x1a, 0x1a, 0x33));
        table.setFont(new Font("Dialog", Font.PLAIN, 11));
        table.setRowHeight(24);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean focus,
                    int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                c.setForeground(sel ? C_CYAN : C_TEXT);
                c.setBackground(sel ? new Color(0x18, 0x18, 0x40) : new Color(0x08, 0x08, 0x1e));
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    File f = fileModel.getFileAt(row);
                    if (isDirectory(row)) {
                        loadDirectory(f.toPath());
                        pathField.setText(f.getAbsolutePath());
                        nameField.setText("");
                    } else if (isSupportedModpackFile(f)) {
                        nameField.setText(f.getName());
                        selectedFile = f;
                        if (e.getClickCount() >= 2) {
                            acceptSelection();
                        }
                    }
                }
            }
        });

        return table;
    }

    private void loadDirectory(Path path) {
        currentPath = path;
        File dir = path.toFile();
        List<File> files = new ArrayList<>();

        if (dir.isDirectory() && dir.listFiles() != null) {
            File[] list = dir.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.isDirectory() || matchesVisibleFilter(f)) {
                        files.add(f);
                    }
                }
            }
        }

        Collections.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareTo(b.getName());
        });

        fileModel.setFiles(files);
        pathField.setText(dir.getAbsolutePath());
        selectedFile = null;
        nameField.setText("");
    }

    private boolean matchesVisibleFilter(File f) {
        return isAllFilesFilter() || isSupportedModpackFile(f);
    }

    private boolean isSupportedModpackFile(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".mrpack") || name.endsWith(".zip");
    }

    private boolean isAllFilesFilter() {
        return "Todos".equals(filterCombo.getSelectedItem());
    }

    private boolean isDirectory(int row) {
        return row >= 0 && row < fileModel.getRowCount() && fileModel.getFileAt(row).isDirectory();
    }

    private void navigateUp() {
        if (currentPath.getParent() != null) {
            loadDirectory(currentPath.getParent());
        }
    }

    private void navigateToTypedPath() {
        String typed = pathField.getText().trim();
        if (typed.isEmpty()) {
            return;
        }
        File dir = new File(typed);
        if (!dir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "La ruta no es una carpeta valida", "Error", JOptionPane.WARNING_MESSAGE);
            pathField.setText(currentPath.toString());
            return;
        }
        loadDirectory(dir.toPath());
    }

    private void acceptSelection() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecciona un archivo", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File file = new File(currentPath.toFile(), name);
        if (!file.exists() || !isSupportedModpackFile(file)) {
            JOptionPane.showMessageDialog(this, "Archivo inválido o no existe", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        selectedFile = file;
        approved = true;
        dispose();
    }

    private JButton makeIconBtn(String icon, java.awt.event.ActionListener al) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Dialog", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(28, 28));
        btn.setOpaque(false);
        btn.setBorder(new LineBorder(C_BORDER, 1));
        btn.setForeground(C_CYAN);
        btn.setBackground(new Color(0x08, 0x08, 0x22));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(al);
        return btn;
    }

    private JButton makeNavBtn(String icon, java.awt.event.ActionListener al) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Dialog", Font.PLAIN, 12));
        btn.setPreferredSize(new Dimension(36, 28));
        btn.setOpaque(false);
        btn.setBorder(new LineBorder(C_BORDER, 1));
        btn.setForeground(C_CYAN);
        btn.setBackground(new Color(0x08, 0x08, 0x22));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(al);
        return btn;
    }

    private JButton buildNeonBtn(String text, Color accent, int w, int h) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int r = accent.getRed(), gn = accent.getGreen(), b = accent.getBlue();
                Color base = getModel().isPressed() ? new Color(r/5, gn/5, b/5)
                        : getModel().isRollover() ? new Color(Math.min(255,r/3), Math.min(255,gn/3), Math.min(255,b/3))
                        : new Color(r/7, gn/7, b/7);
                g2.setColor(base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(r,gn,b,50));
                g2.setStroke(new BasicStroke(5f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 7, 7);
                g2.setColor(C_TEXT);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2,
                        (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Dialog", Font.BOLD, 12));
        btn.setPreferredSize(new Dimension(w, h));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public File showDialog() {
        setVisible(true);
        return approved ? selectedFile : null;
    }

    private static class FileTableModel extends AbstractTableModel {
        private List<File> files = new ArrayList<>();
        private static final String[] COLUMNS = {"Nombre", "Tipo", "Tamaño", "Modificado"};

        void setFiles(List<File> newFiles) {
            this.files = newFiles;
            fireTableDataChanged();
        }

        File getFileAt(int row) {
            return row >= 0 && row < files.size() ? files.get(row) : null;
        }

        @Override public int getRowCount() { return files.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override public Object getValueAt(int row, int col) {
            if (row < 0 || row >= files.size()) return "";
            File f = files.get(row);
            return switch (col) {
                case 0 -> (f.isDirectory() ? "📁 " : "📦 ") + f.getName();
                case 1 -> f.isDirectory() ? "Carpeta" : "Archivo";
                case 2 -> f.isDirectory() ? "-" : formatSize(f.length());
                case 3 -> new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(f.lastModified()));
                default -> "";
            };
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}





