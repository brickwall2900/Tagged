package io.github.brickwall2900.tagged;

import java.awt.image.BufferedImage;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class LRUCache<K, V> {
    private final ConcurrentHashMap<K, Entry<V>> internalMap = new ConcurrentHashMap<>();
    private final Consumer<V> onItemRemoved;
    private int maxEntries;

    public LRUCache(int maxEntries, Consumer<V> onItemRemoved) {
        this.maxEntries = maxEntries;
        this.onItemRemoved = onItemRemoved;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    private boolean removeEldestEntry(Entry<V> eldest) {
        boolean removing = internalMap.size() > maxEntries;
        if (removing && onItemRemoved != null) {
            onItemRemoved.accept(eldest.value);
        }
        return removing;
    }

    private Map.Entry<K, Entry<V>> firstEntry() {
        boolean seen = false;
        Map.Entry<K, Entry<V>> best = null;
        Comparator<Map.Entry<K, Entry<V>>> comparator =
                Comparator.comparingLong(x -> x.getValue().addedTimestamp);
        for (Map.Entry<K, Entry<V>> vEntry : internalMap.entrySet()) {
            if (!seen || comparator.compare(vEntry, best) < 0) {
                seen = true;
                best = vEntry;
            }
        }
        return best;
    }

    public void put(K key, V value) {
        Map.Entry<K, Entry<V>> entry = firstEntry();
        while (entry != null) {
            if (removeEldestEntry(entry.getValue())) {
                internalMap.remove(entry.getKey());
                entry = firstEntry();
            } else {
                break;
            }
        }
        internalMap.put(key, new Entry<>(value, System.currentTimeMillis()));
    }

    public V get(K key) {
        Entry<V> entry = internalMap.get(key);
        return entry != null ? entry.value : null;
    }

    public Collection<V> values() {
        return new ValueSet();
    }

    private class ValueSet implements Collection<V> {
        private final Collection<Entry<V>> backingSet = internalMap.values();

        @Override
        public int size() {
            return backingSet.size();
        }

        @Override
        public boolean isEmpty() {
            return backingSet.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return backingSet.contains(o);
        }

        @Override
        public Iterator<V> iterator() {
            return new MyIterator(backingSet.iterator());
        }

        private class MyIterator implements Iterator<V> {
            private final Iterator<Entry<V>> backingIterator;

            private MyIterator(Iterator<Entry<V>> backingIterator) {
                this.backingIterator = backingIterator;
            }

            @Override
            public boolean hasNext() {
                return backingIterator.hasNext();
            }

            @Override
            public V next() {
                Entry<V> entry = backingIterator.next();
                return entry.value;
            }

            @Override
            public void remove() {
                backingIterator.remove();
            }
        }

        @Override
        @SuppressWarnings({"unchecked"})
        public Object[] toArray() {
            Object[] objects = backingSet.toArray();
            Object[] entries = new Object[objects.length];
            int size = objects.length;
            for (int i = 0; i < size; i++) {
                entries[i] = ((Entry<V>) objects[i]).value;
            }
            return entries;
        }

        @Override
        public <T> T[] toArray(T[] a) {
            throw new UnsupportedOperationException("not doing all dat");
        }

        @Override
        public <T> T[] toArray(IntFunction<T[]> generator) {
            throw new UnsupportedOperationException("not doing all dat");
        }

        public boolean add(V vEntry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection<? extends V> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        public boolean removeIf(Predicate<? super V> filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            backingSet.clear();
        }

        @Override
        public boolean equals(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Spliterator<V> spliterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<V> stream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<V> parallelStream() {
            throw new UnsupportedOperationException();
        }
    }

    private record Entry<V>(V value, long addedTimestamp) {}
}
