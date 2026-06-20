package io.github.brickwall2900.tagged;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Cleaner;

public class AutoImageFlusherImageIcon implements Icon {
    private static final Cleaner CLEANER = Cleaner.create();
    private Icon delegate;

    public AutoImageFlusherImageIcon(Icon delegate) {
        this();
        this.delegate = delegate;
    }

    public AutoImageFlusherImageIcon() {
        CLEANER.register(this, new CleanAction());
    }

    private class CleanAction implements Runnable {
        @Override
        public void run() {
            if (delegate != null) {
                if (delegate instanceof ImageIcon imageIcon) {
                    Image image = imageIcon.getImage();
                    if (image != null) {
                        image.flush();
                    }
                } else if (delegate instanceof ScaledImageIcon scaledImageIcon) {
                    Image image = scaledImageIcon.getImage();
                    if (image != null) {
                        image.flush();
                    }
                }
            }
        }
    }

    public Icon getDelegate() {
        return delegate;
    }

    public void setDelegate(ImageIcon delegate) {
        this.delegate = delegate;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        delegate.paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
        return delegate.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return delegate.getIconHeight();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
