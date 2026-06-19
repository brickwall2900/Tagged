package io.github.brickwall2900.tagged.icons;

import io.github.brickwall2900.tagged.gif.GifImageIcon;
import io.github.brickwall2900.tagged.gif.GifImageReader;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.WeakHashMap;

public class GifImageIconProvider implements IconProvider {
    // im so going to get fucking skinned alive for this
    private final WeakHashMap<Path, SoftReference<GifImageIcon>> iconHashMap = new WeakHashMap<>();

    @Override
    public Icon getIcon(Path path, ImageObserver observer) {
        SoftReference<GifImageIcon> icon = iconHashMap.get(path);
        if (icon != null) {
            GifImageIcon imageIcon = icon.get();
            if (imageIcon != null) {
                return imageIcon;
            }
        }

        try {
            GifImageIcon imageIcon = new GifImageIcon(GifImageReader.readImage(path, true));
            imageIcon.setImageObserver(observer);
            iconHashMap.put(path, new SoftReference<>(imageIcon));
            return imageIcon;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean supports(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return contentType != null && contentType.equalsIgnoreCase("image/gif");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int priority() {
        return 67;
    }
}
