package io.github.brickwall2900.tagged;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import io.github.brickwall2900.swing.core.TargetLocator;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public class Tagged extends JFrame {
    private static final int PADDING = 4;
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(Tagged.class.getName());

    static void main() {
        SwingUtilities.invokeLater(Tagged::swingMain);
    }

    private static void swingMain() {
        FlatLightLaf.setup();

        Tagged frame = new Tagged();
        frame.setVisible(true);
    }

    public Tagged() {
        helper = new TaggedHelper(this);

        preferences = Preferences.userNodeForPackage(Tagged.class);
        loadPreferences();

        initContentPane();
        initMenu();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClose();
            }
        });

        setTitle(BUNDLE.getString("title"));
        pack();
        setLocationByPlatform(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void initContentPane() {
        JPanel contentPane = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new BorderLayout(PADDING, PADDING));
        JScrollPane scrollPanel = new JScrollPane();
        JPanel statusPanel = new JPanel(new BorderLayout());

        JTextField searchField = new JTextField();
        searchField.setName("SearchField");
        searchPanel.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        searchPanel.add(searchField, BorderLayout.CENTER);

        JList<TaggedHelper.FileTag> list = new JList<>();
        list.setName("FileTagList");
        list.setModel(new DefaultListModel<>());
        list.setCellRenderer(new TaggedFileListCellRenderer());
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(128);
        list.setFixedCellHeight(128);
        scrollPanel.setViewportView(list);
        scrollPanel.setPreferredSize(new Dimension(800, 600));

        JLabel statusLabel = new JLabel();
        statusLabel.setName("StatusLabel");
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        contentPane.add(searchPanel, BorderLayout.NORTH);
        contentPane.add(scrollPanel, BorderLayout.CENTER);
        contentPane.add(statusPanel, BorderLayout.SOUTH);
        setContentPane(contentPane);
    }

    private void initMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu(BUNDLE.getString("menu.file"));

        JMenuItem fileAddLocation = new JMenuItem(BUNDLE.getString("menu.file.add"), KeyEvent.VK_A);
        fileAddLocation.addActionListener(this::onAddLocationMenuItemPressed);
        fileAddLocation.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK));

        JCheckBoxMenuItem fileDarkMode = new JCheckBoxMenuItem(BUNDLE.getString("menu.file.darkMode"));
        fileDarkMode.setSelected(preferences.getBoolean("darkmode", false));
        fileDarkMode.addChangeListener(this::onDarkModeMenuItemChecked);
        fileDarkMode.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK));

        fileMenu.add(fileAddLocation);
        fileMenu.add(fileDarkMode);

        menuBar.add(fileMenu);

        setJMenuBar(menuBar);
    }

    private void loadPreferences() {
        assert preferences != null;
        boolean darkMode = preferences.getBoolean("darkmode", false);
        setDarkMode(darkMode);
    }

    /// adds a location to the entry list
    @SuppressWarnings("unchecked")
    private void addLocation() {
        Path newLocation = openFileChooserDirectory(false);
        if (newLocation != null) {
            locations.add(newLocation);
            SwingWorkerWithDone<TaggedHelper.FileTag[], Void> worker = helper.startIndexingAsync(newLocation);
            worker.addPropertyChangeListener(evt -> {
                if (Objects.equals("path", evt.getPropertyName())) {
                    updateStatusBar("Found " + evt.getNewValue());
                }
            });
            worker.onDone((files, exception) -> {
                if (exception != null) {
                    exception.printStackTrace();
                } else {
                    DefaultListModel<TaggedHelper.FileTag> model = ((DefaultListModel<TaggedHelper.FileTag>)
                            $("FileTagList", JList.class).getModel());
                    model.addAll(List.of(files));
                    updateStatusBar("");
                }
            });
            worker.execute();
        }
    }

    private void setDarkMode(boolean darkMode) {
        SwingUtilities.invokeLater(() -> {
            if (darkMode) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            SwingUtilities.updateComponentTreeUI(this);
            if (fileChooser != null) {
            SwingUtilities.updateComponentTreeUI(fileChooser);
            }

            preferences.putBoolean("darkmode", darkMode);
        });
    }

    // utility //

    /// okay shut up i'm trying something new
    private <T extends Component> T $(String selector, Class<T> as) {
        return as.cast(TargetLocator.getTarget(selector, getContentPane(), this));
    }

    /// returns a Path if user says yes, null otherwise
    private Path openFileChooserDirectory(boolean save) {
        initFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = save ? fileChooser.showSaveDialog(this) : fileChooser.showOpenDialog(this);
        return result == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile().toPath() : null;
    }

    private void initFileChooser() {
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(Path.of(System.getProperty("user.home")).toFile());
        }
    }

    private long lastUpdateMillis = System.currentTimeMillis();
    private void updateStatusBar(String text) {
        if ((System.currentTimeMillis() - lastUpdateMillis) >= 100) {
            $("StatusLabel", JLabel.class).setText(text);
            lastUpdateMillis = System.currentTimeMillis();
        }
    }

    // events //
    private void onDarkModeMenuItemChecked(ChangeEvent e) {
        JCheckBoxMenuItem src = (JCheckBoxMenuItem) e.getSource();
        setDarkMode(src.isSelected());
    }

    private void onAddLocationMenuItemPressed(ActionEvent e) {
        addLocation();
    }

    private void onWindowClose() {
        helper.shutdown();
    }

    private final TaggedHelper helper;
    private final List<Path> locations = new ArrayList<>();
    private final Preferences preferences;
    private JFileChooser fileChooser;
}
