package io.github.brickwall2900.tagged;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import io.github.brickwall2900.swing.adapters.DocumentAdapter;
import io.github.brickwall2900.swing.core.DialogBuilder;
import io.github.brickwall2900.swing.core.TargetLocator;
import io.github.brickwall2900.tagged.gif.GifImageWrapperIcon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

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
    public static final String PREF_KEY_SEARCH_OPTION = "SearchOption";

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
        // next time
        // we are NOT using swing gng </3
        // it's kinda too painful to use for me
        // too much legacy code
        FlatLightLaf.setup();

        Tagged frame = new Tagged();
        frame.setVisible(true);
        frame.loadStuff();
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
            public void windowClosed(WindowEvent e) {
                onWindowClose();
            }
        });

        Image icon = null;
        try (InputStream stream = Tagged.class.getResourceAsStream("icon.png")) {
            icon = ImageIO.read(Objects.requireNonNull(stream));
        } catch (IOException | NullPointerException e) {
            showError(BUNDLE.getString("error.loading.iconError"), e);
        }

        setTitle(BUNDLE.getString("title"));
        setIconImage(icon);
        pack();
        setLocationByPlatform(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void initContentPane() {
        JPanel contentPane = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new BorderLayout(PADDING, PADDING));
        JScrollPane scrollPanel = new JScrollPane();
        JPanel statusPanel = new JPanel(new BorderLayout());

        JTextField searchField = new JTextField();
        searchField.setName("SearchField");
        searchField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                onSearchFieldSearched(e);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                onSearchFieldSearched(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onSearchFieldSearched(e);
            }
        });
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
        TaggedFileListModel model = new TaggedFileListModel();
        list.setModel(model);

        TaggedFileListCellRenderer cellRenderer = new TaggedFileListCellRenderer(iconManager);
        list.setCellRenderer(cellRenderer);
        cellRenderer.setCellPadding(preferences.getInt(PREF_KEY_CELL_PADDING, 32));
        cellRenderer.setIconLoadedBuffer(preferences.getInt(PREF_KEY_CACHE_BUFFER, 50));

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);

        int cellSize = preferences.getInt(PREF_KEY_CELL_SIZE, 96);
        list.setFixedCellWidth(cellSize);
        list.setFixedCellHeight(cellSize);

        TaggedFileListModel.SearchOption searchOption = TaggedFileListModel.SearchOption.valueOf(
                preferences.get(PREF_KEY_SEARCH_OPTION, TaggedFileListModel.SearchOption.LENIENT.name())
        );
        model.setSearchOption(searchOption);

        JPopupMenu popupMenu = new JPopupMenu();
        initContextMenu(popupMenu::add, popupMenu::addSeparator);

        list.setComponentPopupMenu(popupMenu);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onListClicked(e);
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                onListKeyTyped(e);
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
        JMenu appMenu = new JMenu(BUNDLE.getString("menu.app"));

        JMenuItem addLocation = new JMenuItem(BUNDLE.getString("menu.app.add"), KeyEvent.VK_A);
        addLocation.addActionListener(this::onAddLocationMenuItemPressed);
        addLocation.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK));
        addLocation.setName("MenuAddLocation");

        JMenuItem saveIndex = new JMenuItem(BUNDLE.getString("menu.app.saveIndex"), KeyEvent.VK_S);
        saveIndex.addActionListener(this::onSaveTagsMenuItemPressed);
        saveIndex.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));

        JMenuItem options = new JMenuItem(BUNDLE.getString("menu.app.options"), KeyEvent.VK_O);
        options.addActionListener(this::onOptionsMenuItemPressed);
        options.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK));

        JMenuItem debug = new JMenuItem(BUNDLE.getString("menu.app.debug"));
        debug.addActionListener(this::onDebugMenuItemPressed);

        appMenu.add(addLocation);
        appMenu.add(saveIndex);
        appMenu.add(options);
        appMenu.addSeparator();
        appMenu.add(debug);

        initContextMenu(fileMenu::add, fileMenu::addSeparator);

        menuBar.add(fileMenu);
        menuBar.add(appMenu);

        setJMenuBar(menuBar);
    }

    private void initContextMenu(
            Consumer<JMenuItem> addItem,
            Runnable addSeparator) {
        JMenuItem open = new JMenuItem(BUNDLE.getString("menu.context.open"), KeyEvent.VK_O);
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        open.addActionListener(this::onContextOpenFileMenuItemPressed);

        JMenuItem showFileLocation = new JMenuItem(BUNDLE.getString("menu.context.showLocation"), KeyEvent.VK_F);
        showFileLocation.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK));
        showFileLocation.addActionListener(this::onContextShowLocationFileMenuItemPressed);

        JMenuItem tags = new JMenuItem(BUNDLE.getString("menu.context.tags"), KeyEvent.VK_T);
        tags.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK));
        tags.addActionListener(this::onContextTagMenuItemPressed);

        JMenuItem removeTags = new JMenuItem(BUNDLE.getString("menu.context.removeTags"), KeyEvent.VK_R);
        removeTags.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
        removeTags.addActionListener(this::onContextRemoveTagMenuItemPressed);

        addItem.accept(open);
        addItem.accept(showFileLocation);
        addSeparator.run();
        addItem.accept(tags);
        addItem.accept(removeTags);
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
        iconManager.cacheManager.setCacheMaxSize(cacheSizeLimit * 1024L * 1024L);
        preferences.putInt(PREF_KEY_CACHE_SIZE_LIMIT, Math.clamp(cacheSizeLimit, 0, Integer.MAX_VALUE / 1024 / 1024));
    }

    /// adds a location to the entry list
    public void addLocation(Path newLocation) {
        locations.add(newLocation);
    }

    public void removeLocation(Path location) {
        locations.remove(location);
    }

    public List<Path> getLocations() {
        return locationsUnmodifiable;
    }

    private void startIndexing(Long2ObjectMap<TaggedHelper.FileTag> map, Path newLocation) {
        SwingWorkerWithDone<TaggedHelper.FileTag[], Void> worker = helper.newIndexWorkerAsync(map, newLocation);
        worker.addPropertyChangeListener(evt -> {
            if (Objects.equals("path", evt.getPropertyName())) {
                updateStatusBar(BUNDLE.getString("status.finding").formatted(evt.getNewValue()));
            }
        });
        worker.onDone((files, exception) -> {
            if (exception != null) {
                showError(BUNDLE.getString("error.indexing.fileIndexError"), exception);
            } else {
                TaggedFileListModel model = ((TaggedFileListModel)
                        $("FileTagList", JList.class).getModel());

                model.addAll(files);
                updateStatusBarImmediate(BUNDLE.getString("status.finding.done").formatted(files.length));
                startIndexHashing(newLocation, files);
            }
        });
        worker.execute();
    }

    private void startWritingLocation(List<Path> locations) {
        SwingWorkerWithDone<Void, Void> worker = helper.newLocationWriterAsync(locations);
        worker.onDone((_, exception) -> {
            if (exception != null) {
                showError(BUNDLE.getString("error.indexing.locationWrite"), exception);
            }
        });
        worker.execute();
    }

    private void startIndexHashing(Path parentDirectory, TaggedHelper.FileTag[] files) {
        SwingWorkerWithDone<Long2ObjectMap<TaggedHelper.FileTag>, Void> worker = helper.newIndexHashWorkerAsync(files);
        worker.onDone((list, exception) -> {
            if (exception != null) {
                showError(BUNDLE.getString("error.indexing.hashIndexError"), exception);
            } else {
                System.out.printf("%d items hashed%n", list.size());
                helper.getHashToFileTagMap(parentDirectory).putAll(list);
                startIndexWriting(parentDirectory, list);
            }
        });
        worker.execute();
    }

    private void startIndexWriting(Path parentDirectory, Long2ObjectMap<TaggedHelper.FileTag> list) {
        SwingWorkerWithDone<Void, Void> worker = helper.newIndexWriterAsync(list, parentDirectory);
        worker.onDone((_, exception) -> {
            if (exception != null) {
                showError(BUNDLE.getString("error.indexing.indexWrite"), exception);
            } else {
                updateStatusBarImmediate(BUNDLE.getString("status.indexWritten"));
            }
        });
        worker.execute();
    }

    private void startIndexWritingAndWait(Path parentDirectory, Long2ObjectMap<TaggedHelper.FileTag> list) {
        SwingWorkerWithDone<Void, Void> worker = helper.newIndexWriterAsync(list, parentDirectory);
        worker.onDone((_, exception) -> {
            if (exception != null) {
                showError(BUNDLE.getString("error.indexing.indexWrite"), exception);
            }
        });
        worker.execute();
        try {
            worker.get();
        } catch (InterruptedException | ExecutionException _) {
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

    private void loadStuff() {
        if (helper.doesLocationExist()) {
            loadLocationsAndIndices();
        }
    }

    private void loadLocationsAndIndices() {
        // SON what am i doing???
        // I AM A BOUT TO GET SLIMED OMY
        $(getJMenuBar(), "MenuAddLocation", JMenuItem.class).setEnabled(false);
        SwingWorkerWithDone<List<Path>, Void> worker = helper.newLocationReaderAsync();
        worker.onDone((result, exception) -> {
            if (exception != null) {
                showError(BUNDLE.getString("error.loading.locationRead"), exception);
            } else {
                locations.addAll(result);
                $(getJMenuBar(), "MenuAddLocation", JMenuItem.class).setEnabled(true);

                for (Path location : result) {
                    loadIndices(location);
                }
            }
        });
        worker.execute();
    }

    private void loadIndices(Path location) {
        SwingWorkerWithDone<Long2ObjectMap<TaggedHelper.FileTag>, Void> worker = helper.newIndexReaderAsync(location);
        worker.onDone((result, exception) -> {
            if (exception != null) {
                showError(BUNDLE.getString("error.loading.indexRead"), exception);
            } else {
                helper.setHashToFileTagMap(location, result);
                startIndexing(result, location);
                updateStatusBarImmediate("Indices loaded");
            }
        });
        worker.execute();
    }

    // utility //
    /// okay shut up i'm trying something new
    private <T extends Component> T $(String selector, Class<T> as) {
        return $(getContentPane(), selector, as);
    }

    private <T extends Component> T $(Component src, String selector, Class<T> as) {
        return as.cast(TargetLocator.getTarget(selector, src, this));
    }

    /// returns a Path if user says yes, null otherwise
    public Path openFileChooserDirectory(boolean save) {
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
        if ((System.currentTimeMillis() - lastUpdateMillis) >= 33) {
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
            JDialog dialog = DialogBuilder.builder()
                    .title(getTitle())
                    .content(BUNDLE.getString("error.desktop.exception").formatted(error))
                    .messageType(JOptionPane.ERROR_MESSAGE)
                    .build(this);
            dialog.setIconImage(getIconImage());
            dialog.setVisible(true);
        }

        if (!desktopSupported) {
            JDialog dialog = DialogBuilder.builder()
                    .title(getTitle())
                    .content(BUNDLE.getString("error.desktop.notSupported"))
                    .messageType(JOptionPane.ERROR_MESSAGE)
                    .build(this);
            dialog.setIconImage(getIconImage());
            dialog.setVisible(true);
        }
    }

    private void tryBrowsingFile(Path path) {
        boolean desktopSupported = Desktop.isDesktopSupported();

        IOException error = null;

        if (path != null && desktopSupported) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                desktop.browseFileDirectory(path.toFile());
            } else {
                try {
                    desktop.open(path.getParent().toFile());
                } catch (IOException e) {
                    error = e;
                }
            }
        }

        if (error != null) {
            JDialog dialog = DialogBuilder.builder()
                    .title(getTitle())
                    .content(BUNDLE.getString("error.desktop.exception").formatted(error))
                    .messageType(JOptionPane.ERROR_MESSAGE)
                    .build(this);
            dialog.setIconImage(getIconImage());
            dialog.setVisible(true);
        }

        if (!desktopSupported) {
            JDialog dialog = DialogBuilder.builder()
                    .title(getTitle())
                    .content(BUNDLE.getString("error.desktop.notSupported"))
                    .messageType(JOptionPane.ERROR_MESSAGE)
                    .build(this);
            dialog.setIconImage(getIconImage());
            dialog.setVisible(true);
        }
    }

    @SuppressWarnings("unchecked")
    private void performSearch(String filter) {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        TaggedFileListModel model = ((TaggedFileListModel) list.getModel());

        list.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        String finalFilter = filter == null || filter.isBlank() ? null : filter;
        SwingUtilities.invokeLater(() -> model.setFilter(finalFilter));

        list.setCursor(null);
    }

    private void showError(String content, Throwable t) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(content), BorderLayout.NORTH);

        JTextArea errorField = new JTextArea();
        errorField.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(errorField);

        try (StringWriter writer = new StringWriter();
             PrintWriter out = new PrintWriter(writer)) {
            t.printStackTrace(out);
            errorField.setText(writer.toString());
        } catch (IOException e) {
            // silence
            e.addSuppressed(t);
            e.printStackTrace(System.err);
            errorField.setText(t.toString());
        }

        panel.add(scrollPane, BorderLayout.CENTER);

        JDialog dialog = DialogBuilder.builder()
                .title(getTitle())
                .content(panel)
                .messageType(JOptionPane.ERROR_MESSAGE)
                .build(this);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(true);
        dialog.setVisible(true);
    }

    // events //

    private void onAddLocationMenuItemPressed(ActionEvent e) {
        LocationDialog dialog = new LocationDialog(this);
        dialog.setVisible(true);

        // re index all locations lol
        TaggedFileListModel model = ((TaggedFileListModel)
                $("FileTagList", JList.class).getModel());
        model.clear();

        startWritingLocation(locations);
        for (Path location : locations) {
            Long2ObjectMap<TaggedHelper.FileTag> map = helper.getHashToFileTagMap(location);
            map = map != null ? map : new Long2ObjectOpenHashMap<>();
            helper.setHashToFileTagMap(location, map);
            startIndexing(map, location);
        }
    }

    private void onSaveTagsMenuItemPressed(ActionEvent e) {
        for (Path location : locations) {
            startIndexWriting(location, helper.getHashToFileTagMap(location));
        }
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
                Path filePath = fileTag.locationPath();
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
                preferences.getInt(PREF_KEY_CELL_PADDING, 64),
                preferences.getInt(PREF_KEY_THREAD_COUNT,
                        (int) (Runtime.getRuntime().availableProcessors() / 1.25)),
                preferences.getInt(PREF_KEY_CACHE_BUFFER, 50),
                preferences.getInt(PREF_KEY_CACHE_SIZE_LIMIT, 0),
                TaggedFileListModel.SearchOption.valueOf(
                        preferences.get(PREF_KEY_SEARCH_OPTION, TaggedFileListModel.SearchOption.LENIENT.name())
                )
        ));
        optionDialog.setOnOptionsApplied((options) -> {
            preferences.putInt(PREF_KEY_CELL_SIZE, Math.clamp(options.cellSize(), 32, Short.MAX_VALUE));
            preferences.putInt(PREF_KEY_CELL_PADDING, Math.clamp(options.cellPadding(), 0, Short.MAX_VALUE));
            preferences.putInt(PREF_KEY_CACHE_BUFFER, Math.clamp(options.cacheBuffer(), 0, Integer.MAX_VALUE));
            preferences.putInt(PREF_KEY_CACHE_SIZE_LIMIT, Math.clamp(options.cacheSizeLimit(), 0, Integer.MAX_VALUE / 1024 / 1024));
            preferences.putInt(PREF_KEY_THREAD_COUNT, options.threads());
            preferences.putBoolean(PREF_KEY_DARK_MODE, options.darkMode());
            preferences.putBoolean(PREF_KEY_FAST_TARGET, options.fastTarget());
            preferences.put(PREF_KEY_SEARCH_OPTION, String.valueOf(options.searchOption()));

            setDarkMode(options.darkMode(), optionDialog);
            // specifically for dark mode ;-;);
            helper.changeThreadCount(options.threads());

            JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
            TaggedFileListModel model = (TaggedFileListModel) list.getModel();
            TaggedFileListCellRenderer cellRenderer = (TaggedFileListCellRenderer) list.getCellRenderer();
            cellRenderer.setCellPadding(options.cellPadding());
            cellRenderer.setIconLoadedBuffer(options.cacheBuffer());

            model.setSearchOption(options.searchOption());

            int cellSize = options.cellSize();
            list.setFixedCellWidth(cellSize);
            list.setFixedCellHeight(cellSize);

            iconManager.setFastTargetEnabled(options.fastTarget());
            iconManager.cacheManager.setCacheMaxSize(options.cacheSizeLimit() * 1024L * 1024L);
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
            tryOpeningFile(selected.locationPath());
        }
    }

    @SuppressWarnings("unchecked")
    private void onContextShowLocationFileMenuItemPressed(ActionEvent e) {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        TaggedHelper.FileTag selected = list.getSelectedValue();
        if (selected != null) {
            tryBrowsingFile(selected.locationPath());
        }
    }

    @SuppressWarnings("unchecked")
    private void onContextTagMenuItemPressed(ActionEvent e) {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        TaggedFileListModel model = (TaggedFileListModel) list.getModel();
        List<TaggedHelper.FileTag> selectedTags = list.getSelectedValuesList();

        if (selectedTags == null || selectedTags.isEmpty()) {
            return;
        }

        for (TaggedHelper.FileTag selected : selectedTags) {
            /*JTextField tagField = new JTextField();

            JPanel panel = new JPanel(new BorderLayout(4, 4));
            panel.add(new JLabel(BUNDLE.getString("dialog.tagEditor")), BorderLayout.NORTH);
            panel.add(tagField, BorderLayout.CENTER);
            panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));*/

            DialogBuilder builder = DialogBuilder.builder()
                    //.content(panel)
                    .content(BUNDLE.getString("dialog.tagEditor").formatted(selected.fileName()))
                    .messageType(JOptionPane.QUESTION_MESSAGE)
                    .optionType(JOptionPane.OK_CANCEL_OPTION)
                    .setModalityType(Dialog.ModalityType.APPLICATION_MODAL)
                    .title(getTitle())
                    .wantsInput(true);

            JOptionPane optionPane = builder.getOptionPane();
            optionPane.setInitialSelectionValue(String.join(" ", selected.tags()));

            JDialog dialog = builder.build(this);
            dialog.setIconImage(getIconImage());
            dialog.setVisible(true);

            Object result = optionPane.getValue();
            String inputValue = Objects.toString(optionPane.getInputValue());
            if (result == null) {
                continue;
            }
            if (Objects.equals(result, JOptionPane.OK_OPTION)) {
                String[] tags = inputValue.split("\\s+");

                if (tags.length == 1 && tags[0].isBlank()) {
                    tags = new String[0];
                }

                TaggedHelper.FileTag newTag = new TaggedHelper.FileTag(selected.locationPath(), selected.fileName(), tags);
                model.modify(selected, newTag);
                helper.storeTag(newTag);
            } else if (Objects.equals(result, JOptionPane.CANCEL_OPTION)) {
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void onContextRemoveTagMenuItemPressed(ActionEvent e) {
        JList<TaggedHelper.FileTag> list = $("FileTagList", JList.class);
        TaggedFileListModel model = (TaggedFileListModel) list.getModel();
        List<TaggedHelper.FileTag> selected = list.getSelectedValuesList();
        if (selected != null && !selected.isEmpty()) {
            DialogBuilder builder = DialogBuilder.builder()
                    // is this mf obsessed with the stream API???
                    .content(BUNDLE.getString("dialog.removeTag").formatted(selected.stream()
                            .map(TaggedHelper.FileTag::fileName)
                            .map(Path::toString)
                            .collect(Collectors.joining(", "))))
                    .messageType(JOptionPane.QUESTION_MESSAGE)
                    .optionType(JOptionPane.YES_NO_OPTION)
                    .setModalityType(Dialog.ModalityType.APPLICATION_MODAL)
                    .title(getTitle());

            JOptionPane optionPane = builder.getOptionPane();

            JDialog dialog = builder.build(this);
            dialog.setIconImage(getIconImage());
            dialog.setVisible(true);

            Object result = optionPane.getValue();
            if (result != null && Objects.equals(result, JOptionPane.OK_OPTION)) {
                for (TaggedHelper.FileTag fileTag : selected) {
                    TaggedHelper.FileTag newTag = new TaggedHelper.FileTag(fileTag.locationPath(), fileTag.fileName(),
                            new String[0]);
                    model.modify(fileTag, newTag);
                    helper.storeTag(newTag);
                }
            }
        }
    }

    private void onListClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            onContextOpenFileMenuItemPressed(null);
        }
    }

    private void onListKeyTyped(KeyEvent e) {
        if (e.isControlDown()) {
            return;
        }

        if (e.getKeyChar() == '\n') {
            onContextOpenFileMenuItemPressed(null);
            return;
        }

        JTextField searchField = $("SearchField", JTextField.class);
        searchField.dispatchEvent(e);
        searchField.requestFocus(FocusEvent.Cause.ACTIVATION);
    }

    private void onSearchFieldSearched(DocumentEvent e) {
        JTextField field = $("SearchField", JTextField.class);
        String text = field.getText();
        performSearch(text);
    }

    private void onWindowClose() {
        System.out.println("Shutting down!");
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            showError(BUNDLE.getString("error.preferences.store"), e);
        }
        for (Path location : locations) {
            startIndexWritingAndWait(location, helper.getHashToFileTagMap(location));
        }
        repaintTimer.stop();
        iconManager.shutdown();
        helper.shutdown();
        System.out.println("Goodbye!");
        System.exit(0);
    }

    private final TaggedHelper helper;
    private final IconManager iconManager;
    private final List<Path> locations = new ArrayList<>();
    private final List<Path> locationsUnmodifiable = Collections.unmodifiableList(locations);
    final Preferences preferences;
    private Timer repaintTimer;
    private JFileChooser fileChooser;
}
