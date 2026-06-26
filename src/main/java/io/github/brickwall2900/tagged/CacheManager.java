package io.github.brickwall2900.tagged;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class CacheManager {
    private final Path cacheDirectory;
    private long cacheMaxSize = 1024 * 1024 * 1024; // 1 GiB

    public CacheManager(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    private String filenameToHash(String id) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException _) {
        }
        if (digest != null) {
            return HexFormat.of().formatHex(digest.digest(id.getBytes(StandardCharsets.UTF_8)));
        } else {
            return String.valueOf(id.hashCode());
        }
    }

    public Path newCacheEntry(String name) {
        return cacheDirectory.resolve(filenameToHash(name));
    }

    public long getCacheCurrentSize() {
        try (Stream<Path> list = Files.walk(cacheDirectory)) {
            return list.mapToLong(x -> {
                try {
                    return Files.size(x);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).sum();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public long getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(long cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    public void manageCache() throws IOException {
        if (cacheMaxSize == 0) return;
        synchronized (this) {
            List<Path> deleted = new ArrayList<>();
            long cacheCurrentSize = getCacheCurrentSize();
            long cacheMaxSize = this.cacheMaxSize;
            try (Stream<Path> stream = Files.walk(cacheDirectory)) {
                List<Path> files = new ArrayList<>(stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparingLong(x -> {
                            try {
                                return Files.getLastModifiedTime((Path) x).toMillis();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }))
                        .toList());
                while (cacheCurrentSize > cacheMaxSize && !files.isEmpty()) {
                    Path firstFile = files.getFirst();
                    deleted.add(files.removeFirst());

                    cacheCurrentSize -= Files.size(firstFile);
                    cacheMaxSize = this.cacheMaxSize;
                }
            }
            deleted.parallelStream().forEach(x -> {
                try {
                    Files.deleteIfExists(x);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
