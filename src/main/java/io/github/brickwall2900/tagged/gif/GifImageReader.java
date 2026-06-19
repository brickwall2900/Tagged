package io.github.brickwall2900.tagged.gif;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

// an entire gif reader + parser
// made this on Roblox and remaking it here
// will post a writeup on this if i feel like

// micro optimized as fuck though
// i did try to use a profiler ngl
public final class GifImageReader implements AutoCloseable {
    public static final int[][] INTERLACE_ORDER = new int[][]{
            new int[]{0, 8},
            new int[]{4, 8},
            new int[]{2, 4},
            new int[]{1, 2}
    };
    public static final int MAX_CODE_SIZE = 12;
    public static final int MAX_DICTIONARY_SIZE = 1 << MAX_CODE_SIZE;
    private static final int BUFFER_SIZE = 32767 * 2;
    private static final byte[] HEADER_87a = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HEADER_89a = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte END_OF_FILE = 0x3B;
    private static final byte EXTENSION_BYTE = 0x21;
    private static final byte IMAGE_DESCRIPTOR_BYTE = 0x2C;

    // dunno if this is okay
    // should wel ike buffer ther whgole tentier gi dfaa there and jsut rrad form rthat tone bufer?
    // or just jhusrea  channel because shwy not?
    private final ReadableByteChannel channel;
    private ByteBuffer buffer;

    // jeffrey epstein crashed my gif decoder ;(
    // 38d3685e1a950990ac016e5eef280ed03305fbc4d66562e933a2b7cb5590188b
    // oke fixed
    private int bufferCursor;

    GifImageReader(ReadableByteChannel channel) {
        this.channel = channel;
        this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.bufferCursor = 0;
    }

    private void readBytes(
            final byte[] outBuffer,
            final int offset,
            final int length
    ) throws IOException {
        final int remaining = BUFFER_SIZE - bufferCursor;
        if (remaining < length) {
            // incoming buffer overflow
            // need to read remaining bytes + do channel read + required remaining byte reads
            buffer.get(bufferCursor, outBuffer, offset, remaining);

            final int read = channel.read(buffer.position(0));
            if (read < length - remaining) {
                throw new EOFException();
            }

            // get the rest of the data
            final int lastReadSize = length - remaining;
            buffer.get(0, outBuffer, offset + remaining, lastReadSize);
            bufferCursor = lastReadSize;
        } else {
            // okay we good
            buffer.get(bufferCursor, outBuffer, offset, length);
            bufferCursor += length;
        }
    }

    private byte readByte() throws IOException {
        if (BUFFER_SIZE - bufferCursor < 1) {
            // incoming buffer overflow
            // need to read remaining bytes + do channel read + required remaining byte reads
            final int read = channel.read(buffer.position(0));
            if (read < 1) {
                throw new EOFException();
            }
            bufferCursor = 0;
        }
        // okay we good
        return buffer.get(bufferCursor++);
    }

    private short readShort() throws IOException {
        final int remaining = BUFFER_SIZE - bufferCursor;
        if (remaining == 1) { // one byte
            short output = buffer.get(bufferCursor);

            if (channel.read(buffer.position(0)) < 1) {
                throw new EOFException();
            }

            output = (short) (output | (buffer.get(0) << 8));
            bufferCursor = 1;
            return output;
        } else if (remaining == 0) { // no bytes
            if (channel.read(buffer.position(0)) < 1) {
                throw new EOFException();
            }
        }

        // two or three or more bytes
        short output = buffer.getShort(bufferCursor);
        bufferCursor += 2;
        return output;
    }

    private void skipBytes(final int bytes) throws IOException {
        if (BUFFER_SIZE - bufferCursor < bytes) {
            final int firstBufferRead = BUFFER_SIZE - bytes;
            final int secondBufferRequired = bytes - firstBufferRead;

            if (channel.read(buffer.position(0)) < secondBufferRequired) {
                throw new EOFException();
            }

            bufferCursor = secondBufferRequired;
        } else {
            bufferCursor += bytes;
        }
    }

