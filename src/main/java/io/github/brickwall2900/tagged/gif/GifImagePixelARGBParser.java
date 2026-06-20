package io.github.brickwall2900.tagged.gif;

public abstract class GifImagePixelARGBParser<R> extends GifImageParser<R> {
    public static final int[][] INTERLACE_ORDER = new int[][]{
            new int[]{0, 8},
            new int[]{4, 8},
            new int[]{2, 4},
            new int[]{1, 2}
    };

    public abstract void decodeImagePixels(
            int[] pixels,
            int[] colorTable,
            int imageLeft,
            int imageTop,
            int imageWidth,
            int imageHeight
    );

    @Override
    public void decodeImage(byte[] indices,
                            boolean interlace,
                            int[] colorTable,
                            int imageLeft,
                            int imageTop,
                            int imageWidth,
                            int imageHeight) {
        final int[] pixels = new int[indices.length];
        if (!interlace) {
            readPixels(
                    indices,
                    pixels,
                    colorTable,
                    globalColorTable,
                    transparencyEnabled,
                    transparencyIndex
            );
        } else {
            readPixelsInterlaced(
                    indices,
                    pixels,
                    imageWidth,
                    imageHeight,
                    colorTable,
                    globalColorTable,
                    transparencyEnabled,
                    transparencyIndex
            );
        }
        decodeImagePixels(
                pixels,
                colorTable,
                imageLeft,
                imageTop,
                imageWidth,
                imageHeight
        );
    }

    private void readPixels(
            final byte[] indices,
            final int[] pixels,
            final int[] localColorTable,
            final int[] globalColorTable,
            final boolean transparencyEnabled,
            final int transparencyIndex
    ) {
        final byte[] src = indices;
        final int[] dst = pixels;
        final int length = indices.length;

        final int tableLen = localColorTable.length;
        final int backgroundColorIndex = this.backgroundColorIndex & 0xFF;
        final int bgColor = (globalColorTable != null && backgroundColorIndex < globalColorTable.length)
                ? globalColorTable[backgroundColorIndex] : 0;

        int dstIndex = 0;

        if (transparencyEnabled) {
            final int iTransparencyIndex = transparencyIndex & 0xFF;
            for (int i = 0; i < length; i++) {
                final int index = src[i] & 0xFF;

                final int color = (index == iTransparencyIndex) ? 0 :
                        (index < tableLen) ? localColorTable[index] : bgColor;

                dst[dstIndex++] = color;
            }
        } else {
            for (int i = 0; i < length; i++) {
                final int idx = src[i] & 0xFF;
                final int color = (idx < tableLen) ? localColorTable[idx] : bgColor;
                dst[dstIndex++] = color;
            }
        }
    }

    private void readPixelsInterlaced(
            final byte[] indices,
            final int[] pixels,
            final int imageWidth,
            final int imageHeight,
            final int[] localColorTable,
            final int[] globalColorTable,
            final boolean transparencyEnabled,
            final int transparencyIndex
    ) {
        final byte[] src = indices;
        final int[] dst = pixels;

        final int width = imageWidth;
        final int height = imageHeight;
        final int tableLen = localColorTable.length;
        final int backgroundColorIndex = this.backgroundColorIndex & 0xFF;
        final int bgColor = (globalColorTable != null && backgroundColorIndex < globalColorTable.length)
                ? globalColorTable[backgroundColorIndex] : 0;

        int srcIndex = 0;

        if (transparencyEnabled) {
            final int iTransparencyIndex = transparencyIndex & 0xFF;
            for (int[] pass : INTERLACE_ORDER) {
                final int startRow = pass[0];
                final int rowStep = pass[1];

                for (int y = startRow; y < height; y += rowStep) {
                    final int dstRowOffset = y * width;
                    for (int x = 0; x < width; x++) {
                        final int idx = src[srcIndex++] & 0xFF;
                        final int color = (idx == iTransparencyIndex) ? 0 :
                                (idx < tableLen) ? localColorTable[idx] : bgColor;

                        final int dstIdx = dstRowOffset + x;
                        dst[dstIdx] = color;
                    }
                }
            }
        } else {
            for (int[] pass : INTERLACE_ORDER) {
                final int startRow = pass[0];
                final int rowStep = pass[1];

                for (int y = startRow; y < height; y += rowStep) {
                    final int dstRowOffset = y * width;
                    for (int x = 0; x < width; x++) {
                        final int idx = src[srcIndex++] & 0xFF;
                        final int color = (idx < tableLen) ? localColorTable[idx] : bgColor;

                        final int dstIdx = dstRowOffset + x;
                        dst[dstIdx] = color;
                    }
                }
            }
        }
    }
}
