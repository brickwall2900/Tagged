package io.github.brickwall2900.tagged;

public class Timestamped {
    private static final int MAX_ENTRIES = 1 << 8;
    private final long[] timestampStack = new long[MAX_ENTRIES];
    private final String[] timestampMessage = new String[MAX_ENTRIES];
    private int entry = 0;

    public void push(String message) {
        assert entry < MAX_ENTRIES;
        timestampMessage[entry] = message;
        timestampStack[entry++] = System.nanoTime();
    }

    public void reportAndPop() {
        int lastEntry = entry - 1;
        assert lastEntry >= 0;
        String message = timestampMessage[lastEntry];
        long timestamp = timestampStack[lastEntry];
        System.out.printf("Timestamp: %s (%.6f seconds)%n", message, (System.nanoTime() - timestamp) / 1e9);
        entry--;
    }

    public void reportAndPopAll() {
        while (entry > 0) {
            reportAndPop();
        }
    }

    public void reportPopAndPush(String message) {
        reportAndPop();
        push(message);
    }
}
