package io.github.brickwall2900.tagged;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import io.github.brickwall2900.swing.core.TargetLocator;
import io.github.brickwall2900.tagged.gif.GifImageWrapperIcon;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.ToLongFunction;
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
        iconManager = new IconManager();

        preferences = Preferences.userNodeForPackage(Tagged.class);
        loadPreferences();

        initContentPane();
        initMenu();
        initRepaintTimer();

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

        JList<TaggedHelper.FileTag> list = new JList<>() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                onListRepaint();
            }
        };
        list.setName("FileTagList");
        list.setModel(new TaggedFileListModel());
        list.setCellRenderer(new TaggedFileListCellRenderer(iconManager));
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(96);
        list.setFixedCellHeight(96);
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

        JCheckBoxMenuItem debug = new JCheckBoxMenuItem(BUNDLE.getString("menu.file.debug"));
        debug.addActionListener(this::onDebugMenuItemPressed);

        fileMenu.add(fileAddLocation);
        fileMenu.add(fileDarkMode);
        fileMenu.addSeparator();
        fileMenu.add(debug);

        menuBar.add(fileMenu);

        setJMenuBar(menuBar);
    }

    private void initRepaintTimer() {
        repaintTimer = new Timer(33, this::onRepaintTick);
        repaintTimer.setRepeats(true);
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
                    updateStatusBar(BUNDLE.getString("status.finding").formatted(evt.getNewValue()));
                }
            });
            worker.onDone((files, exception) -> {
                if (exception != null) {
                    exception.printStackTrace();
                } else {
                    TaggedFileListModel model = ((TaggedFileListModel)
                            $("FileTagList", JList.class).getModel());
                    model.addAll(files);
                    updateStatusBarImmediate(BUNDLE.getString("status.finding.done").formatted(files.length));
                }
            });
            worker.execute();
        }
    }

    private void setDarkMode(boolean darkMode) {
        SwingUtilities.invokeLater(() -> {
            FlatAnimatedLafChange.showSnapshot();
            if (darkMode) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            SwingUtilities.updateComponentTreeUI(this);
            if (fileChooser != null) {
                SwingUtilities.updateComponentTreeUI(fileChooser);
            }
            FlatAnimatedLafChange.hideSnapshotWithAnimation();

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

    private void updateStatusBarImmediate(String text) {
        $("StatusLabel", JLabel.class).setText(text);
    }

    // events //
    private void onDarkModeMenuItemChecked(ChangeEvent e) {
        JCheckBoxMenuItem src = (JCheckBoxMenuItem) e.getSource();
        setDarkMode(src.isSelected());
    }

    private void onAddLocationMenuItemPressed(ActionEvent e) {
        addLocation();
    }

    @SuppressWarnings("unchecked")
    private void onListRepaint() {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        int firstVisibleIndex = list.getFirstVisibleIndex();
        int lastVisibleIndex = list.getLastVisibleIndex();
        if (firstVisibleIndex != -1 && lastVisibleIndex != -1) {
            TaggedFileListModel model = (TaggedFileListModel) list.getModel();
            long minDelayTime = iconManager.getMinimumRefreshTime(
                    model.subList(firstVisibleIndex, lastVisibleIndex + 1)
                            .stream()
                            .map(TaggedHelper.FileTag::filePath)
                            .toList());
            if (minDelayTime != -1) {
                repaintTimer.setDelay((int) minDelayTime);
                if (!repaintTimer.isRunning()) {
                    System.out.println("Repaint timer started");
                    repaintTimer.start();
                }
            } else {
                if (repaintTimer.isRunning()) {
                    System.out.println("Repaint timer stopped");
                    repaintTimer.stop();
                }
            }
        }
    }

    // dirty ahh
    private void onDebugMenuItemPressed(ActionEvent e) {
        Runtime runtime = Runtime.getRuntime();

        JOptionPane optionPane = new JOptionPane();
        JTextArea textArea = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(textArea);
        optionPane.setMessage(scrollPane);
        optionPane.setMessageType(JOptionPane.INFORMATION_MESSAGE);
        optionPane.setOptionType(JOptionPane.DEFAULT_OPTION);

        JDialog dialog = optionPane.createDialog(this, BUNDLE.getString("debug.title"));
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        dialog.setResizable(true);

        Timer dbgTimer = new Timer(250, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Map<Path, Reference<Icon>> cache = iconManager.getIconCacheMap();
                    Set<Reference<Icon>> icons;
                    synchronized (cache) {
                        icons = new HashSet<>(cache.values());
                    }
                    int entries = icons.size();
                    long gifMemoryUsage = 0L;
                    long gifCanvasMemoryUsage = 0L;
                    for (Reference<Icon> iconRef : icons) {
                        Icon icon = iconRef != null ? iconRef.get() : null;
                        if (icon instanceof GifImageWrapperIcon gifImageWrapperIcon) {
                            gifMemoryUsage += gifImageWrapperIcon.getMemoryUsage();
                            gifCanvasMemoryUsage += gifImageWrapperIcon.getCanvasMemoryUsage();
                        }
                    }
                    textArea.setText(BUNDLE.getString("debug")
                            .formatted(
                                    gifMemoryUsage,
                                    gifMemoryUsage / 1024.0 / 1024.0,
                                    gifCanvasMemoryUsage / 1024.0 / 1024.0,
                                    entries,
                                    (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0,
                                    runtime.freeMemory() / 1024.0 / 1024.0,
                                    runtime.totalMemory() / 1024.0 / 1024.0,
                                    iconManager.getMaxEntries()));
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }
        });
        dbgTimer.start();
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dbgTimer.stop();
            }
        });
        dialog.setVisible(true);
    }

    @SuppressWarnings("unchecked")
    private void onRepaintTick(ActionEvent actionEvent) {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        list.repaint(list.getVisibleRect());


    }

    private void onWindowClose() {
        repaintTimer.stop();
        iconManager.shutdown();
        helper.shutdown();
    }

    private final TaggedHelper helper;
    private final IconManager iconManager;
    private final List<Path> locations = new ArrayList<>();
    private final Preferences preferences;
    private Timer repaintTimer;
    private JFileChooser fileChooser;
}
