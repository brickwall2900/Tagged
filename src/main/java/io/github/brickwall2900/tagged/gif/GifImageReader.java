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

// instead of a two-step read then parse process
// we will do one step read and parse process
// memory go broom broom
public final class GifImageReader<R> implements AutoCloseable {
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

    // by itself, GifImageReader holds no state of the GIF its reading on
    // the parser will take care of all that... while i'm just decoding its file structure.
    private final GifImageReaderVisitor<R> visitor;

    GifImageReader(ReadableByteChannel channel, GifImageReaderVisitor<R> visitor) {
        this.channel = channel;
        this.visitor = visitor;
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
            int output = buffer.get(bufferCursor) & 0xFF;

            if (channel.read(buffer.position(0)) < 1) {
                throw new EOFException();
            }

            output = output | ((buffer.get(0) & 0xFF) << 8);
            bufferCursor = 1;
            return (short) output;
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
            final int firstBufferRead = BUFFER_SIZE - bufferCursor;
            final int secondBufferRequired = bytes - firstBufferRead;

            if (channel.read(buffer.position(0)) < secondBufferRequired) {
                throw new EOFException();
            }

            bufferCursor = secondBufferRequired;
        } else {
            bufferCursor += bytes;
        }
    }

    private void visit() throws IOException {
        // initial buffer read
        channel.read(buffer);

        readAndValidateHeader();

        readLogicalScreenDescriptorAndGlobalColorTable();

        readBlocks();
    }

    private void readAndValidateHeader() throws IOException {
        byte[] headerVersion = new byte[6];
        readBytes(headerVersion, 0, 6);

        if (Arrays.equals(HEADER_87a, headerVersion)) {
            visitor.onHeaderRead(GifImageReaderVisitor.HeaderVersion.GIF_87A);
            return;
        }

        if (Arrays.equals(HEADER_89a, headerVersion)) {
            visitor.onHeaderRead(GifImageReaderVisitor.HeaderVersion.GIF_89A);
            return;
        }

        throw new IOException("Bad GIF header");
    }

    private void readLogicalScreenDescriptorAndGlobalColorTable() throws IOException {
        int canvasWidth = readShort() & 0xFFFF;
        int canvasHeight = readShort() & 0xFFFF;

        final byte packedField = readByte();
        boolean globalColorTablePresent = (packedField & 0b10000000) == 0b10000000;
        int globalColorTableSize = 1 << ((packedField & 0b00000111) + 1);
        boolean sortFlag = (packedField & 0b00001000) == 0b00001000;

        byte backgroundColorIndex = readByte();
        byte pixelAspectRatio = readByte();

        visitor.onLogicalScreenDescriptorRead(
                canvasWidth,
                canvasHeight,
                globalColorTablePresent,
                globalColorTableSize,
                sortFlag,
                backgroundColorIndex,
                pixelAspectRatio,
                globalColorTablePresent ? readColorTable(globalColorTableSize) : null
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

    private void readBlocks() throws IOException {
        byte b = readByte();
        do {
            if (b == EXTENSION_BYTE) {
                readExtension();
            } else if (b == IMAGE_DESCRIPTOR_BYTE) {
                readImageDescriptorAndBlock();
            } else {
                throw new IllegalStateException("Unexpected byte 0x" + Integer.toHexString(b) +
                        " at 0x" + Integer.toHexString(bufferCursor));
            }

            b = readByte();
        } while (b != END_OF_FILE);
    }

    private void readExtension() throws IOException {
        byte label = readByte();
        switch (label) {
            // graphics
            case (byte) 0xF9 -> readGraphicsControlExtension();

            // application
            case (byte) 0xFF -> readApplicationExtension();

            // comment
            case (byte) 0xFE -> readCommentExtension();

            // plaintext and any other extension
            //case (byte) 0x01 ->
            default -> {
                int skipped = readByte() & 0xFF;
                do {
                    skipBytes(skipped);
                    skipped = readByte();
                } while (skipped != 0);
            }
        }
    }

    private void readGraphicsControlExtension() throws IOException {
        byte blockSize = readByte();

        final byte packedField = readByte();
        byte disposalMethod = (byte) ((packedField & 0b00011100) >>> 2);
        boolean userInput = (packedField & 0b00000010) == 0b00000010;
        boolean transparentColor = (packedField & 0b00000001) == 0b00000001;

        short delayTime = readShort();
        byte transparentColorIndex = readByte();

        readByte(); // block terminator

        visitor.onGraphicsControlExtensionRead(
                disposalMethod,
                userInput,
                transparentColor,
                transparentColorIndex,
                delayTime
        );
    }

    private void readApplicationExtension() throws IOException {
        byte blockSize = readByte();

        byte[] stringBuf = new byte[8 + 3];
        readBytes(stringBuf, 0, 8 + 3);
        String id = new String(stringBuf, 0, 8, StandardCharsets.US_ASCII);
        String code = new String(stringBuf, 8, 3, StandardCharsets.US_ASCII);

        byte[] subBlocks = readSubBlocks();
        visitor.onApplicationExtensionRead(id, code, subBlocks);
    }

    private void readCommentExtension() throws IOException {
        byte[] subBlocks = readSubBlocks();
        visitor.onCommentExtensionRead(subBlocks);
    }

    private void readImageDescriptorAndBlock() throws IOException {
        int imageLeft = readShort() & 0xFFFF;
        int imageTop = readShort() & 0xFFFF;
        int imageWidth = readShort() & 0xFFFF;
        int imageHeight = readShort() & 0xFFFF;

        final byte packedField = readByte();
        boolean localColorTablePresent = (packedField & 0b10000000) == 0b10000000;
        int localColorTableSize = 1 << ((packedField & 0b00000111) + 1);
        boolean sortFlag = (packedField & 0b00100000) == 0b00100000;
        boolean interlace = (packedField & 0b01000000) == 0b01000000;

        int[] colorTable = null;
        if (localColorTablePresent) {
            colorTable = readColorTable(localColorTableSize);
        }

        byte minimumCodeSize = readByte();
        byte[] subBlocks = readSubBlocks();
        visitor.onImageBlockRead(
                imageLeft,
                imageTop,
                imageWidth,
                imageHeight,
                localColorTablePresent,
                localColorTableSize,
                sortFlag, // whoops i swapped the parameter
                interlace, // hahaha i want to die i don't deserve to exist
                minimumCodeSize,
                colorTable,
                subBlocks);
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

    private R getResult() {
        return visitor.getResult();
    }

    @Override
    public void close() throws IOException {
        buffer = null;
        channel.close();
    }

    public static <R> R readImage(Path path, GifImageReaderVisitor<R> parser) throws IOException {
        GifImageReader<R> reader = new GifImageReader<>(Files.newByteChannel(path), parser);
        reader.visit();
        return reader.getResult();
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
            try (GifImageWrapperIconParser parser = new GifImageWrapperIconParser()) {
                GifImageWrapperIcon image = GifImageReader.readImage(chosen, parser);
                System.setProperty("notoptimized", image.toString());
            }
            System.out.printf("%.6f seconds%n", ((System.nanoTime() - x) / 1e9));
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
}
