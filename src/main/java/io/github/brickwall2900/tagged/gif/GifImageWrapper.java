package io.github.brickwall2900.tagged.gif;

import io.github.brickwall2900.tagged.icons.ImageCache;

import java.awt.*;
import java.awt.image.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class GifImageWrapper {
    private static final int LOW_RES = 200;
    private final BufferedImage[] images;
    private final int[] delayTime;
    private final int loopCount;
    private final int minDelayTime;

    private int timesLooped;
    private long lastUpdate;
    private int frame;

    GifImageWrapper(String gifName, List<GifImageReader.ParsedImage> images, int loopCount, boolean lowResolution) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("no images");
        }

        final int imageCount = images.size();
        this.loopCount = loopCount;
        this.images = new BufferedImage[imageCount];
        this.delayTime = new int[imageCount];
        this.lastUpdate = System.currentTimeMillis();
        this.frame = 0;

        int minDelayTime = Integer.MAX_VALUE;
        for (int i = 0; i < imageCount; i++) {
            BufferedImage previous = i > 0 ? this.images[i - 1] : null;
            GifImageReader.ParsedImage image = images.get(i);
            this.images[i] = makeImage(gifName, i, previous, image, lowResolution);
            this.delayTime[i] = image.delayTime();
            minDelayTime = Math.min(this.delayTime[i], minDelayTime);
        }
        this.minDelayTime = minDelayTime;
    }
    private BufferedImage makeImage(String gifName, int i, BufferedImage previous, GifImageReader.ParsedImage parsedImage, boolean lowRes) {
        final int x = parsedImage.left();
        final int y = parsedImage.top();
        final int width = parsedImage.width();
        final int height = parsedImage.height();
        BufferedImage source = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB);
        int[] sourcePixels = ((DataBufferInt) (source.getRaster().getDataBuffer())).getData();
        System.arraycopy(parsedImage.imagePixels(), 0, sourcePixels, 0, sourcePixels.length);

        BufferedImage target = null;
        try {
            target = lowRes ? ImageCache.load(gifName + "::" + i, source)
                    : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .createCompatibleImage(
                            width,
                            height,
                            Transparency.BITMASK);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ;

        Graphics2D g2d = target.createGraphics();
        try {
            if (previous != null) {
                g2d.drawImage(previous, null, 0, 0);
            }
            g2d.drawImage(source, x, y, target.getWidth(), target.getHeight(), null);
        } finally {
            g2d.dispose();
        }

        source.flush();

        return target;
    }

    public BufferedImage getCurrentFrameNow() {
        if (loopCount == 0 || loopCount > timesLooped) {
            long timePassed = System.currentTimeMillis() - lastUpdate;

            do {
                int delay = delayTime[frame++];
                timePassed -= delay;

                // one frame consumed
                if (frame <= images.length) {
                    // count as one loop
                    frame = 0;
                    timesLooped++;
                    break;
                }
            } while (timePassed > 0);
        }
        return images[frame];
    }

    public int minDelayTime() {
        return minDelayTime;
    }

    public void unload() {
        for (BufferedImage img : images) {
            img.flush();
        }
        Arrays.fill(images, null);
    }
}
