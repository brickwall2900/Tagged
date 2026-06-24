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
import java.awt.image.RenderedImage;
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
import java.util.concurrent.atomic.AtomicLong;

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
    private int thumbnailPadding = 4;
    private int thumbnailSize = 32;
    private boolean fastTarget = true;

    public TaggedHelper helper;

    private final AtomicLong loadId = new AtomicLong();
    private final LRUCache<Path, Reference<Icon>> thumbnailCache = new LRUCache<>(30, this::onItemRemoved);
    private final Set<Path> loadingTasks = ConcurrentHashMap.newKeySet();

    public IconManager(TaggedHelper helper) {
        this.helper = helper;
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
        //Timestamped t = new Timestamped();
        //t.push("thumbnailCacheGet");
        Reference<Icon> iconRef;
        iconRef = thumbnailCache.get(path);
        Icon cachedIcon = iconRef != null ? iconRef.get() : null;

        if (cachedIcon != null) {
            //t.reportPopAndPush("scaleIcon");
            scaleIcon(associatedComponent, cachedIcon);
            //t.reportAndPop();
            return cachedIcon;
        }

        //t.reportPopAndPush("submit");
        try {
            if (loadingTasks.add(path)) {
                helper.getExecutor().submit(new LoadTask(loadId.getAndIncrement(), associatedComponent, path));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //t.reportAndPop();

        return defaultPlaceholderIcon;
    }

    private void rescale(int[] widthHeight) {
        final int shownThumbnailSize = getShownThumbnailSize();
        int width = widthHeight[0];
        int height = widthHeight[1];
        //if (width > thumbnailSize || height > thumbnailSize) {
            if (width > height) {
                height = (height * shownThumbnailSize) / width;
                width = shownThumbnailSize;
            } else {
                width = (width * shownThumbnailSize) / height;
                height = shownThumbnailSize;
            }
        //}
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
            gifIcon.setPostScaleWidth(wh[0]);
            gifIcon.setPostScaleHeight(wh[1]);
        }
    }

    public int getThumbnailSize() {
        return thumbnailSize;
    }

    public void setThumbnailSize(int thumbnailSize) {
        this.thumbnailSize = thumbnailSize;
    }

    public int getShownThumbnailSize() {
        return Math.max((Math.ceilDiv(thumbnailSize, 64) * 64) - thumbnailPadding, thumbnailPadding);
    }

    public int getThumbnailPadding() {
        return thumbnailPadding;
    }

    public void setThumbnailPadding(int thumbnailPadding) {
        this.thumbnailPadding = thumbnailPadding;
    }

    public int getMaxEntries() {
        return thumbnailCache.getMaxEntries();
    }

    public void setMaxEntries(int entries) {
        thumbnailCache.setMaxEntries(entries);
    }

    public boolean isFastTargetEnabled() {
        return fastTarget;
    }

    public void setFastTargetEnabled(boolean fastTarget) {
        this.fastTarget = fastTarget;
    }

    // technically, this function isn't being used
    // it depends on Map#values
    // by commenting it i don't have to implement it anymore
    /*public long getMinimumRefreshTime() {
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
    }*/

    public long getMinimumRefreshTime(List<Path> paths) {
        long refreshTime = -1;

        for (Path path : paths) {
            Reference<Icon> iconRef = thumbnailCache.get(path);
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

    LRUCache<Path, Reference<Icon>> getIconCacheMap() {
        return thumbnailCache;
    }

    private Icon loadGif(Path path, String id) throws IOException {
        try (GifImageWrapperIconIndexedParser parser =
                     new GifImageWrapperFastIconIndexedAutoDownsamplerParser(Math.min(getShownThumbnailSize(), 64), fastTarget)) {
            GifImageWrapperIcon icon = GifImageReader.readImage(path, parser);
            //executor.submit(() -> icon.saveToCache(CACHE_DIR, id));
            return icon;
        }
        //return new ScaledImageIcon(new ImageIcon(path.toUri().toURL()), thumbnailSize, thumbnailSize);
        //return new ImageIcon(path.toUri().toURL());
    }

    // never used
    // i don't know how to make cached images eco-friendly small
    private Icon loadCachedGif(String id) throws UncheckedIOException {
        return new GifImageWrapperIcon(CACHE_DIR, id);
    }

    private void onItemRemoved(Reference<Icon> iconRef) {
        helper.getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                Icon icon = iconRef.get();
                if (icon != null) {
                    switch (icon) {
                        case ImageIcon imageIcon -> {
                            Image image = imageIcon.getImage();
                            if (image != null) {
                                image.flush();
                            }
                        }
                        case GifImageWrapperIcon gifIcon -> gifIcon.flush();
                        case ScaledImageIcon scaledImageIcon -> {
                            Image image = scaledImageIcon.getImage();
                            if (image != null) {
                                image.flush();
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
        });
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

            final int shownThumbnailSize = getShownThumbnailSize();

            String id = filenameToHash(path.toString() + shownThumbnailSize);

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
            if (width > shownThumbnailSize || height > shownThumbnailSize) {
                if (width > height) {
                    height = (height * shownThumbnailSize) / width;
                    width = shownThumbnailSize;
                } else {
                    width = (width * shownThumbnailSize) / height;
                    height = shownThumbnailSize;
                }
            }

            BufferedImage thumbnail;
            if (fastTarget) {
                thumbnail = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice()
                        .getDefaultConfiguration()
                        .createCompatibleImage(
                                width,
                                height,
                                Transparency.OPAQUE);
            } else {
                thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
            }
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
    }

    private class LoadTask implements Runnable {
        private final long id;
        private final Component component;
        private final Path path;

        private LoadTask(long id, Component component, Path path) {
            this.id = id;
            this.component = component;
            this.path = path;
        }

        @Override
        public void run() {
            //Timestamped t = new Timestamped();
            boolean success = false;
            try {
                //t.push("run");
                Icon thumbnail = loadImage(path);
                //t.reportPopAndPush("put");
                if (thumbnail != null) {
                    thumbnailCache.put(path, new SoftReference<>(thumbnail), id);
                    success = true;
                }
                //t.reportAndPop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //t.reportAndPopAll();
                loadingTasks.remove(path);
            }

            //t.push("invokeLaterRepaint");
            if (success) {
                SwingUtilities.invokeLater(component::repaint);
            }
            //t.reportAndPop();
        }
    }
}
