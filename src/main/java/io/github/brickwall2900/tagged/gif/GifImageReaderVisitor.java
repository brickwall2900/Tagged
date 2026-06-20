package io.github.brickwall2900.tagged.gif;

public interface GifImageReaderVisitor<R> {
    void onHeaderRead(HeaderVersion version);

    void onLogicalScreenDescriptorRead(
        int canvasWidth,
        int canvasHeight,
        boolean globalColorTablePresent,
        int globalColorTableSize,
        boolean sortFlag,
        byte backgroundColorIndex,
        byte pixelAspectRatio,
        int[] globalColorTable
    );

    void onImageBlockRead(
            int imageLeft,
            int imageTop,
            int imageWidth,
            int imageHeight,
            boolean localColorTablePresent,
            int localColorTableSize,
            boolean sortFlag,
            boolean interlace,
            byte minimumCodeSize,
            int[] localColorTable,
            byte[] subBlocks);

    void onGraphicsControlExtensionRead(
            byte disposalMethod,
            boolean userInput,
            boolean transparentColor,
            byte transparentColorIndex,
            short delayTime
    );

    void onApplicationExtensionRead(String id, String code, byte[] subBlocks);

    void onCommentExtensionRead(byte[] subBlocks);

    R getResult();

    enum HeaderVersion {
        GIF_87A, GIF_89A;
    }
}
