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
    private static final Path TAGGED_DIRECTORY = Path.of(System.getProperty("user.home"), ".tagged");
    private static final Path INDEX_FILE = TAGGED_DIRECTORY.resolve("index");
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
    private Long2ObjectMap<FileTag> hashToFileTagMap = new Long2ObjectOpenHashMap<>();

    public TaggedHelper(Tagged tagged) {
        this.tagged = tagged;
        executor = Executors.newFixedThreadPool((int) (Runtime.getRuntime().availableProcessors() / 1.25));
    }

    public SwingWorkerWithDone<FileTag[], Void> newIndexWorkerAsync(Path location) {
        return new FileIndexWorker(location);
    }

    public SwingWorkerWithDone<Long2ObjectMap<FileTag>, Void> newIndexHashWorkerAsync(FileTag[] tags) {
        return new IndexHashWorker(tags);
    }

    public SwingWorkerWithDone<Void, Void> newIndexWriterAsync(Long2ObjectMap<FileTag> map) {
        return new TagsWriter(map, INDEX_FILE);
    }

    public SwingWorkerWithDone<Long2ObjectMap<FileTag>, Void> newIndexReaderAsync() {
        return new TagsReader(INDEX_FILE);
    }

    public SwingWorkerWithDone<Void, Void> newLocationWriterAsync(List<Path> locations) {
        return new LocationWriter(locations, LOCATION_FILE);
    }

    public SwingWorkerWithDone<List<Path>, Void> newLocationReaderAsync() {
        return new LocationReader(LOCATION_FILE);
    }

    public boolean doesIndexExist() {
        return Files.exists(INDEX_FILE);
    }

    public boolean doesLocationExist() {
        return Files.exists(LOCATION_FILE);
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public Long2ObjectMap<FileTag> getHashToFileTagMap() {
        return hashToFileTagMap;
    }

    public void setHashToFileTagMap(Long2ObjectMap<FileTag> hashToFileTagMap) {
        this.hashToFileTagMap = hashToFileTagMap;
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
    public record FileTag(Path filePath, String[] tags) {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            FileTag fileTag = (FileTag) o;
            return Objects.equals(filePath, fileTag.filePath);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(filePath);
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
                Path backupDest = destination.getParent().resolve("index-" +
                        Files.getLastModifiedTime(destination).toMillis());
                Files.copy(destination, backupDest, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);

                deleteExtra();
            }

            try (DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(destination)))) {
                Iterator<Long2ObjectMap.Entry<FileTag>> iterator = Long2ObjectMaps.fastIterator(files);
                outputStream.writeInt(files.size());
                while (iterator.hasNext()) {
                    Long2ObjectMap.Entry<FileTag> entry = iterator.next();
                    write(outputStream, entry.getLongKey(), entry.getValue());
                }
            }

            Files.setLastModifiedTime(destination, FileTime.fromMillis(System.currentTimeMillis()));

            return null;
        }

        private static void deleteExtra() throws IOException {
            try (Stream<Path> list = Files.list(TAGGED_DIRECTORY)) {
                List<Path> candidates = list
                        .filter(x -> x.getFileName().toString().toLowerCase().startsWith("index-"))
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

        private void write(DataOutputStream outputStream, long hash, FileTag file) throws IOException {
            String[] tags = file.tags;
            int tagCount = tags.length;
            if (tagCount == 0) return;

            writeString(file.filePath.toString(), outputStream);
            outputStream.writeLong(hash);
            outputStream.writeInt(tagCount);
            for (int i = 0; i < tagCount; i++) {
                String tag = tags[i];
                writeString(tag, outputStream);
            }
        }

        private void writeString(String string, DataOutputStream outputStream) throws IOException {
            int length = string.length();
            boolean longString = length> 65535;
            outputStream.writeBoolean(longString);
            if (longString) {
                outputStream.writeInt(length);
                outputStream.write(string.getBytes(StandardCharsets.UTF_8));
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
            String filepath = fileTag.filePath.toString();
            return new FileTagHashMeta(LongHashFunction.xx().hashChars(filepath), fileTag);
        }
    }

    private static final class FileIndexWorker extends SwingWorkerWithDone<FileTag[], Void> {
        private final Path location;

        public FileIndexWorker(Path location) {
            this.location = location;
        }

        @Override
        protected FileTag[] doInBackground() {
            try (Stream<Path> stream = Files.walk(location)) {
                AtomicLong progress = new AtomicLong();
                return stream
                        .filter(Files::isRegularFile)
                        .map(p -> new FileTag(p, new String[0]))
                        .peek(_ -> firePropertyChange(
                                "progress",
                                progress.get(),
                                progress.incrementAndGet()))
                        .peek(p -> firePropertyChange(
                                "path",
                                null,
                                p.filePath))
                        .sorted(Comparator.comparing(p -> p.filePath))
                        .toArray(FileTag[]::new);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
