package io.github.brickwall2900.tagged.gif;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

    public GifImageWrapperIcon(Path cacheDir, String id) {
        try (ZipFile zipFile = new ZipFile(cacheDir.resolve(id).toFile())) {
            ZipEntry metaFile = zipFile.getEntry("meta");
            if (metaFile == null) {
                throw new IOException("meta file not found");
            }

            Properties properties = new Properties();
            try (InputStream stream = zipFile.getInputStream(metaFile)) {
                properties.load(stream);
            }

            int count = Integer.parseInt(properties.getProperty("count"));
            images = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                ZipEntry entry = zipFile.getEntry(String.valueOf(i));
                try (InputStream stream = new BufferedInputStream(zipFile.getInputStream(entry))) {
                    String encoded = properties.getProperty(String.valueOf(i));
                    String[] split = encoded.split(",");
                    ImageFrame frame = new ImageFrame(
                            Objects.requireNonNull(ImageIO.read(stream),
                                    "heart attack while reading " + i + " in " + id),
                            Integer.parseInt(split[0]),
                            Integer.parseInt(split[1]),
                            Integer.parseInt(split[2]),
                            Integer.parseInt(split[3]),
                            Short.parseShort(split[4]),
                            Byte.parseByte(split[5]),
                            new Color(Integer.parseInt(split[6]))
                    );
                    images.add(frame);
                }
            }

            canvasWidth = Integer.parseInt(properties.getProperty("width"));
            canvasHeight = Integer.parseInt(properties.getProperty("height"));
            scaledWidth = Integer.parseInt(properties.getProperty("scaledWidth"));
            scaledHeight = Integer.parseInt(properties.getProperty("scaledHeight"));
            backgroundColorIndex = Integer.parseInt(properties.getProperty("backgroundColorIndex"));
            imageCount = Integer.parseInt(properties.getProperty("imageCount"));
            loopCount = Integer.parseInt(properties.getProperty("loopCount"));
            totalDurationMs = Integer.parseInt(properties.getProperty("totalDurationMs"));

            finishedDecoding();
            startTime = System.currentTimeMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
            double sampleSize,
            boolean fastTarget) {
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
                8,
                colorTable.length,
                colorTable,
                0,
                false,
                transparencyEnabled ? transparencyIndex : -1,
                DataBuffer.TYPE_BYTE
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

        VolatileImage fastTargetImage = null;
        if (fastTarget) {
            fastTargetImage = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .createCompatibleVolatileImage(
                            scaledFrameWidth,
                            scaledFrameHeight,
                            Transparency.BITMASK
                    );
            Graphics2D g2d = null;
            try {
                g2d = fastTargetImage.createGraphics();
                g2d.setComposite(AlphaComposite.Src);
                g2d.setColor(Color.BLACK);
                g2d.clearRect(0, 0, imageWidth, imageHeight);
                g2d.drawImage(target, 0, 0, null);
            } finally {
                if (g2d != null) {
                    g2d.dispose();
                }
            }
        }

        totalDurationMs += delayTime;
        images.add(new ImageFrame(
                fastTarget ? fastTargetImage : target,
                scaledLeft,
                scaledTop,
                scaledFrameWidth,
                scaledFrameHeight,
                delayTime,
                disposalMethod,
                new Color(colorTable[backgroundColorIndex])));
        imageCount++;
    }

    void finishedDecoding() {
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
    }

    public void saveToCache(Path cacheDir, String id) {
        try {
            Path mainFolder = cacheDir.resolve(id);
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(mainFolder))) {
                //zipOutputStream.setLevel(9);
                Properties properties = new Properties();
                for (int i = 0; i < images.size(); i++) {
                    ZipEntry entry = new ZipEntry(String.valueOf(i));
                    zipOutputStream.putNextEntry(entry);

                    ImageFrame image = images.get(i);
                    //ImageIO.write(image.image, "BMP", zipOutputStream);
                    properties.setProperty(String.valueOf(i), "%d,%d,%d,%d,%d,%d,%d".formatted(
                            image.left,
                            image.top,
                            image.width,
                            image.height,
                            image.delay,
                            image.disposalMethod,
                            image.backgroundColor.getRGB()
                    ));

                    zipOutputStream.closeEntry();
                }
                properties.setProperty("count", String.valueOf(images.size()));
                properties.setProperty("width", String.valueOf(canvasWidth));
                properties.setProperty("height", String.valueOf(canvasHeight));
                properties.setProperty("scaledWidth", String.valueOf(scaledWidth));
                properties.setProperty("scaledHeight", String.valueOf(scaledHeight));
                properties.setProperty("backgroundColorIndex", String.valueOf(backgroundColorIndex));
                properties.setProperty("imageCount", String.valueOf(imageCount));
                properties.setProperty("loopCount", String.valueOf(loopCount));
                properties.setProperty("totalDurationMs", String.valueOf(totalDurationMs));

                ZipEntry metaFile = new ZipEntry("meta");
                zipOutputStream.putNextEntry(metaFile);
                properties.store(zipOutputStream, null);
                zipOutputStream.closeEntry();

                zipOutputStream.finish();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

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
        GraphicsConfiguration graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();

        if (canvas.contentsLost()) {
            canvas.validate(graphicsConfiguration);
        }
        if (lastFrame.contentsLost()) {
            lastFrame.validate(graphicsConfiguration);
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

            if (nextFrame.image instanceof VolatileImage volatileImage && volatileImage.contentsLost()) {
                volatileImage.validate(graphicsConfiguration);
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
        canvas.flush();
        lastFrame.flush();
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
        final int bytesPerPixel = Math.ceilDiv(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getColorModel().getPixelSize(), 8);
        for (ImageFrame frame : images) {
            if (frame.image instanceof BufferedImage bufferedImage) {
                int type = bufferedImage.getType();
                usage += switch (type) {
                    case BufferedImage.TYPE_INT_ARGB -> frame.width * frame.height * 4;
                    case BufferedImage.TYPE_BYTE_INDEXED -> frame.width * frame.height;
                    // we don't know lol
                    default -> frame.width * frame.height;
                };
            } else {
                usage += (long) bytesPerPixel * frame.width * frame.height;
            }
        }
        if (canvas != null) {
            // estimation
            // assume ARGB lol
            usage += ((long) canvasWidth * canvasHeight * bytesPerPixel) * 2;
            // consider canvas and last frame canvas
        }
        return usage;
    }

    public long getCanvasMemoryUsage() {
        return getMemoryUsage();
    }

    private record ImageFrame(Image image,
                              int left,
                              int top,
                              int width,
                              int height,
                              short delay,
                              byte disposalMethod,
                              Color backgroundColor) {}
}
