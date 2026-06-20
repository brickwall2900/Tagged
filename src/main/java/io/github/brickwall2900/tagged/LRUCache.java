package io.github.brickwall2900.tagged;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private int maxEntries;
    private final Consumer<V> onItemRemoved;

    public LRUCache(int maxEntries, Consumer<V> onItemRemoved) {
        super(maxEntries, 0.75f, true); // accessOrder = true for LRU ordering
        this.maxEntries = maxEntries;
        this.onItemRemoved = onItemRemoved;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean removing = size() > maxEntries;
        if (removing && onItemRemoved != null) {
            onItemRemoved.accept(eldest.getValue());
        }
        return removing;
    }

    @Override
    public V put(K key, V value) {
        for (Map.Entry<K, V> entry = firstEntry();
             entry != null;
             entry = firstEntry()) {
            if (removeEldestEntry(entry)) {
                pollFirstEntry();
            } else {
                break;
            }
        }
        return super.put(key, value);
    }
}
