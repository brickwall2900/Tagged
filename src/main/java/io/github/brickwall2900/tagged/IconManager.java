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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
            Runtime.getRuntime().availableProcessors() / 2);

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
        Reference<Icon> iconRef = thumbnailCache.get(path);
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
            int width = scaledImageIcon.getOriginalWidth();
            int height = scaledImageIcon.getOriginalHeight();
            if (width > thumbnailSize || height > thumbnailSize) {
                if (width > height) {
                    height = (height * thumbnailSize) / width;
                    width = thumbnailSize;
                } else {
                    width = (width * thumbnailSize) / height;
                    height = thumbnailSize;
                }
            }
            scaledImageIcon.setWidth(width);
            scaledImageIcon.setHeight(height);
        } else if (cachedIcon instanceof GifImageWrapperIcon gifIcon) {
            ImageObserver prevObserver = gifIcon.getImageObserver();
            if (!Objects.equals(prevObserver, associatedComponent)) {
                gifIcon.setImageObserver(associatedComponent);
            }
            int width = gifIcon.getCanvasWidth();
            int height = gifIcon.getCanvasHeight();
            if (width > thumbnailSize || height > thumbnailSize) {
                if (width > height) {
                    height = (height * thumbnailSize) / width;
                    width = thumbnailSize;
                } else {
                    width = (width * thumbnailSize) / height;
                    height = thumbnailSize;
                }
            }
            gifIcon.setScaledWidth(width);
            gifIcon.setScaledHeight(height);
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

    Map<Path, Reference<Icon>> getIconCacheMap() {
        return thumbnailCache;
    }

    private Icon loadGif(Path path) throws IOException {
        try (GifImageWrapperIconIndexedParser parser = new GifImageWrapperIconIndexedParser()) {
            return GifImageReader.readImage(path, parser);
        }
        //return new ScaledImageIcon(new ImageIcon(path.toUri().toURL()), thumbnailSize, thumbnailSize);
        //return new ImageIcon(path.toUri().toURL());
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

    private Icon loadImage(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null || !SUPPORTED_MIME_TYPES.contains(contentType)) {
                return null;
            }

            if (path.getFileName().toString().toLowerCase().endsWith(".gif")) {
                // special handling with GIF files
                return loadGif(path);
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
            Graphics2D g2 = thumbnail.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(original, 0, 0, width, height, null);
            g2.dispose();

            original.flush();

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
