package io.github.brickwall2900.tagged.gif;

// SON WHAT IS THIS
public class GifImageWrapperIconIndexedAutoDownsamplerParser extends GifImageWrapperIconIndexedParser {
    private final int imageSize;

    public GifImageWrapperIconIndexedAutoDownsamplerParser(int imageSize) {
        this.imageSize = imageSize;
    }

    @Override
    public void decodeImageIndexed(byte[] indices,
                                  int[] colorTable,
                                  int imageLeft,
                                  int imageTop,
                                  int imageWidth,
                                  int imageHeight) {
        double ratio = (double) canvasWidth / imageSize;
        ratio = Math.max(ratio, (double) canvasHeight / imageSize);

        wrapper.loopCount = timesToLoop;
        wrapper.makeImageIndexed(
                indices,
                colorTable,
                imageLeft,
                imageTop,
                imageWidth,
                imageHeight,
                delayTime,
                disposalMethod,
                transparencyEnabled,
                transparencyIndex,
                ratio
        );
    }

    @Override
    public void onLogicalScreenDescriptorRead(int canvasWidth,
                                              int canvasHeight,
                                              boolean globalColorTablePresent,
                                              int globalColorTableSize,
                                              boolean sortFlag,
                                              byte backgroundColorIndex,
                                              byte pixelAspectRatio,
                                              int[] globalColorTable) {
        super.onLogicalScreenDescriptorRead(
                canvasWidth,
                canvasHeight,
                globalColorTablePresent,
                globalColorTableSize,
                sortFlag,
                backgroundColorIndex,
                pixelAspectRatio,
                globalColorTable
        );

        double ratio = (double) canvasWidth / imageSize;
        ratio = Math.max(ratio, (double) canvasHeight / imageSize);
        int scaledFrameWidth = (int) Math.max(1, canvasWidth / ratio);
        int scaledFrameHeight = (int) Math.max(1, canvasHeight / ratio);

        wrapper.setBackgroundColorIndex(backgroundColorIndex & 0xFF);
        wrapper.setCanvasWidth(scaledFrameWidth);
        wrapper.setScaledWidth(scaledFrameWidth);
        wrapper.setCanvasHeight(scaledFrameHeight);
        wrapper.setScaledHeight(scaledFrameHeight);
    }
}
