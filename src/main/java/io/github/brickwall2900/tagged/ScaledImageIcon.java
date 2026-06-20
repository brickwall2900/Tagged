package io.github.brickwall2900.tagged;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.image.ImageObserver;
import java.beans.BeanProperty;
import java.beans.Transient;

public class ScaledImageIcon implements Icon {
    private ImageIcon originalIcon;
    private int width, height;

    public ScaledImageIcon(ImageIcon originalIcon) {
        this.originalIcon = originalIcon;
    }

    public ScaledImageIcon(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public ScaledImageIcon(ImageIcon originalIcon, int width, int height) {
        this.originalIcon = originalIcon;
        this.width = width;
        this.height = height;
    }

    public ImageIcon getOriginalIcon() {
        return originalIcon;
    }

    public void setOriginalIcon(ImageIcon originalIcon) {
        this.originalIcon = originalIcon;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getOriginalWidth() {
        return originalIcon != null ? originalIcon.getIconWidth() : 0;
    }

    public int getOriginalHeight() {
        return originalIcon != null ? originalIcon.getIconHeight() : 0;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        final Image image = originalIcon != null ? originalIcon.getImage() : null;
        final ImageObserver imageObserver = originalIcon != null ? originalIcon.getImageObserver() : null;
        if (image == null) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double scaleX = (double) width / originalIcon.getIconWidth();
        double scaleY = (double) height / originalIcon.getIconHeight();

        g2d.translate(x, y);
        g2d.scale(scaleX, scaleY);

        if (imageObserver != null) {
            g2d.drawImage(image, 0, 0, imageObserver);
        } else {
            g2d.drawImage(image, 0, 0, c);
        }

        g2d.dispose();
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }

    public int getImageLoadStatus() {
        return originalIcon.getImageLoadStatus();
    }

    public String getDescription() {
        return originalIcon.getDescription();
    }

    @Transient
    public Image getImage() {
        return originalIcon.getImage();
    }

    public void setImageObserver(ImageObserver observer) {
        originalIcon.setImageObserver(observer);
    }

    @BeanProperty(expert = true, description = "The AccessibleContext associated with this ImageIcon.")
    public AccessibleContext getAccessibleContext() {
        return originalIcon.getAccessibleContext();
    }

    public void setDescription(String description) {
        originalIcon.setDescription(description);
    }

    @Transient
    public ImageObserver getImageObserver() {
        return originalIcon.getImageObserver();
    }

    public void setImage(Image image) {
        originalIcon.setImage(image);
    }

    @Override
    public String toString() {
        return "ScaledImageIcon{" +
                "originalIcon=" + originalIcon +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}
