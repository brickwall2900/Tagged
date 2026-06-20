package io.github.brickwall2900.tagged;

// one big gigantic function
// im tired

import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.github.brickwall2900.tagged.gif.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// i dont deserve friends and i hate myself so fucking much
public class IconManager {
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(ImageIO.getReaderMIMETypes());
    public static final String SVGFILE = """
            <svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24">
            	<path d="M0 0h24v24H0z" fill="none" />
            	<path fill="currentColor" d="M13 9V3.5L18.5 9M6 2c-1.11 0-2 .89-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z" />
            </svg>""";

    public final Icon defaultPlaceholderIcon;
    public final FlatSVGIcon.ColorFilter colorFilter;
    public int thumbnailSize = 32;

    public final ExecutorService executor = Executors.newFixedThreadPool(
            (int) (Runtime.getRuntime().availableProcessors() / 1.25));
    //public final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final LRUCache<Path, Reference<Icon>> thumbnailCacheLRU = new LRUCache<>(30, this::onItemRemoved);
    private final Map<Path, Reference<Icon>> thumbnailCache = Collections.synchronizedMap(thumbnailCacheLRU);
    private final Set<Path> loadingTasks = ConcurrentHashMap.newKeySet();

    public IconManager() {
        try {
            defaultPlaceholderIcon = new FlatSVGIcon(new ByteArrayInputStream(SVGFILE.getBytes(StandardCharsets.UTF_8)));
            colorFilter = new FlatSVGIcon.ColorFilter();
            colorFilter.setMapper(_ -> UIManager.getColor("Label.foreground"));

            FlatSVGIcon icon = ((FlatSVGIcon)(defaultPlaceholderIcon));
            icon.setColorFilter(colorFilter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Icon getIcon(Path path, Component associatedComponent) {
        Reference<Icon> iconRef;
        iconRef = thumbnailCache.get(path);
        Icon cachedIcon = iconRef != null ? iconRef.get() : null;

        if (cachedIcon != null) {
            scaleIcon(associatedComponent, cachedIcon);
            return cachedIcon;
        }

        if (loadingTasks.add(path)) {
            executor.submit(new LoadTask(associatedComponent, path));
        }

        return defaultPlaceholderIcon;
    }

    private void rescale(int[] widthHeight) {
        int width = widthHeight[0];
        int height = widthHeight[1];
        if (width > thumbnailSize || height > thumbnailSize) {
            if (width > height) {
                height = (height * thumbnailSize) / width;
                width = thumbnailSize;
            } else {
                width = (width * thumbnailSize) / height;
                height = thumbnailSize;
            }
        }
        widthHeight[0] = width;
        widthHeight[1] = height;
    }

    public void scaleIcon(Component associatedComponent, Icon cachedIcon) {
        if (cachedIcon instanceof ImageIcon imageIcon) {
            ImageObserver prevObserver = imageIcon.getImageObserver();
            if (!Objects.equals(prevObserver, associatedComponent)) {
                imageIcon.setImageObserver(associatedComponent);
            }
        } else if (cachedIcon instanceof ScaledImageIcon scaledImageIcon) {
            ImageObserver prevObserver = scaledImageIcon.getImageObserver();
            if (!Objects.equals(prevObserver, associatedComponent)) {
                scaledImageIcon.setImageObserver(associatedComponent);
            }
            int[] wh = new int[] { scaledImageIcon.getOriginalWidth(), scaledImageIcon.getOriginalHeight() };
            rescale(wh);
            scaledImageIcon.setWidth(wh[0]);
            scaledImageIcon.setHeight(wh[1]);
        } else if (cachedIcon instanceof GifImageWrapperIcon gifIcon) {
            ImageObserver prevObserver = gifIcon.getImageObserver();
            if (!Objects.equals(prevObserver, associatedComponent)) {
                gifIcon.setImageObserver(associatedComponent);
            }
            int[] wh = new int[] { gifIcon.getIconWidth(), gifIcon.getIconHeight() };
            rescale(wh);
            gifIcon.setScaledWidth(wh[0]);
            gifIcon.setScaledHeight(wh[1]);
        }
    }

    public int getThumbnailSize() {
        return thumbnailSize;
    }

    public void setThumbnailSize(int thumbnailSize) {
        this.thumbnailSize = thumbnailSize;
    }

    public int getMaxEntries() {
        return thumbnailCacheLRU.getMaxEntries();
    }

    public void setMaxEntries(int entries) {
        thumbnailCacheLRU.setMaxEntries(entries);
    }

    public long getMinimumRefreshTime() {
        long refreshTime = -1;
        Set<Reference<Icon>> icons;
        synchronized (thumbnailCache) {
            icons = new HashSet<>(thumbnailCache.values());
        }
        for (Reference<Icon> iconRef : icons) {
            Icon icon = iconRef != null ? iconRef.get() : null;
            if (icon instanceof GifImageWrapperIcon gifImageWrapperIcon) {
                if (refreshTime == -1) {
                    refreshTime = gifImageWrapperIcon.getMinDelayTime();
                } else {
                    refreshTime = Math.min(refreshTime, gifImageWrapperIcon.getMinDelayTime());
                }
            }
        }
        return refreshTime;
    }

    public long getMinimumRefreshTime(List<Path> paths) {
        long refreshTime = -1;
        Map<Path, Reference<Icon>> cacheCopy;
        synchronized (thumbnailCache) {
            cacheCopy = new HashMap<>(thumbnailCache);
        }

        Iterator<Map.Entry<Path, Reference<Icon>>> iterator = cacheCopy.entrySet().iterator();
        Set<Reference<Icon>> icons = new HashSet<>();
        while (iterator.hasNext()) {
            Map.Entry<Path, Reference<Icon>> entry = iterator.next();
            if (paths.contains(entry.getKey())) {
                icons.add(entry.getValue());
            }
        }

        for (Reference<Icon> iconRef : icons) {
            Icon icon = iconRef != null ? iconRef.get() : null;
            if (icon instanceof GifImageWrapperIcon gifImageWrapperIcon) {
                if (refreshTime == -1) {
                    refreshTime = gifImageWrapperIcon.getMinDelayTime();
                } else {
                    refreshTime = Math.min(refreshTime, gifImageWrapperIcon.getMinDelayTime());
                }
            }
        }
        return refreshTime;
    }

    Map<Path, Reference<Icon>> getIconCacheMap() {
        return thumbnailCache;
    }

    private Icon loadGif(Path path, String id) throws IOException {
        try (GifImageWrapperIconIndexedParser parser =
                     new GifImageWrapperIconIndexedDownsampledParser(2)) {
            GifImageWrapperIcon icon = GifImageReader.readImage(path, parser);
            //executor.submit(() -> icon.saveToCache(CACHE_DIR, id));
            return icon;
        }
        //return new ScaledImageIcon(new ImageIcon(path.toUri().toURL()), thumbnailSize, thumbnailSize);
        //return new ImageIcon(path.toUri().toURL());
    }

    private Icon loadCachedGif(String id) throws UncheckedIOException {
        return new GifImageWrapperIcon(CACHE_DIR, id);
    }

    private void onItemRemoved(Reference<Icon> iconRef) {
        Icon icon = iconRef.get();
        if (icon != null) {
            if (icon instanceof ImageIcon imageIcon) {
                Image image = imageIcon.getImage();
                if (image != null) {
                    image.flush();
                }
            } else if (icon instanceof GifImageWrapperIcon gifIcon) {
                gifIcon.flush();
            }
        }
    }

    private static String filenameToHash(String id) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException _) {
        }
        if (digest != null) {
            return HexFormat.of().formatHex(digest.digest(id.getBytes(StandardCharsets.UTF_8)));
        } else {
            return String.valueOf(id.hashCode());
        }
    }
    public static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".cache", "Tagged");

