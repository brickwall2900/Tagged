package io.github.brickwall2900.tagged.gif;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

// what in the actual fuck is this name
// AbstractEmployeeJobSwitchIndexedReversedParserFactoryBuilderWrapper ahh type shit
public class GifImageWrapperIconIndexedDownsampledParser extends GifImageWrapperIconIndexedParser {
    private final double sampleSize;

    public GifImageWrapperIconIndexedDownsampledParser(double sampleSize) {
        this.sampleSize = sampleSize;
    }

    @Override
    public void decodeImageIndexed(byte[] indices,
                                  int[] colorTable,
                                  int imageLeft,
                                  int imageTop,
                                  int imageWidth,
                                  int imageHeight) {
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
                sampleSize
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

        int scaledFrameWidth = (int) Math.max(1, canvasWidth / sampleSize);
        int scaledFrameHeight = (int) Math.max(1, canvasHeight / sampleSize);

        wrapper.setBackgroundColorIndex(backgroundColorIndex & 0xFF);
        wrapper.setCanvasWidth(scaledFrameWidth);
        wrapper.setScaledWidth(scaledFrameWidth);
        wrapper.setCanvasHeight(scaledFrameHeight);
        wrapper.setScaledHeight(scaledFrameHeight);
    }
}