    private GifFile read() throws IOException {
        // initial buffer read
        channel.read(buffer);

        readAndValidateHeader();
        LogicalScreenDescriptor logicalScreenDescriptor = readLogicalScreenDescriptor();
        int[] colorTable = null;
        if (logicalScreenDescriptor.globalColorTablePresent) {
            colorTable = readColorTable(logicalScreenDescriptor.globalColorTableSize);
        }
        Block[] blocks = readBlocks();
        return new GifFile(logicalScreenDescriptor, colorTable, blocks);
    }

    private void readAndValidateHeader() throws IOException {
        byte[] headerVersion = new byte[6];
        readBytes(headerVersion, 0, 6);

        if (Arrays.mismatch(HEADER_87a, headerVersion) != -1
                && Arrays.mismatch(HEADER_89a, headerVersion) != -1) {
            throw new IOException("Bad GIF header");
        }
    }

    private LogicalScreenDescriptor readLogicalScreenDescriptor() throws IOException {
        short canvasWidth = readShort();
        short canvasHeight = readShort();

        final byte packedField = readByte();
        boolean globalColorTablePresent = (packedField & 0b10000000) == 0b10000000;
        int globalColorTableSize = 1 << ((packedField & 0b00000111) + 1);
        boolean sortFlag = (packedField & 0b00001000) == 0b00001000;

        byte backgroundColorIndex = readByte();
        byte pixelAspectRatio = readByte();

        return new LogicalScreenDescriptor(
                canvasWidth,
                canvasHeight,
                globalColorTablePresent,
                globalColorTableSize,
                sortFlag,
                backgroundColorIndex,
                pixelAspectRatio
        );
    }

    private int[] readColorTable(int length) throws IOException {
        int[] colors = new int[length];
        byte[] color = new byte[3];
        for (int i = 0; i < length; i++) {
            readBytes(color, 0, 3);
            colors[i] = (0xFF << 24) | ((color[0] & 0xFF) << 16) | ((color[1] & 0xFF) << 8) | (color[2] & 0xFF);
        }
        return colors;
    }

    private Block[] readBlocks() throws IOException {
        List<Block> blocks = new ArrayList<>();

        byte b = readByte();
        do {
            if (b == EXTENSION_BYTE) {
                Extension extension = readExtension();
                if (extension != null) {
                    blocks.add(extension);
                }
            } else if (b == IMAGE_DESCRIPTOR_BYTE) {
                blocks.add(readImageBlock());
            } else {
                throw new IllegalStateException("Unexpected byte 0x" + Integer.toHexString(b) +
                        " at 0x" + Integer.toHexString(bufferCursor));
            }

            b = readByte();
        } while (b != END_OF_FILE);

        return blocks.toArray(Block[]::new);
    }

    private Extension readExtension() throws IOException {
        byte label = readByte();
        switch (label) {
            // graphics
            case (byte) 0xF9 -> {
                return readGraphicsControlExtension();
            }

            // application
            case (byte) 0xFF -> {
                return readApplicationExtension();
            }

            // comment
            case (byte) 0xFE -> {
                return readCommentExtension();
            }

            // plaintext and any other extension
            //case (byte) 0x01 ->
            default -> {
                byte skipped = readByte();
                do {
                    skipBytes(skipped);
                    skipped = readByte();
                } while (skipped != 0);
                return null;
            }
        }
    }

    private GraphicsControlExtension readGraphicsControlExtension() throws IOException {
        byte blockSize = readByte();

        final byte packedField = readByte();
        byte disposalMethod = (byte) ((packedField & 0b00011100) >>> 2);
        boolean userInput = (packedField & 0b00000010) == 0b00000010;
        boolean transparentColor = (packedField & 0b00000001) == 0b00000001;

        short delayTime = readShort();
        byte transparentColorIndex = readByte();

        readByte(); // block terminator

        return new GraphicsControlExtension(
                disposalMethod,
                userInput,
                transparentColor,
                transparentColorIndex,
                delayTime
        );
    }

