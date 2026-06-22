package io.github.brickwall2900.tagged;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import io.github.brickwall2900.swing.core.DialogBuilder;
import io.github.brickwall2900.swing.core.TargetLocator;
import io.github.brickwall2900.tagged.gif.GifImageWrapperIcon;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class Tagged extends JFrame {
    private static final int PADDING = 4;
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(Tagged.class.getName());

    public static final String PREF_KEY_THREAD_COUNT = "ThreadCount";
    public static final String PREF_KEY_CELL_SIZE = "CellSize";
    public static final String PREF_KEY_CELL_PADDING = "CellPadding";
    public static final String PREF_KEY_CACHE_BUFFER = "CacheBuffer";
    public static final String PREF_KEY_CACHE_SIZE_LIMIT = "CacheSizeLimit";
    public static final String PREF_KEY_DARK_MODE = "DarkMode";
    public static final String PREF_KEY_FAST_TARGET = "FastTarget";

    static {
        try {
            assert false;
        } catch (AssertionError _) {
            System.err.println("our dearest MARI");
            System.err.println("the sun shined brighter when she was here");
        }
    }

    private int refreshRate;
    private GraphicsDevice graphicsDevice;
    private DisplayMode displayMode;

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
        iconManager = new IconManager(helper);

        preferences = Preferences.userNodeForPackage(Tagged.class);
        loadPreferences();

        initContentPane();
        initMenu();
        initRepaintTimer();

        graphicsDevice = getGraphicsConfiguration().getDevice();
        displayMode = graphicsDevice.getDisplayMode();

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
        TaggedFileListCellRenderer cellRenderer = new TaggedFileListCellRenderer(iconManager);
        list.setCellRenderer(cellRenderer);
        cellRenderer.setCellPadding(preferences.getInt(PREF_KEY_CELL_PADDING, 32));
        cellRenderer.setIconLoadedBuffer(preferences.getInt(PREF_KEY_CACHE_BUFFER, 50));
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        int cellSize = preferences.getInt(PREF_KEY_CELL_SIZE, 96);
        list.setFixedCellWidth(cellSize);
        list.setFixedCellHeight(cellSize);
        list.setComponentPopupMenu(initContextMenu());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onListClicked(e);
            }
        });
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

        JMenuItem options = new JMenuItem(BUNDLE.getString("menu.file.options"), KeyEvent.VK_O);
        options.addActionListener(this::onOptionsMenuItemPressed);
        options.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK));

        JCheckBoxMenuItem debug = new JCheckBoxMenuItem(BUNDLE.getString("menu.file.debug"));
        debug.addActionListener(this::onDebugMenuItemPressed);

        fileMenu.add(fileAddLocation);
        fileMenu.add(options);
        fileMenu.addSeparator();
        fileMenu.add(debug);

        menuBar.add(fileMenu);

        setJMenuBar(menuBar);
    }

    private JPopupMenu initContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem open = new JMenuItem(BUNDLE.getString("menu.context.open"), KeyEvent.VK_O);
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        open.addActionListener(this::onContextOpenFileMenuItemPressed);

        JMenuItem showFileLocation = new JMenuItem(BUNDLE.getString("menu.context.showLocation"), KeyEvent.VK_S);
        showFileLocation.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
        showFileLocation.addActionListener(this::onContextShowLocationFileMenuItemPressed);

        JMenuItem tags = new JMenuItem(BUNDLE.getString("menu.context.tags"), KeyEvent.VK_T);
        tags.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK));

        popupMenu.add(open);
        popupMenu.add(showFileLocation);
        popupMenu.addSeparator();
        popupMenu.add(tags);

        return popupMenu;
    }

    private void initRepaintTimer() {
        repaintTimer = new Timer(33, this::onRepaintTick);
        repaintTimer.setRepeats(true);
    }

    private void loadPreferences() {
        assert preferences != null;
        boolean darkMode = preferences.getBoolean(PREF_KEY_DARK_MODE, false);
        setDarkMode(darkMode);

        // make this one hardcoded
        // why disable an optimization?
        // oh for memory usage i guess
        // but images take up less memory usage when we uhhhhh rescaled them to IconManager's thumbnailSize
        // but that can change
        boolean fastTarget = preferences.getBoolean(PREF_KEY_FAST_TARGET, true);
        iconManager.setFastTargetEnabled(fastTarget);

        int threadCount = preferences.getInt(PREF_KEY_THREAD_COUNT,
                (int) (Runtime.getRuntime().availableProcessors() / 1.25));
        threadCount = Math.clamp(threadCount, 1, Runtime.getRuntime().availableProcessors());
        preferences.putInt(PREF_KEY_THREAD_COUNT, threadCount);
        helper.changeThreadCount(threadCount);

        int cellSize = preferences.getInt(PREF_KEY_CELL_SIZE, 96);
        preferences.putInt(PREF_KEY_CELL_SIZE, Math.clamp(cellSize, 32, Short.MAX_VALUE));

        int cellPadding = preferences.getInt(PREF_KEY_CELL_PADDING, 32);
        preferences.putInt(PREF_KEY_CELL_PADDING, Math.clamp(cellPadding, 32, Short.MAX_VALUE));

        int cacheBuffer = preferences.getInt(PREF_KEY_CACHE_BUFFER, 50);
        preferences.putInt(PREF_KEY_CACHE_BUFFER, Math.clamp(cacheBuffer, 0, Integer.MAX_VALUE));

        int cacheSizeLimit = preferences.getInt(PREF_KEY_CACHE_SIZE_LIMIT, 0);
        preferences.putInt(PREF_KEY_CACHE_SIZE_LIMIT, Math.clamp(cacheSizeLimit, 0, Integer.MAX_VALUE / 1024 / 1024));
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

    private void setDarkMode(boolean darkMode, Component... needToBeUpdated) {
        SwingUtilities.invokeLater(() -> {
            FlatAnimatedLafChange.showSnapshot();
            if (darkMode) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            for (Component c : needToBeUpdated) {
                SwingUtilities.updateComponentTreeUI(c);
            }
            SwingUtilities.updateComponentTreeUI(this);
            if (fileChooser != null) {
                SwingUtilities.updateComponentTreeUI(fileChooser);
            }
            FlatAnimatedLafChange.hideSnapshotWithAnimation();

            preferences.putBoolean(PREF_KEY_DARK_MODE, darkMode);
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

    private void tryOpeningFile(Path path) {
        boolean desktopSupported = Desktop.isDesktopSupported();

        IOException error = null;

        if (path != null && desktopSupported) {
            Desktop desktop = Desktop.getDesktop();
            try {
                desktop.open(path.toFile());
            } catch (IOException ex) {
                error = ex;
            }
        }

        if (error != null) {
            DialogBuilder.builder()
                    .title(getTitle())
                    .content(BUNDLE.getString("error.desktop.exception").formatted(error))
                    .messageType(JOptionPane.ERROR_MESSAGE)
                    .build(this)
                    .setVisible(true);
        }

        if (!desktopSupported) {
            DialogBuilder.builder()
                    .title(getTitle())
                    .content(BUNDLE.getString("error.desktop.notSupported"))
                    .messageType(JOptionPane.ERROR_MESSAGE)
                    .build(this)
                    .setVisible(true);
        }
    }

    // events //

    private void onAddLocationMenuItemPressed(ActionEvent e) {
        addLocation();
    }

    @SuppressWarnings("unchecked")
    private void onListRepaint() {
        //System.out.println("~~~ List scrolled ~~~");
        //Timestamped t = new Timestamped();
        //t.push("Selector");
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        //t.reportPopAndPush("GetIndices");
        int firstVisibleIndex = list.getFirstVisibleIndex();
        int lastVisibleIndex = list.getLastVisibleIndex();
        //t.reportPopAndPush("Method");
        if (firstVisibleIndex != -1 && lastVisibleIndex != -1) {
            TaggedFileListModel model = (TaggedFileListModel) list.getModel();
            List<Path> result = new ArrayList<>();
            //t.push("MinDelayTimeGet");
            for (int i = firstVisibleIndex; i < lastVisibleIndex + 1; i++) {
                TaggedHelper.FileTag fileTag = model.getElementAt(i);
                Path filePath = fileTag.filePath();
                result.add(filePath);
            }
            long minDelayTime = iconManager.getMinimumRefreshTime(
                    result);
            //t.reportPopAndPush("RepaintTimerSet");
            if (minDelayTime != -1) {
                int refreshRate = displayMode.getRefreshRate();
                int refreshTime = (int) ((1f / refreshRate) * 1e3);
                minDelayTime = Math.max(minDelayTime, refreshTime);
                repaintTimer.setDelay((int) minDelayTime);
                if (!repaintTimer.isRunning()) {
                    list.setIgnoreRepaint(true);
                    repaintTimer.start();
                }
            } else {
                if (repaintTimer.isRunning()) {
                    list.setIgnoreRepaint(false);
                    repaintTimer.stop();
                }
            }
            //t.reportAndPop();
        }
        //t.reportAndPop();
    }

    @SuppressWarnings("unchecked")
    private void onOptionsMenuItemPressed(ActionEvent e) {
        OptionDialog optionDialog = new OptionDialog(this);
        optionDialog.loadOptions(new OptionDialog.ApplicationOptions(
                preferences.getBoolean(PREF_KEY_DARK_MODE, false),
                preferences.getBoolean(PREF_KEY_FAST_TARGET, true),
                preferences.getInt(PREF_KEY_CELL_SIZE, 96),
                preferences.getInt(PREF_KEY_CELL_PADDING, 32),
                preferences.getInt(PREF_KEY_THREAD_COUNT,
                        (int) (Runtime.getRuntime().availableProcessors() / 1.25)),
                preferences.getInt(PREF_KEY_CACHE_BUFFER, 50),
                preferences.getInt(PREF_KEY_CACHE_SIZE_LIMIT, 0)
        ));
        optionDialog.setOnOptionsApplied((options) -> {
            preferences.putInt(PREF_KEY_CELL_SIZE, Math.clamp(options.cellSize(), 32, Short.MAX_VALUE));
            preferences.putInt(PREF_KEY_CELL_PADDING, Math.clamp(options.cellPadding(), 32, Short.MAX_VALUE));
            preferences.putInt(PREF_KEY_CACHE_BUFFER, Math.clamp(options.cacheBuffer(), 0, Integer.MAX_VALUE));
            preferences.putInt(PREF_KEY_CACHE_SIZE_LIMIT, Math.clamp(options.cacheSizeLimit(), 0, Integer.MAX_VALUE / 1024 / 1024));
            preferences.putInt(PREF_KEY_THREAD_COUNT, options.threads());
            preferences.putBoolean(PREF_KEY_DARK_MODE, options.darkMode());
            preferences.putBoolean(PREF_KEY_FAST_TARGET, options.fastTarget());

            setDarkMode(options.darkMode(), optionDialog);
            // specifically for dark mode ;-;);
            helper.changeThreadCount(options.threads());

            JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
            TaggedFileListCellRenderer cellRenderer = (TaggedFileListCellRenderer) list.getCellRenderer();
            cellRenderer.setCellPadding(options.cellPadding());
            cellRenderer.setIconLoadedBuffer(options.cacheBuffer());

            int cellSize = options.cellSize();
            list.setFixedCellWidth(cellSize);
            list.setFixedCellHeight(cellSize);

            iconManager.setFastTargetEnabled(options.fastTarget());
        });
        optionDialog.setVisible(true);
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
                    LRUCache<Path, Reference<Icon>> cache = iconManager.getIconCacheMap();
                    Set<Reference<Icon>> icons = new HashSet<>(cache.values());
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
                                    repaintTimer.isRunning(),
                                    repaintTimer.getDelay(),
                                    repaintTimer.getDelay() / 1000.0,
                                    1 / (repaintTimer.getDelay() / 1000.0),
                                    iconManager.getShownThumbnailSize(),
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
    private void onRepaintTick(ActionEvent e) {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        list.repaint(list.getVisibleRect());
    }

    @SuppressWarnings("unchecked")
    private void onContextOpenFileMenuItemPressed(ActionEvent e) {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        TaggedHelper.FileTag selected = list.getSelectedValue();
        if (selected != null) {
            tryOpeningFile(selected.filePath());
        }
    }

    @SuppressWarnings("unchecked")
    private void onContextShowLocationFileMenuItemPressed(ActionEvent e) {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        TaggedHelper.FileTag selected = list.getSelectedValue();
        if (selected != null) {
            tryOpeningFile(selected.filePath().getParent());
        }
    }

    private void onListClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            onContextOpenFileMenuItemPressed(null);
        }
    }

    private void onWindowClose() {
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
        repaintTimer.stop();
        iconManager.shutdown();
        helper.shutdown();
    }

    private final TaggedHelper helper;
    private final IconManager iconManager;
    private final List<Path> locations = new ArrayList<>();
    final Preferences preferences;
    private Timer repaintTimer;
    private JFileChooser fileChooser;
}
