package io.github.brickwall2900.tagged;

import javax.swing.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class SwingWorkerWithDone<T, V> extends SwingWorker<T, V> {
    private DoneAcceptor<T> onDone;

    public SwingWorkerWithDone(DoneAcceptor<T> onDone) {
        this.onDone = onDone;
    }

    public SwingWorkerWithDone() {
    }

    public DoneAcceptor<T> getOnDone() {
        return onDone;
    }

    public void onDone(DoneAcceptor<T> onDone) {
        this.onDone = onDone;
    }

    @Override
    protected void done() {
        super.done();
        if (onDone != null) {
            try {
                onDone.done(get(), null);
            } catch (InterruptedException | ExecutionException e) {
                onDone.done(null, e);
            }
        }
    }

    @FunctionalInterface
    public interface DoneAcceptor<T> {
        void done(T value, Throwable exception);
    }
}
