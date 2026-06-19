package io.github.brickwall2900.tagged.icons;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class ImageCache {
    private static final LRUCache<String, BufferedImage> CACHE = new LRUCache<>(100);
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".cache", "Tagged");

    static {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage scaleImageWithAspectRatio(Image originalImage, int maxWidth, int maxHeight) {
        int originalWidth = originalImage.getWidth(null);
        int originalHeight = originalImage.getHeight(null);

        // Calculate the scaling factor while maintaining aspect ratio
        double widthRatio = (double) maxWidth / originalWidth;
        double heightRatio = (double) maxHeight / originalHeight;
        double scalingFactor = Math.min(widthRatio, heightRatio);

        // Calculate new dimensions
        int newWidth = (int) (originalWidth * scalingFactor);
        int newHeight = (int) (originalHeight * scalingFactor);

        // Create a new buffered image with the new dimensions
        BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .createCompatibleImage(
                        newWidth,
                        newHeight,
                        Transparency.BITMASK);
        Graphics2D g2d = image.createGraphics();
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        return image;
    }


    private static String filenameToHash(String id) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
        }
        if (digest != null) {
            return HexFormat.of().formatHex(digest.digest(id.getBytes(StandardCharsets.UTF_8)));
        } else {
            return String.valueOf(id.hashCode());
        }
    }

    public static Image load(Path path) throws IOException {
        String id = filenameToHash(path.toString());
        if (CACHE.containsKey(id)) {
            return CACHE.get(id);
        }

        Path cachePath = CACHE_DIR.resolve(id);
        if (Files.exists(cachePath)) {
            try (InputStream stream = new BufferedInputStream(Files.newInputStream(cachePath))) {
                BufferedImage i = ImageIO.read(stream);
                CACHE.put(id, i);
                return i;
            }
        }

        try (InputStream stream = new BufferedInputStream(Files.newInputStream(path));
             OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(cachePath))) {
            Image image = ImageIO.read(stream);
            BufferedImage rendered = scaleImageWithAspectRatio(image, 300, 300);
            ImageIO.write(rendered, "JPG", outputStream);
            CACHE.put(id, rendered);
            return rendered;
        }
    }

    public static BufferedImage load(String id, Image image) throws IOException {
        String cid = filenameToHash(id);
        if (CACHE.containsKey(cid)) {
            return CACHE.get(cid);
        }

        Path cachePath = CACHE_DIR.resolve(cid);
        if (Files.exists(cachePath)) {
            try (InputStream stream = new BufferedInputStream(Files.newInputStream(cachePath))) {
                BufferedImage i = ImageIO.read(stream);
                CACHE.put(cid, i);
                return i;
            }
        }

        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(cachePath))) {
            BufferedImage rendered = scaleImageWithAspectRatio(image, 300, 300);
            ImageIO.write(rendered, "JPG", outputStream);
            CACHE.put(cid, rendered);
            return rendered;
        }
    }
}
