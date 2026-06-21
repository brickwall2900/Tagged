package io.github.brickwall2900.tagged.gif;

// i need suggestions
// composition over inheritance?
// and how do i make it composite?
public abstract class GifImageParser<R> implements GifImageReaderVisitor<R>, AutoCloseable {
    static final byte MAX_CODE_SIZE = 12;
    static final short MAX_DICTIONARY_SIZE = 1 << MAX_CODE_SIZE;
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
    public void onImageBlockRead(final int imageLeft,
                                 final int imageTop,
                                 final int imageWidth,
                                 final int imageHeight,
                                 final boolean localColorTablePresent,
                                 final int localColorTableSize,
                                 final boolean sortFlag,
                                 final boolean interlace,
                                 final byte minimumCodeSize,
                                 final int[] localColorTable,
                                 final byte[] subBlocks) {
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

    // i just found out the source code for the native GIF image decoder in Java
    // they use a native method for ts
    // how did it come to this point wtf
    private static void decompressGifImageBlock(
            final int[] dictionary,
            final byte[] imageCompressedDataBlock,
            final byte[] indexBuffer,
            final int minimumCodeSize
    ) {
        // i wonder what JIT optimizations can be applied here
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

            // suffix is the stored byte
            // prefix is the next index of the suffix you read
            // prefix value 4095 and above indicates a root prefix which means it's the very end please stop reading
            // length is the count of how many prefixes u gotta read starting from an index
            // its cached here so we don't need a separate array to write to the index buffer

            // suffix is 8 bits since it's a byte
            // prefix is 12 bits since it's 0 up to the dictionary size
            // length is 12 bits since the max possible lengths you read from the prefix is also the dict size
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
                final int k = targetCursor + length - 1;
                int writeCursor = k - 1;
                while (s <= 4094) {
                    indexBuffer[writeCursor--] = (byte) (dictionary[s]);
                    s = (short) ((dictionary[s] >>> 8) & 0xFFF);
                }
                // append k
                indexBuffer[k] = indexBuffer[targetCursor];
            } else {
                break;
            }

            if (lastCode != -1 && dictionarySize < MAX_DICTIONARY_SIZE) {
                // dictionary // int = 0bllllllllllllppppppppppppssssssss
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
