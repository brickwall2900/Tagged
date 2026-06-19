package io.github.brickwall2900.tagged.icons;

import javax.swing.*;
import java.awt.image.ImageObserver;
import java.nio.file.Path;

public interface IconProvider {
    Icon getIcon(Path path, ImageObserver observer);
    boolean supports(Path path);
    default int priority() {
        return 0;
    }
}
