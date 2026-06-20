package io.github.brickwall2900.tagged.gif;

import java.util.Arrays;

public abstract class GifImagePixelIndexedParser<R> extends GifImageParser<R> {
    public static final int[][] INTERLACE_ORDER = new int[][]{
            new int[]{0, 8},
            new int[]{4, 8},
            new int[]{2, 4},
            new int[]{1, 2}
    };

    public abstract void decodeImageIndexed(
            byte[] indices,
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
        byte[] finalIndices;

        final int backgroundColorIndex = this.backgroundColorIndex & 0xFF;
        final int bgColor = (globalColorTable != null && backgroundColorIndex < globalColorTable.length)
                ? globalColorTable[backgroundColorIndex] : 0;

        final int[] colorTableCopyTest = Arrays.copyOf(colorTable, colorTable.length);
        for (int i = 0; i < colorTable.length; i++) {
            if (i == backgroundColorIndex) {
                colorTableCopyTest[i] = bgColor;
            }
        }

        if (interlace) {
            finalIndices = new byte[indices.length];
            int srcIndex = 0;
            for (int[] pass : INTERLACE_ORDER) {
                int startRow = pass[0];
                int rowStep = pass[1];
                for (int y = startRow; y < imageHeight; y += rowStep) {
                    int dstRowOffset = y * imageWidth;
                    for (int x = 0; x < imageWidth; x++) {
                        finalIndices[dstRowOffset + x] = indices[srcIndex++];
                    }
                }
            }
        } else {
            finalIndices = indices;
        }

        decodeImageIndexed(
                finalIndices,
                colorTableCopyTest,
                imageLeft,
                imageTop,
                imageWidth,
                imageHeight
        );
    }
}