    private Icon loadImage(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null || !SUPPORTED_MIME_TYPES.contains(contentType)) {
                return null;
            }

            String id = filenameToHash(path.toString());

            Path cachePath = CACHE_DIR.resolve(id);
            if (path.getFileName().toString().toLowerCase().endsWith(".gif")) {
                // special handling with GIF files
//                try {
//                    if (Files.exists(cachePath)) {
//                        return loadCachedGif(id);
//                    }
//                } catch (UncheckedIOException e) {
//                    e.printStackTrace();
//                }
                return loadGif(path, id);
            }

            if (Files.exists(cachePath)) {
                try (InputStream stream = new BufferedInputStream(Files.newInputStream(cachePath))) {
                    BufferedImage fileCached = ImageIO.read(stream);
                    return new ScaledImageIcon(new ImageIcon(fileCached));
                }
            }

            BufferedImage original = ImageIO.read(path.toFile());
            if (original == null) {
                return null;
            }

            int width = original.getWidth();
            int height = original.getHeight();
            if (width > thumbnailSize || height > thumbnailSize) {
                if (width > height) {
                    height = (height * thumbnailSize) / width;
                    width = thumbnailSize;
                } else {
                    width = (width * thumbnailSize) / height;
                    height = thumbnailSize;
                }
            }

            BufferedImage thumbnail = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .createCompatibleImage(
                            width,
                            height,
                            Transparency.OPAQUE);
            Graphics2D g2d = thumbnail.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
            g2d.drawImage(original, 0, 0, width, height, null);
            g2d.dispose();

            original.flush();

            try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(cachePath))) {
                ImageIO.write(thumbnail, "JPG", outputStream);
            }

            return new ScaledImageIcon(new ImageIcon(thumbnail));

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    private class LoadTask implements Runnable {
        private final Component component;
        private final Path path;

        private LoadTask(Component component, Path path) {
            this.component = component;
            this.path = path;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                Icon thumbnail = loadImage(path);
                if (thumbnail != null) {
                    thumbnailCache.put(path, new SoftReference<>(thumbnail));
                    success = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                loadingTasks.remove(path);
            }

            if (success) {
                SwingUtilities.invokeLater(component::repaint);
            }
        }
    }
}