    private ApplicationExtension readApplicationExtension() throws IOException {
        byte blockSize = readByte();

        byte[] stringBuf = new byte[8 + 3];
        readBytes(stringBuf, 0, 8 + 3);
        String id = new String(stringBuf, 0, 8, StandardCharsets.US_ASCII);
        String code = new String(stringBuf, 8, 3, StandardCharsets.US_ASCII);

        byte[] subBlocks = readSubBlocks();
        short netscapeTimesToLoop = 0;

        if (Objects.equals(id, "NETSCAPE") && Objects.equals(code, "2.0")) {
            try (DataInputStream subBlockStream = new DataInputStream(new ByteArrayInputStream(subBlocks))) {
                subBlockStream.skipBytes(1);
                netscapeTimesToLoop = subBlockStream.readShort();
            }
        }

        return new ApplicationExtension(
                subBlocks,
                id,
                code,
                netscapeTimesToLoop);
    }

    private CommentExtension readCommentExtension() throws IOException {
        byte[] subBlocks = readSubBlocks();
        return new CommentExtension(
                subBlocks,
                null
        );
    }

    private ImageBlock readImageBlock() throws IOException {
        ImageDescriptor descriptor = readImageDescriptor();
        int[] localColorTable = null;
        if (descriptor.localColorTablePresent) {
            localColorTable = readColorTable(descriptor.localColorTableSize);
        }

        byte minimumCodeSize = readByte();
        return new ImageBlock(
                descriptor,
                localColorTable,
                minimumCodeSize,
                readSubBlocks()
        );
    }

    private ImageDescriptor readImageDescriptor() throws IOException {
        short imageLeft = readShort();
        short imageTop = readShort();
        short imageWidth = readShort();
        short imageHeight = readShort();

        final byte packedField = readByte();
        boolean localColorTablePresent = (packedField & 0b10000000) == 0b10000000;
        int localColorTableSize = 1 << ((packedField & 0b00000111) + 1);
        boolean sortFlag = (packedField & 0b00100000) == 0b00100000;
        boolean interlace = (packedField & 0b01000000) == 0b01000000;

        return new ImageDescriptor(
                imageLeft,
                imageTop,
                imageWidth,
                imageHeight,
                localColorTablePresent,
                localColorTableSize,
                interlace,
                sortFlag
        );
    }

    private byte[] readSubBlocks() throws IOException {
        int numberOfBytes = readByte() & 0xFF;
        if (numberOfBytes == 0) {
            return new byte[0];
        }

        byte[] output = new byte[numberOfBytes];
        int totalRead = 0;
        do {
            if (totalRead + numberOfBytes > output.length) {
                output = Arrays.copyOf(output, (totalRead + numberOfBytes) * 2);
            }

            readBytes(output, totalRead, numberOfBytes);
            totalRead += numberOfBytes;

            numberOfBytes = Byte.toUnsignedInt(readByte());
        } while (numberOfBytes != 0);

        return output.length == totalRead ? output : Arrays.copyOf(output, totalRead);
    }

    // one rule: be fast as fuck
    private List<ParsedImage> parse(GifFile file, int[] loopCount) {
        return parseImageBlocks(file.blocks, file.descriptor, file.globalColorTable, loopCount);
    }

    private GifImageWrapper construct(String gifName, List<ParsedImage> images, int loopCount, boolean lowResolution) {
        return new GifImageWrapper(gifName, images, loopCount, lowResolution);
    }

