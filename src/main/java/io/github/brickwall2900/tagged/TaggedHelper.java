package io.github.brickwall2900.tagged;

import javax.swing.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class TaggedHelper {
    private final Tagged tagged;
    private int threadCount;
    private ExecutorService executor;

    public TaggedHelper(Tagged tagged) {
        this.tagged = tagged;
        this.executor = Executors.newFixedThreadPool((int) (Runtime.getRuntime().availableProcessors() / 1.25));
    }

    public SwingWorkerWithDone<FileTag[], Void> startIndexingAsync(Path location) {
        return new FileIndexWorker(location);
    }

    public ExecutorService getExecutor() {
        return executor;
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

    private static final class IndexWriterWorker extends SwingWorkerWithDone<Void, Void> {
        private final FileTag[] files;

        private IndexWriterWorker(FileTag[] files) {
            this.files = files;
        }

        @Override
        protected Void doInBackground() throws Exception {
            return null;
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
