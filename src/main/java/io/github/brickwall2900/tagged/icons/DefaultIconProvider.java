package io.github.brickwall2900.tagged.icons;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.image.ImageObserver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class DefaultIconProvider implements IconProvider {
    public static final String SVGFILE = """
            <svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24">
            	<path d="M0 0h24v24H0z" fill="none" />
            	<path fill="currentColor" d="M13 9V3.5L18.5 9M6 2c-1.11 0-2 .89-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z" />
            </svg>""";

    public static final Icon ICON;
    public static final FlatSVGIcon.ColorFilter COLOR_FILTER;

    static {
        try {
            ICON = new FlatSVGIcon(new ByteArrayInputStream(SVGFILE.getBytes(StandardCharsets.UTF_8)));
            COLOR_FILTER = new FlatSVGIcon.ColorFilter();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Icon getIcon(Path path, ImageObserver observer) {
        COLOR_FILTER.setMapper(_ -> UIManager.getColor("Label.foreground"));
        FlatSVGIcon icon = ((FlatSVGIcon)(ICON));
        icon.setColorFilter(COLOR_FILTER);
        icon.setImageObserver(observer);
        return ICON;
    }

    @Override
    public boolean supports(Path path) {
        return true;
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }
}
