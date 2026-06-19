package io.github.brickwall2900.tagged.icons;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.ImageObserver;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.WeakHashMap;

public class ImageIconProvider implements IconProvider {
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(ImageIO.getReaderMIMETypes());

    // im so going to get fucking skinned alive for this
    private final WeakHashMap<Path, ImageIcon> iconHashMap = new WeakHashMap<>();

    @Override
    public Icon getIcon(Path path, ImageObserver observer) {
        ImageIcon icon = iconHashMap.get(path);
        if (icon != null) {
            icon.setImageObserver(observer);
            return icon;
        } else {
            try {
                icon = new ImageIcon(ImageCache.load(path));
                icon.setImageObserver(observer);
                iconHashMap.put(path, icon);
                return icon;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    public boolean supports(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return contentType != null && contentType.startsWith("image/") && SUPPORTED_MIME_TYPES.contains(contentType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int priority() {
        return 0;
    }

}