    private List<ParsedImage> parseImageBlocks(
            final Block[] blocks,
            final LogicalScreenDescriptor descriptor,
            final int[] globalColorTable,
            final int[] loopCount
    ) {
        boolean[] transparencyEnabled = new boolean[1];
        byte[] transparencyIndex = new byte[1];
        short[] delayTime = new short[1];

        // put dict here so we dont have to reallocate the dict every block
        final byte MAX_CODE_SIZE = 12;
        final short MAX_DICTIONARY_SIZE = 1 << MAX_CODE_SIZE;
        final int[] dictionary = new int[MAX_DICTIONARY_SIZE];

        final List<ParsedImage> parsedImages = new ArrayList<>();
        final int size = blocks.length;
        for (int i = 0; i < size; i++) {
            if (blocks[i] instanceof ApplicationExtension applicationExtension) {
                loopCount[0] = applicationExtension.netscapeTimesToLoop;
                continue;
            }

            ParsedImage image = parseImageBlock(
                    blocks[i],
                    descriptor,
                    globalColorTable,
                    dictionary,
                    transparencyEnabled,
                    transparencyIndex,
                    delayTime);
            if (image != null) {
                parsedImages.add(image);
            }
        }

        return parsedImages;
    }

    private ParsedImage parseImageBlock(
            final Block block,
            final LogicalScreenDescriptor descriptor,
            final int[] globalColorTable,
            final int[] dictionary,
            final boolean[] transparencyEnabled,
            final byte[] transparencyIndex,
            short[] delayTime) {
        if (block instanceof ImageBlock(
                ImageDescriptor imageDescriptor, int[] colorTable, byte minimumCodeSize, byte[] subBlocks
        )) {
            final int[] localColorTable = colorTable != null
                    ? colorTable : globalColorTable;
            final byte[] indices = new byte[imageDescriptor.imageWidth * imageDescriptor.imageHeight];
            decompressGifImageBlock(
                    dictionary,
                    subBlocks,
                    indices,
                    minimumCodeSize
            );
            final int[] pixels = new int[indices.length];
            if (!imageDescriptor.interlace) {
                readPixels(
                        indices,
                        pixels,
                        descriptor,
                        localColorTable,
                        globalColorTable,
                        transparencyEnabled[0],
                        transparencyIndex[0]
                );
            } else {
                readPixelsInterlaced(
                        indices,
                        pixels,
                        descriptor,
                        imageDescriptor,
                        localColorTable,
                        globalColorTable,
                        transparencyEnabled[0],
                        transparencyIndex[0]
                );
            }

            return new ParsedImage(pixels,
                    imageDescriptor.imageLeft & 0xFFFF,
                    imageDescriptor.imageTop & 0xFFFF,
                    imageDescriptor.imageWidth & 0xFFFF,
                    imageDescriptor.imageHeight & 0xFFFF,
                    delayTime[0] & 0xFFFF);
        } else if (block instanceof GraphicsControlExtension graphicsControlExtension) {
            transparencyEnabled[0] = graphicsControlExtension.transparencyEnabled;
            transparencyIndex[0] = graphicsControlExtension.transparentColorIndex;
            delayTime[0] = graphicsControlExtension.delayTime;
            return null;
        }
        return null;
    }

