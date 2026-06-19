package io.github.brickwall2900.tagged.gif;

import javax.swing.*;
import java.awt.*;

public class GifImageIcon extends ImageIcon {
    private GifImageWrapper imageWrapper;

    public GifImageIcon(GifImageWrapper imageWrapper) {
        this.imageWrapper = imageWrapper;
    }

    public GifImageWrapper getImageWrapper() {
        return imageWrapper;
    }

    public void setImageWrapper(GifImageWrapper imageWrapper) {
        this.imageWrapper = imageWrapper;
    }

    @Override
    public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        if (imageWrapper != null) {
            setImage(imageWrapper.getCurrentFrameNow());
        }
        super.paintIcon(c, g, x, y);
    }
}
