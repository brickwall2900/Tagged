package io.github.brickwall2900.tagged.gif;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

public class GifImageWrapperIconParser extends GifImagePixelARGBParser<GifImageWrapperIcon> {
    private GifImageWrapperIcon wrapper = new GifImageWrapperIcon();
    private short timesToLoop;

    @Override
    public void decodeImagePixels(int[] pixels,
                                  int[] colorTable,
                                  int imageLeft,
                                  int imageTop,
                                  int imageWidth,
                                  int imageHeight) {
        wrapper.loopCount = timesToLoop;
        wrapper.makeImage(
                pixels,
                colorTable,
                imageLeft,
                imageTop,
                imageWidth,
                imageHeight,
                delayTime,
                disposalMethod
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

        wrapper.setCanvasWidth(canvasWidth);
        wrapper.setCanvasHeight(canvasHeight);
    }

    @Override
    public void onHeaderRead(HeaderVersion version) {
    }

    @Override
    public void onApplicationExtensionRead(String id,
                                           String code,
                                           byte[] subBlocks) {
        if (Objects.equals(id, "NETSCAPE") && Objects.equals(code, "2.0")) {
            try (DataInputStream subBlockStream = new DataInputStream(new ByteArrayInputStream(subBlocks))) {
                subBlockStream.skipBytes(1);
                timesToLoop = subBlockStream.readShort();
            } catch (IOException e) {
                throw new InternalError("never reaching here", e);
            }
        }
    }

    @Override
    public void onCommentExtensionRead(byte[] subBlocks) {
    }

    @Override
    public GifImageWrapperIcon getResult() {
        wrapper.finishedDecoding();
        return wrapper;
    }

    @Override
    public void close() {
        super.close();
        this.wrapper = null;
    }
}
