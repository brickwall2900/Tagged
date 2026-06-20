package io.github.brickwall2900.tagged.gif;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.List;

public class GifImageWrapperIcon implements Icon {
    private final List<ImageFrame> images;
    private ImageObserver imageObserver;
    private int canvasWidth, canvasHeight;
    private int imageCount;
    private int backgroundColorIndex;
    private int scaledWidth, scaledHeight;

    private VolatileImage canvas;
    private VolatileImage lastFrame;
    private int lastRenderedIndex = -1;

    int loopCount;
    int minDelayTime = 0;
    long totalDurationMs;
    long startTime;

    GifImageWrapperIcon() {
        images = new ArrayList<>();
        startTime = System.currentTimeMillis();
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public void setCanvasWidth(int canvasWidth) {
        this.canvasWidth = canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    public void setCanvasHeight(int canvasHeight) {
        this.canvasHeight = canvasHeight;
    }

    public int getScaledWidth() {
        return scaledWidth;
    }

    public void setScaledWidth(int scaledWidth) {
        this.scaledWidth = scaledWidth;
    }

    public int getScaledHeight() {
        return scaledHeight;
    }

    public void setScaledHeight(int scaledHeight) {
        this.scaledHeight = scaledHeight;
    }

    public int getBackgroundColorIndex() {
        return backgroundColorIndex;
    }

    public void setBackgroundColorIndex(int backgroundColorIndex) {
        this.backgroundColorIndex = backgroundColorIndex;
    }

    void makeImage(int[] pixels,
                   int[] colorTable,
                   int imageLeft,
                   int imageTop,
                   int imageWidth,
                   int imageHeight,
                   short delayTime,
                   byte disposalMethod) {
        BufferedImage target = new BufferedImage(
                imageWidth,
                imageHeight,
                BufferedImage.TYPE_INT_ARGB);

        int[] targetPixels = ((DataBufferInt) (target.getRaster().getDataBuffer())).getData();
        System.arraycopy(pixels, 0, targetPixels, 0, targetPixels.length);

        delayTime *= 10; // delay time is 100ths of second; make that 1000ths of a second

        if (imageCount == 0) {
            minDelayTime = delayTime;
        } else {
            minDelayTime = Math.min(minDelayTime, delayTime);
        }

        totalDurationMs += delayTime;
        images.add(new ImageFrame(
                target,
                imageLeft,
                imageTop,
                imageWidth,
                imageHeight,
                delayTime,
                disposalMethod,
                new Color(colorTable[backgroundColorIndex])));
        imageCount++;
    }

    void makeImageIndexed(
            byte[] indices,
            int[] colorTable,
            int imageLeft,
            int imageTop,
            int imageWidth,
            int imageHeight,
            short delayTime,
            byte disposalMethod,
            boolean transparencyEnabled,
            int transparencyIndex,
            double sampleSize) {
        int scaledFrameWidth = (int) Math.max(1, imageWidth / sampleSize);
        int scaledFrameHeight = (int) Math.max(1, imageHeight / sampleSize);
        int scaledLeft = (int) (imageLeft / sampleSize);
        int scaledTop = (int) (imageTop / sampleSize);

        byte[] downsampledIndices = new byte[(scaledFrameWidth * scaledFrameHeight)];

        for (int y = 0; y < scaledFrameHeight; y++) {
            int oldY = (int) (y * sampleSize);
            for (int x = 0; x < scaledFrameWidth; x++) {
                int oldX = (int) (x * sampleSize);

                downsampledIndices[y * scaledFrameWidth + x] = indices[oldY * imageWidth + oldX];
            }
        }

        IndexColorModel icm = new IndexColorModel(
                8, colorTable.length, colorTable, 0, false,
                transparencyEnabled ? transparencyIndex : -1, DataBuffer.TYPE_BYTE
        );

        BufferedImage target = new BufferedImage(
                scaledFrameWidth,
                scaledFrameHeight,
                BufferedImage.TYPE_BYTE_INDEXED,
                icm);

        byte[] targetPixels = ((DataBufferByte) target.getRaster().getDataBuffer()).getData();
        System.arraycopy(downsampledIndices, 0, targetPixels, 0, targetPixels.length);

        delayTime *= 10; // delay time is 100ths of second; make that 1000ths of a second

        if (imageCount == 0) {
            minDelayTime = delayTime;
        } else {
            minDelayTime = Math.min(minDelayTime, delayTime);
        }

        totalDurationMs += delayTime;
        images.add(new ImageFrame(
                target,
                scaledLeft,
                scaledTop,
                scaledFrameWidth,
                scaledFrameHeight,
                delayTime,
                disposalMethod,
                new Color(colorTable[backgroundColorIndex])));
        imageCount++;
    }

    public int getCurrentFrameIndexNow() {
        if (imageCount == 0) {
            return -1;
        }

        if (imageCount == 1) {
            return 0; // first frame
        }

        if (totalDurationMs == 0) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - startTime;

        if (loopCount > 0) {
            long currentLoopCount = elapsed / totalDurationMs;
            if (currentLoopCount >= loopCount) {
                return imageCount - 1;
            }
        }

        long timeInCurrentLoop = elapsed % totalDurationMs;
        long accumulatedDelay = 0;

        for (int i = 0; i < imageCount; i++) {
            ImageFrame frame = images.get(i);
            accumulatedDelay += frame.delay;
            if (timeInCurrentLoop < accumulatedDelay) {
                return i;
            }
        }

        return -1; // man idfk
    }

    public int getMinDelayTime() {
        return minDelayTime;
    }

    private void drawFrames(int targetIndex) {
        if (canvas == null) {
            canvas = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .createCompatibleVolatileImage(canvasWidth, canvasHeight, Transparency.BITMASK);
            lastFrame = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .createCompatibleVolatileImage(canvasWidth, canvasHeight, Transparency.BITMASK);
        }

        // if looped then fuck everything
        if (targetIndex < lastRenderedIndex || lastRenderedIndex == -1) {
            Graphics2D g2d = canvas.createGraphics();
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, canvasWidth, canvasHeight);
            g2d.dispose();
            lastRenderedIndex = -1;
        }

        // re-render old frames from 0 to targetIndex
        while (lastRenderedIndex < targetIndex) {
            int nextIndex = lastRenderedIndex + 1;
            ImageFrame nextFrame = images.get(nextIndex);

            if (lastRenderedIndex != -1) {
                ImageFrame oldFrame = images.get(lastRenderedIndex);
                // handle disposal method
                if (oldFrame.disposalMethod == 2) {
                    // clear background
                    Graphics2D g2d = canvas.createGraphics();
                    g2d.setComposite(AlphaComposite.Clear);
                    g2d.fillRect(oldFrame.left, oldFrame.top, oldFrame.width, oldFrame.height);
                    g2d.dispose();
                } else if (oldFrame.disposalMethod == 3) {
                    // clear to previous frame
                    Graphics2D g2d = canvas.createGraphics();
                    g2d.setComposite(AlphaComposite.Src);
                    g2d.drawImage(lastFrame, 0, 0, null);
                    g2d.dispose();
                }
            }

            // draw last frame before drawing this frame
            if (nextFrame.disposalMethod == 3) {
                Graphics2D g2d = lastFrame.createGraphics();
                g2d.setComposite(AlphaComposite.Src);
                g2d.drawImage(canvas, 0, 0, null);
                g2d.dispose();
            }

            // draw this frame
            Graphics2D g2d = canvas.createGraphics();
            g2d.drawImage(nextFrame.image, nextFrame.left, nextFrame.top, null);
            g2d.dispose();

            lastRenderedIndex = nextIndex;
        }
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        ImageObserver observer = imageObserver != null ? imageObserver : c;

        int frameIndex = getCurrentFrameIndexNow();
        if (frameIndex != -1) {
            drawFrames(frameIndex);
            //g.drawImage(canvas, x, y, observer);

            Graphics2D g2d = (Graphics2D) g.create();

            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);

            double scaleX = (double) scaledWidth / canvasWidth;
            double scaleY = (double) scaledHeight / canvasHeight;

            g2d.translate(x, y);
            g2d.scale(scaleX, scaleY);

            g2d.drawImage(canvas, 0, 0, observer);

            g2d.dispose();
        }
    }

    public void flush() {
        for (ImageFrame frame : images) {
            frame.image.flush();
        }
    }

    @Override
    public int getIconWidth() {
        return scaledWidth;
    }

    @Override
    public int getIconHeight() {
        return scaledHeight;
    }

    public ImageObserver getImageObserver() {
        return imageObserver;
    }

    public void setImageObserver(ImageObserver imageObserver) {
        this.imageObserver = imageObserver;
    }

    public long getMemoryUsage() {
        long usage = 0;
        for (ImageFrame frame : images) {
            byte[] targetPixels = ((DataBufferByte) frame.image.getRaster().getDataBuffer()).getData();
            usage += targetPixels.length;
        }
        if (canvas != null) {
            // estimation
            usage += ((long) canvasWidth * canvasHeight * 4) * 2;
        }
        return usage;
    }

    public long getCanvasMemoryUsage() {
        long usage = 0;
        if (canvas != null) {
            // estimation
            usage += ((long) canvasWidth * canvasHeight * 4) * 2;
        }
        return usage;
    }

    private record ImageFrame(BufferedImage image,
                              int left,
                              int top,
                              int width,
                              int height,
                              short delay,
                              byte disposalMethod,
                              Color backgroundColor) {}
}