    private void readPixels(
            final byte[] indices,
            final int[] pixels,
            final LogicalScreenDescriptor descriptor,
            final int[] localColorTable,
            final int[] globalColorTable,
            final boolean transparencyEnabled,
            final byte transparencyIndex
    ) {
        final byte[] src = indices;
        final int[] dst = pixels;
        final int length = indices.length;

        final int tableLen = localColorTable.length;
        final int backgroundColorIndex = descriptor.backgroundColorIndex & 0xFF;
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
            final LogicalScreenDescriptor descriptor,
            final ImageDescriptor imageDescriptor,
            final int[] localColorTable,
            final int[] globalColorTable,
            final boolean transparencyEnabled,
            final byte transparencyIndex
    ) {
        final byte[] src = indices;
        final int[] dst = pixels;

        final int width = imageDescriptor.imageWidth;
        final int height = imageDescriptor.imageHeight;
        final int tableLen = localColorTable.length;
        final int backgroundColorIndex = descriptor.backgroundColorIndex & 0xFF;
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

    @Override
    public void close() throws IOException {
        buffer = null;
        channel.close();
    }

    public static GifImageWrapper readImage(Path path, boolean lowResolution) throws IOException {
        GifImageReader reader = new GifImageReader(Files.newByteChannel(path));
        long x = System.nanoTime();
        GifFile file = reader.read();
        System.out.printf("read: %.6f sec%n", (System.nanoTime() - x) / 1e9);
        x = System.nanoTime();
        int[] loopCount = new int[1];
        List<ParsedImage> images = reader.parse(file, loopCount);
        System.out.printf("parse: %.6f sec%n", (System.nanoTime() - x) / 1e9);
        x = System.nanoTime();
        GifImageWrapper image = reader.construct(path.toString(), images, loopCount[0], lowResolution);
        System.out.printf("construct: %.6f sec%n", (System.nanoTime() - x) / 1e9);
        return image;
    }

    static void main() throws IOException {
        System.in.read();
        Path[] test;
        try (Stream<Path> stream = Files.list(Path.of("/home", "brickwall2900", "Data", "Pictures", "Reaction Images"))) {
            test = stream
                    .filter(x -> x.getFileName().toString().toLowerCase().endsWith(".gif"))
                    .sorted(((Comparator<Path>) (o1, o2) -> {
                        try {
                            return Long.compare(Files.size(o1), Files.size(o2));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).reversed())
                    .toArray(Path[]::new);
        }
//        Random random = new Random();

        for (int i = test.length-1; i >= 0; i--) {
            Path chosen = test[i];
            System.out.printf("%s (%.2f MB)%n", chosen, Files.size(chosen) / 1024.0 / 1024.0);
            long x = System.nanoTime();
            GifImageWrapper image = GifImageReader.readImage(chosen, false);
            System.out.printf("%.6f seconds%n", ((System.nanoTime() - x) / 1e9));
            System.setProperty("notoptimized", image.toString());
        }
        System.in.read();
        System.in.read();
        System.in.read();
//        Path chosen = Path.of("/home/brickwall2900/Data/Downloads/Photos/sample_1.gif");
//        Path chosen = Path.of("/home/brickwall2900/Data/Pictures/Reaction Images/sad-crying-black-guy.gif");
//        System.out.println(chosen);
//        long x = System.nanoTime();
//        GifImage image = GifImageReader.readImage(chosen);
//        System.err.println(image);
//        System.out.printf("%.6f seconds%n", ((System.nanoTime() - x) / 1e9));
    }

    // pixels are ALWAYS in ARGB8 format
    record ParsedImage(int[] imagePixels,
                       int left,
                       int top,
                       int width,
                       int height,
                       int delayTime) {}

    private record GifFile(LogicalScreenDescriptor descriptor,
                           int[] globalColorTable,
                           Block[] blocks) {
        @Override
        public String toString() {
            return "GifFile{" +
                    "descriptor=" + descriptor +
                    ", globalColorTable=" + Arrays.toString(globalColorTable) +
                    ", blocks=" + Arrays.toString(blocks) +
                    '}';
        }
    }

    private record LogicalScreenDescriptor(short canvasWidth,
                                           short canvasHeight,
                                           boolean globalColorTablePresent,
                                           int globalColorTableSize,
                                           boolean sortFlag,
                                           byte backgroundColorIndex,
                                           byte pixelAspectRatio) {}

    private interface Block { }
    private interface Extension extends Block { }

    private record ImageDescriptor(short imageLeft,
                                   short imageTop,
                                   short imageWidth,
                                   short imageHeight,
                                   boolean localColorTablePresent,
                                   int localColorTableSize,
                                   boolean interlace,
                                   boolean sortFlag) {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ImageDescriptor that = (ImageDescriptor) o;
            return imageTop == that.imageTop && imageLeft == that.imageLeft && imageWidth == that.imageWidth && sortFlag == that.sortFlag && imageHeight == that.imageHeight && interlace == that.interlace && localColorTableSize == that.localColorTableSize && localColorTablePresent == that.localColorTablePresent;
        }

        @Override
        public int hashCode() {
            return Objects.hash(imageLeft, imageTop, imageWidth, imageHeight, localColorTablePresent, localColorTableSize, interlace, sortFlag);
        }

        @Override
        public String toString() {
            return "ImageDescriptor{" +
                    "imageLeft=" + imageLeft +
                    ", imageTop=" + imageTop +
                    ", imageWidth=" + imageWidth +
                    ", imageHeight=" + imageHeight +
                    ", localColorTablePresent=" + localColorTablePresent +
                    ", localColorTableSize=" + localColorTableSize +
                    ", interlace=" + interlace +
                    ", sortFlag=" + sortFlag +
                    '}';
        }
    }

    private record ImageBlock(ImageDescriptor descriptor,
                              int[] localColorTable,
                              byte minimumCodeSize,
                              byte[] subBlocks) implements Block {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ImageBlock that = (ImageBlock) o;
            return minimumCodeSize == that.minimumCodeSize && Objects.deepEquals(subBlocks, that.subBlocks) && Objects.equals(descriptor, that.descriptor) && Objects.deepEquals(localColorTable, that.localColorTable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(descriptor, Arrays.hashCode(localColorTable), minimumCodeSize, Arrays.hashCode(subBlocks));
        }

        @Override
        public String toString() {
            return "ImageBlock{" +
                    "descriptor=" + descriptor +
                    ", localColorTable=" + Arrays.toString(localColorTable) +
                    ", minimumCodeSize=" + minimumCodeSize +
                    ", subBlocks=" + Arrays.toString(subBlocks) +
                    '}';
        }
    }

    private record GraphicsControlExtension(byte disposalMethod,
                                            boolean userInput,
                                            boolean transparencyEnabled,
                                            byte transparentColorIndex, short delayTime) implements Extension {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            GraphicsControlExtension that = (GraphicsControlExtension) o;
            return delayTime == that.delayTime && userInput == that.userInput && disposalMethod == that.disposalMethod && transparencyEnabled == that.transparencyEnabled && transparentColorIndex == that.transparentColorIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(disposalMethod, userInput, transparencyEnabled, transparentColorIndex, delayTime);
        }

        @Override
        public String toString() {
            return "GraphicsControlExtension{" +
                    "disposalMethod=" + disposalMethod +
                    ", userInput=" + userInput +
                    ", transparencyEnabled=" + transparencyEnabled +
                    ", transparentColorIndex=" + transparentColorIndex +
                    ", delayTime=" + delayTime +
                    '}';
        }
    }

    private record ApplicationExtension(byte[] blockData,
                                        String id,
                                        String code,
                                        short netscapeTimesToLoop) implements Extension {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ApplicationExtension that = (ApplicationExtension) o;
            return netscapeTimesToLoop == that.netscapeTimesToLoop && Objects.equals(id, that.id) && Objects.equals(code, that.code) && Objects.deepEquals(blockData, that.blockData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(blockData), id, code, netscapeTimesToLoop);
        }

        @Override
        public String toString() {
            return "ApplicationExtension{" +
                    "blockData=" + Arrays.toString(blockData) +
                    ", id='" + id + '\'' +
                    ", code='" + code + '\'' +
                    ", netscapeTimesToLoop=" + netscapeTimesToLoop +
                    '}';
        }
    }

    private record CommentExtension(byte[] blockData,
                                    String comment) implements Extension {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            CommentExtension that = (CommentExtension) o;
            return Objects.equals(comment, that.comment) && Objects.deepEquals(blockData, that.blockData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(blockData), comment);
        }

        @Override
        public String toString() {
            return "CommentExtension{" +
                    "blockData=" + Arrays.toString(blockData) +
                    ", comment='" + comment + '\'' +
                    '}';
        }
    }

    private record PlaintextExtension(byte[] blockData) implements Extension {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            PlaintextExtension that = (PlaintextExtension) o;
            return Objects.deepEquals(blockData, that.blockData);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(blockData);
        }

        @Override
        public String toString() {
            return "PlaintextExtension{" +
                    "blockData=" + Arrays.toString(blockData) +
                    '}';
        }
    }
}
