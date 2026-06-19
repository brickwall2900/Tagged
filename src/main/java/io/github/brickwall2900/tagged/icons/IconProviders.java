package io.github.brickwall2900.tagged.icons;

import javax.swing.*;
import java.awt.image.ImageObserver;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.ServiceLoader;

public final class IconProviders {
    private static final IconProvider[] PROVIDERS;

    static {
        ServiceLoader<IconProvider> iconProviders = ServiceLoader.load(IconProvider.class);
        PROVIDERS = iconProviders.stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(IconProvider::priority).reversed())
                .toArray(IconProvider[]::new);
        System.out.println(Arrays.toString(PROVIDERS));
    }

    public static Icon getIcon(Path path, ImageObserver observer) {
        for (IconProvider provider : PROVIDERS) {
            if (!provider.supports(path)) {
                continue;
            }

            Icon icon = provider.getIcon(path, observer);
            if (icon == null) {
                continue;
            }

            return icon;
        }
        throw new UnsupportedOperationException("Icon for " + path + " could not be loaded");
    }
}
