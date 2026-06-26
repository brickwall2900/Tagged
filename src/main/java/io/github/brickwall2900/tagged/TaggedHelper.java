package io.github.brickwall2900.tagged;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.openhft.hashing.LongHashFunction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class TaggedHelper {
    public static final String INDEX_FILE_NAME = ".taggedindex";
    private static final short FILE_VERSION = 2;
    private static final int FILE_HEADER = 0x54616764;
    private static final Path TAGGED_DIRECTORY = Path.of(System.getProperty("user.home"), ".tagged");
    private static final Path LOCATION_FILE = TAGGED_DIRECTORY.resolve("locations");
    private final Tagged tagged;

    static {
        try {
            Files.createDirectories(TAGGED_DIRECTORY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int threadCount;
    private ExecutorService executor;

    // i don't know but the children yearn for another data structure or smth
    private Map<Path, Long2ObjectMap<FileTag>> locationToHashToFileTagMap = new HashMap<>();

    public TaggedHelper(Tagged tagged) {
        this.tagged = tagged;
        executor = Executors.newFixedThreadPool((int) (Runtime.getRuntime().availableProcessors() / 1.25));
    }

    public SwingWorkerWithDone<FileTag[], Void> newIndexWorkerAsync(Long2ObjectMap<FileTag> map, Path location) {
        return new FileIndexWorker(map, location);
    }

    public SwingWorkerWithDone<Long2ObjectMap<FileTag>, Void> newIndexHashWorkerAsync(FileTag[] tags) {
        return new IndexHashWorker(tags);
    }

    public SwingWorkerWithDone<Void, Void> newIndexWriterAsync(Long2ObjectMap<FileTag> map, Path location) {
        return new TagsWriter(map, location.resolve(INDEX_FILE_NAME));
    }

    public SwingWorkerWithDone<Long2ObjectMap<FileTag>, Void> newIndexReaderAsync(Path location) {
        Path oldIndexPath = TAGGED_DIRECTORY.resolve("index");
        return !Files.isRegularFile(oldIndexPath)
                ? new TagsReader(location.resolve(INDEX_FILE_NAME))
                : new OldTagsReader();
    }

    public SwingWorkerWithDone<Void, Void> newLocationWriterAsync(List<Path> locations) {
        return new LocationWriter(locations, LOCATION_FILE);
    }

    public SwingWorkerWithDone<List<Path>, Void> newLocationReaderAsync() {
        return new LocationReader(LOCATION_FILE);
    }

    public boolean doesIndexExist(Path indexFile) {
        return Files.exists(indexFile);
    }

    public boolean doesLocationExist() {
        return Files.exists(LOCATION_FILE);
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public Long2ObjectMap<FileTag> getHashToFileTagMap(Path parentDirectory) {
        return locationToHashToFileTagMap.get(parentDirectory);
    }

    public void setHashToFileTagMap(Path parentDirectory, Long2ObjectMap<FileTag> hashToFileTagMap) {
        this.locationToHashToFileTagMap.put(parentDirectory, hashToFileTagMap);
    }

    public Map<Path, Long2ObjectMap<FileTag>> getLocationToHashToFileTagMap() {
        return locationToHashToFileTagMap;
    }

    private static long calculateHash(FileTag fileTag) {
        return calculateHash(fileTag.fileName);
    }

    private static long calculateHash(Path path) {
        return LongHashFunction.xx().hashChars(path.toString());
    }

    public void storeTag(FileTag tag) {
        Path location = locationToHashToFileTagMap.keySet()
                .stream()
                .filter(tag.locationPath::startsWith)
                .findFirst()
                .orElse(null);
        if (location != null) {
            Long2ObjectMap<FileTag> hashToFileTagMap = getHashToFileTagMap(location);
            long hash = calculateHash(tag);
            boolean present = hashToFileTagMap.containsKey(hash);
            boolean hasTags = tag.tags.length > 0;
            if (!hasTags && present) {
                hashToFileTagMap.remove(hash);
            } else if (!present && hasTags) {
                hashToFileTagMap.put(hash, tag);
            }
        }
    }

    public void changeThreadCount(int threadCount) {
        if (this.threadCount != threadCount) {
            if (executor != null) {
                ExecutorService old = executor;
                executor = Executors.newFixedThreadPool(threadCount);
                this.threadCount = threadCount;

                old.shutdown();
                try {
                    old.awaitTermination(67, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                }
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    // okay so how would Tagged work?
    public record FileTag(Path locationPath, Path fileName, String[] tags) {
        public FileTag(Path fileName, String[] tags) {
            this(null, fileName, tags);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            FileTag fileTag = (FileTag) o;
            return Objects.equals(fileName, fileTag.fileName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(fileName);
        }

        @Override
        public String toString() {
            return "FileTag[" +
                    "fileName=" + fileName + ", " +
                    "tags=" + Arrays.toString(tags) + ']';
        }
    }

    private static final class LocationWriter extends SwingWorkerWithDone<Void, Void> {
        private final Path destination;
        private final List<Path> locations;

        private LocationWriter(List<Path> locations, Path destination) {
            this.destination = destination;
            this.locations = locations;
        }

        @Override
        protected Void doInBackground() throws Exception {
            if (Files.exists(destination)) {
                Path backupDest = destination.getParent().resolve("location-" +
                        Files.getLastModifiedTime(destination).toMillis());
                Files.copy(destination, backupDest, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);

                deleteExtra();
            }

            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(destination))) {
                writer.beginArray();

                for (Path location : locations) {
                    writer.value(location.toString());
                }

                writer.endArray();
            }

            Files.setLastModifiedTime(destination, FileTime.fromMillis(System.currentTimeMillis()));

            return null;
        }

        private static void deleteExtra() throws IOException {
            try (Stream<Path> list = Files.list(TAGGED_DIRECTORY)) {
                List<Path> candidates = list
                        .filter(x -> x.getFileName().toString().toLowerCase().startsWith("location-"))
                        .sorted(Comparator.comparingLong(x -> {
                            try {
                                return Files.getLastModifiedTime(x).toMillis();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }))
                        .toList();
                if (candidates.size() > 5) {
                    Path candidate = candidates.getFirst();
                    Files.deleteIfExists(candidate);
                }
            }
        }
    }

    private static final class LocationReader extends SwingWorkerWithDone<List<Path>, Void> {
        private final Path source;

        private LocationReader(Path source) {
            this.source = source;
        }

        @Override
        protected List<Path> doInBackground() throws Exception {
            if (Files.notExists(source)) {
                throw new FileNotFoundException("path not found");
            }

            List<Path> locations = new ArrayList<>();

            try (JsonReader reader = new JsonReader(Files.newBufferedReader(source))) {
                reader.beginArray();

                while (reader.hasNext()) {
                    locations.add(Path.of(reader.nextString()));
                }

                reader.endArray();
            }

            return locations;
        }
    }

    private static final class TagsWriter extends SwingWorkerWithDone<Void, Void> {
        private final Long2ObjectMap<FileTag> files;
        private final Path destination;

        private TagsWriter(Long2ObjectMap<FileTag> files, Path destination) {
            this.files = files;
            this.destination = destination;
        }

        @Override
        protected Void doInBackground() throws Exception {
            if (Files.exists(destination)) {
                Path backupDest = destination.getParent().resolve(".taggedindex-" +
                        Files.getLastModifiedTime(destination).toMillis());
                Files.copy(destination, backupDest, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);

                deleteExtra(destination.getParent());
            }

            try (DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(destination)))) {
                Iterator<Long2ObjectMap.Entry<FileTag>> iterator = Long2ObjectMaps.fastIterator(files);
                outputStream.writeInt(FILE_HEADER);
                outputStream.writeShort(FILE_VERSION);
                outputStream.writeInt(files.size());
                while (iterator.hasNext()) {
                    Long2ObjectMap.Entry<FileTag> entry = iterator.next();
                    write(outputStream, entry.getLongKey(), entry.getValue());
                }
            }

            Files.setLastModifiedTime(destination, FileTime.fromMillis(System.currentTimeMillis()));

            return null;
        }

        private static void deleteExtra(Path directory) throws IOException {
            try (Stream<Path> list = Files.list(directory)) {
                List<Path> candidates = new ArrayList<>(list
                        .filter(x -> x.getFileName().toString().toLowerCase().startsWith(".taggedindex-"))
                        .sorted(Comparator.comparingLong(x -> {
                            try {
                                return Files.getLastModifiedTime(x).toMillis();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }))
                        .toList());
                while (candidates.size() > 5) {
                    Path candidate = candidates.removeFirst();
                    Files.deleteIfExists(candidate);
                }
            }
        }

        private void write(DataOutputStream outputStream, long hash, FileTag file) throws IOException {
            String[] tags = file.tags;
            int tagCount = tags.length;
            assert tagCount != 0 : "tag count not empty";

            writeString(file.fileName.getFileName().toString(), outputStream);
            outputStream.writeLong(hash);
            outputStream.writeInt(tagCount);
            for (int i = 0; i < tagCount; i++) {
                String tag = tags[i];
                writeString(tag, outputStream);
            }
        }

        private void writeString(String string, DataOutputStream outputStream) throws IOException {
            int length = string.length();
            boolean longString = length > 65535;
            outputStream.writeBoolean(longString);
            if (longString) {
                byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                outputStream.writeInt(bytes.length);
                outputStream.write(bytes);
            } else {
                outputStream.writeUTF(string);
            }
        }
    }

    private static final class TagsReader extends SwingWorkerWithDone<Long2ObjectMap<FileTag>, Void> {
        private final Path source;

        private TagsReader(Path source) {
            this.source = source;
        }

        @Override
        protected Long2ObjectMap<FileTag> doInBackground() throws Exception {
            if (Files.notExists(source)) {
                throw new FileNotFoundException("path not found");
            }

            Long2ObjectMap<FileTag> list = new Long2ObjectOpenHashMap<>();
            try (DataInputStream inputStream = new DataInputStream(Files.newInputStream(source))) {
                int fileHeader = inputStream.readInt();
                if (fileHeader != FILE_HEADER) {
                    throw new IllegalStateException("file header mismatch");
                }

                short version = inputStream.readShort();
                if (version != FILE_VERSION) {
                    throw new IllegalStateException("bad file version: " + version);
                }

                int count = inputStream.readInt();
                long[] hashOut = new long[1];

                for (int i = 0; i < count; i++) {
                    FileTag tag = read(inputStream, hashOut);
                    list.put(hashOut[0], tag);
                }
            }

            return list;
        }

        private FileTag read(DataInputStream inputStream, long[] hashOut) throws IOException {
            Path filePath = Path.of(readString(inputStream));
            long hash = inputStream.readLong();

            int tagCount = inputStream.readInt();
            String[] tags = new String[tagCount];

            for (int i = 0; i < tagCount; i++) {
                tags[i] = readString(inputStream);
            }

            hashOut[0] = hash;
            return new FileTag(filePath, tags);
        }

        private String readString(DataInputStream inputStream) throws IOException {
            boolean longString = inputStream.readBoolean();
            if (longString) {
                int size = inputStream.readInt();
                byte[] bytes = new byte[size];
                if (inputStream.read(bytes) != size) {
                    throw new EOFException();
                }
                return new String(bytes);
            } else {
                return inputStream.readUTF();
            }
        }
    }

    private static final class OldTagsReader extends SwingWorkerWithDone<Long2ObjectMap<FileTag>, Void> {
        private final Path source;

        private OldTagsReader() {
            source = TAGGED_DIRECTORY.resolve("index");
        }

        @Override
        protected Long2ObjectMap<FileTag> doInBackground() throws Exception {
            if (Files.notExists(source)) {
                throw new FileNotFoundException("path not found");
            }

            Long2ObjectMap<FileTag> list = new Long2ObjectOpenHashMap<>();
            try (DataInputStream inputStream = new DataInputStream(Files.newInputStream(source))) {
                int count = inputStream.readInt();
                long[] hashOut = new long[1];

                for (int i = 0; i < count; i++) {
                    FileTag tag = read(inputStream, hashOut);
                    list.put(hashOut[0], tag);
                }
            }

            Files.deleteIfExists(source);

            return list;
        }

        private FileTag read(DataInputStream inputStream, long[] hashOut) throws IOException {
            Path originalFilePath = Path.of(readString(inputStream));
            Path fileName = originalFilePath.getFileName();
            long hash = inputStream.readLong();
            hash = calculateHash(fileName);

            int tagCount = inputStream.readInt();
            String[] tags = new String[tagCount];

            for (int i = 0; i < tagCount; i++) {
                tags[i] = readString(inputStream);
            }

            hashOut[0] = hash;
            return new FileTag(fileName, tags);
        }

        private String readString(DataInputStream inputStream) throws IOException {
            boolean longString = inputStream.readBoolean();
            if (longString) {
                int size = inputStream.readInt();
                byte[] bytes = new byte[size];
                if (inputStream.read(bytes) != size) {
                    throw new EOFException();
                }
                return new String(bytes);
            } else {
                return inputStream.readUTF();
            }
        }
    }

    private static final class IndexHashWorker extends SwingWorkerWithDone<Long2ObjectMap<FileTag>, Void> {
        private final FileTag[] files;

        private IndexHashWorker(FileTag[] files) {
            this.files = files;
        }

        @Override
        protected Long2ObjectMap<FileTag> doInBackground() throws Exception {
            return Arrays.stream(files)
                    .parallel()
                    .filter(x -> x.tags.length > 0)
                    .map(IndexHashWorker::calculate)
                    .collect(Collector.of(
                            Long2ObjectOpenHashMap::new,
                            (map, meta) -> map.put(meta.hash, meta.tag),
                            (left, right) -> {
                                left.putAll(right);
                                return left;
                            },
                            Collector.Characteristics.IDENTITY_FINISH));
        }

        private record FileTagHashMeta(long hash, FileTag tag) {}

        // creating a fuck ton of objects here huhu
        private static FileTagHashMeta calculate(FileTag fileTag) {
            return new FileTagHashMeta(calculateHash(fileTag), fileTag);
        }
    }

    private static final class FileIndexWorker extends SwingWorkerWithDone<FileTag[], Void> {
        private final Long2ObjectMap<FileTag> fileTags;
        private final Path location;

        public FileIndexWorker(Long2ObjectMap<FileTag> fileTags, Path location) {
            this.fileTags = fileTags;
            this.location = location;
        }

        @Override
        protected FileTag[] doInBackground() {
            try (Stream<Path> stream = Files.walk(location)) {
                AtomicLong progress = new AtomicLong();
                return stream
                        .filter(Files::isRegularFile)
                        .filter(x -> !x.getFileName().toString().startsWith(".taggedindex"))
                        .map(p -> new FileTag(p, p.getFileName(), new String[0]))
                        .peek(_ -> firePropertyChange(
                                "progress",
                                progress.get(),
                                progress.incrementAndGet()))
                        .peek(p -> firePropertyChange(
                                "path",
                                null,
                                p.fileName))
                        .sorted(Comparator.comparing(p -> p.fileName))
                        .map(x -> {
                            // BROOOOOO WHAT IN THE ACTUAL FUCKKK
                            if (fileTags.containsValue(x)) {
                                FileTag indexFileTag = fileTags.get(calculateHash(x));
                                return new FileTag(x.locationPath, indexFileTag.fileName, indexFileTag.tags);
                            } else {
                                return x;
                            }
                        })
                        .toArray(FileTag[]::new);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
