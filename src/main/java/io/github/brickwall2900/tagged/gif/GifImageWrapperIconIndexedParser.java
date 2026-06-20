package io.github.brickwall2900.tagged.gif;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

public class GifImageWrapperIconIndexedParser extends GifImagePixelIndexedParser<GifImageWrapperIcon> {
    private GifImageWrapperIcon wrapper = new GifImageWrapperIcon();
    private short timesToLoop;

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
                transparencyIndex
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

        wrapper.setBackgroundColorIndex(backgroundColorIndex & 0xFF);
        wrapper.setCanvasWidth(canvasWidth);
        wrapper.setScaledWidth(canvasWidth);
        wrapper.setCanvasHeight(canvasHeight);
        wrapper.setScaledHeight(canvasHeight);
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
        return wrapper;
    }

    @Override
    public void close() {
        super.close();
        this.wrapper = null;
    }
}
