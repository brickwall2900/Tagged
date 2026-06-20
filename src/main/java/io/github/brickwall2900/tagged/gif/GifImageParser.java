package io.github.brickwall2900.tagged.gif;

public abstract class GifImageParser<R> implements GifImageReaderVisitor<R>, AutoCloseable {
    final byte MAX_CODE_SIZE = 12;
    final short MAX_DICTIONARY_SIZE = 1 << MAX_CODE_SIZE;
    int[] dictionary = new int[MAX_DICTIONARY_SIZE];

    protected int canvasWidth, canvasHeight;
    protected byte backgroundColorIndex;
    protected int[] globalColorTable;

    protected boolean transparencyEnabled;
    protected int transparencyIndex;
    protected short delayTime;
    protected byte disposalMethod;

    @Override
    public void onLogicalScreenDescriptorRead(int canvasWidth,
                                              int canvasHeight,
                                              boolean globalColorTablePresent,
                                              int globalColorTableSize,
                                              boolean sortFlag,
                                              byte backgroundColorIndex,
                                              byte pixelAspectRatio,
                                              int[] globalColorTable) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.backgroundColorIndex = backgroundColorIndex;
        this.globalColorTable = globalColorTable;
    }

    @Override
    public void onImageBlockRead(int imageLeft,
                                 int imageTop,
                                 int imageWidth,
                                 int imageHeight,
                                 boolean localColorTablePresent,
                                 int localColorTableSize,
                                 boolean sortFlag,
                                 boolean interlace,
                                 byte minimumCodeSize,
                                 int[] localColorTable,
                                 byte[] subBlocks) {
        final int[] colorTable = localColorTable != null
                ? localColorTable : globalColorTable;
        final byte[] indices = new byte[imageWidth * imageHeight];

        decompressGifImageBlock(
                dictionary,
                subBlocks,
                indices,
                minimumCodeSize
        );

        decodeImage(
                indices,
                interlace,
                colorTable,
                imageLeft,
                imageTop,
                imageWidth,
                imageHeight
        );

        transparencyEnabled = false;
        disposalMethod = 0;
        delayTime = 0;
    }

    // here comes the LZW type shit
    // and its unreadable wow
    // what the fuck did i do
    private void decompressGifImageBlock(
            final int[] dictionary,
            final byte[] imageCompressedDataBlock,
            final byte[] indexBuffer,
            final int minimumCodeSize
    ) {
        final int clearCode = (1 << minimumCodeSize);
        final int eoiCode = (clearCode + 1);

        // layout
        // long = 0bllllllllllllppppppppppppssssssss
        for (int i = 0; i < clearCode; i++) {
            dictionary[i] = (1 << 20)
                    | /* ((-1 & 0xFFF) << 8) */ ((4095) << 8)
                    | (i & 0xFF);
            // how to decode
            // length: ((dictionary[code] >>> 20) & 0xFFF)
            // prefix: (short) ((dictionary[code] >>> 8) & 0xFFF)
            // suffix: (byte) (dictionary[code])
        }

        int dictionarySize = clearCode + 2;
        int codeSize = minimumCodeSize + 1;
        int codeMask = (1 << codeSize) - 1;

        long bitBuffer = 0;
        int bitBufferSize = 0;

        int lastCode = -1; // -1 for first steps

        final int indexBufferSize = indexBuffer.length;
        int indexBufferCursor = 0;

        final int dataBufferSize = imageCompressedDataBlock.length;
        final int dataBufferSizeFillLimit = dataBufferSize - 4;
        int dataBufferCursor = 0;

        while (indexBufferSize > indexBufferCursor) {
            if (bitBufferSize <= 24 && dataBufferCursor <= dataBufferSizeFillLimit) {
                // fast path
                // 4 bytes at a time hell yeah
                long bytes = (imageCompressedDataBlock[dataBufferCursor] & 0xFF)
                        | ((imageCompressedDataBlock[dataBufferCursor + 1] & 0xFF) << 8)
                        | ((imageCompressedDataBlock[dataBufferCursor + 2] & 0xFF) << 16)
                        | ((imageCompressedDataBlock[dataBufferCursor + 3] & 0xFFL) << 24);
                bitBuffer |= bytes << bitBufferSize;
                bitBufferSize += 32;
                dataBufferCursor += 4;
            } else {
                while (bitBufferSize < codeSize && dataBufferCursor < dataBufferSize) {
                    bitBuffer |= ((long) (imageCompressedDataBlock[dataBufferCursor++] & 0xFF)) << bitBufferSize;
                    bitBufferSize += 8;
                }
            }

            final short code = (short) (bitBuffer & codeMask);
            bitBuffer >>>= codeSize;
            bitBufferSize -= codeSize;

            if (code == eoiCode) { // end of info
                break;
            }

            if (code == clearCode) {
                // reset dictionary
                codeSize = minimumCodeSize + 1;
                codeMask = (1 << codeSize) - 1;
                dictionarySize = clearCode + 2;
                lastCode = -1;
                continue;
            }

            final int length;
            if (code < dictionarySize) {
                // output {CODE} to index stream
                length = (short) ((dictionary[code] >>> 20) & 0xFFF);

                int writeCursor = indexBufferCursor + length - 1;
                indexBufferCursor += length;

                int s = code;
                while (s <= 4094) {
                    // michael jacksoning the index buffer for optimization broom broom
                    indexBuffer[writeCursor--] = (byte) (dictionary[s]);
                    s = (short) ((dictionary[s] >>> 8) & 0xFFF);
                }
            } else if (code == dictionarySize && lastCode != -1) {
                // let K be the first index of {CODE-1}
                // then output {CODE-1}+K to index stream
                length = (short) (((dictionary[lastCode] >>> 20) & 0xFFF) + 1); // + 1 cuz of k

                final int targetCursor = indexBufferCursor;
                indexBufferCursor += length;

                int s = lastCode;
                int writeCursor = targetCursor + length - 2;
                while (s <= 4094) {
                    indexBuffer[writeCursor--] = (byte) (dictionary[s]);
                    s = (short) ((dictionary[s] >>> 8) & 0xFFF);
                }
                // append k
                indexBuffer[targetCursor + length - 1] = indexBuffer[targetCursor];
            } else {
                break;
            }

            if (lastCode != -1 && dictionarySize < MAX_DICTIONARY_SIZE) {
                // dictionary // long = 0bllllllllllllppppppppppppssssssss
                short nextLength = (short) (((dictionary[lastCode] >>> 20) & 0xFFF) + 1);
                dictionary[dictionarySize++] = (nextLength << 20)
                        | (lastCode << 8)
                        | (indexBuffer[indexBufferCursor - length] & 0xFF);

                if (dictionarySize == (1 << codeSize) && codeSize < MAX_CODE_SIZE) {
                    codeMask = (1 << ++codeSize) - 1;
                }
            }

            lastCode = code;
        }
    }

    public abstract void decodeImage(
            byte[] indices,
            boolean interlace,
            int[] colorTable,
            int imageLeft,
            int imageTop,
            int imageWidth,
            int imageHeight
    );

    @Override
    public void onGraphicsControlExtensionRead(byte disposalMethod,
                                               boolean userInput,
                                               boolean transparentColor,
                                               byte transparentColorIndex,
                                               short delayTime) {
        this.disposalMethod = disposalMethod;
        this.transparencyEnabled = transparentColor;
        this.transparencyIndex = transparentColorIndex & 0xFF;
        this.delayTime = delayTime;
    }

    @Override
    public void close() {
        dictionary = null;
    }
}
