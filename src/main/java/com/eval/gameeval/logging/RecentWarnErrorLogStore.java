package com.eval.gameeval.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RecentWarnErrorLogStore {

    private static final int MAX_BUFFER_SIZE = 5;
    private static final Deque<LogEntry> ENTRIES = new ArrayDeque<>(MAX_BUFFER_SIZE);

    private RecentWarnErrorLogStore() {
    }

    public static void add(LogEntry entry) {
        if (entry == null) {
            return;
        }

        synchronized (ENTRIES) {
            ENTRIES.addLast(entry);
            while (ENTRIES.size() > MAX_BUFFER_SIZE) {
                ENTRIES.removeFirst();
            }
        }
    }

    public static List<LogEntry> snapshot(int limit) {
        int safeLimit = Math.max(1, limit);

        synchronized (ENTRIES) {
            if (ENTRIES.isEmpty()) {
                return List.of();
            }

            int fromIndex = Math.max(ENTRIES.size() - safeLimit, 0);
            List<LogEntry> result = new ArrayList<>(Math.min(safeLimit, ENTRIES.size()));
            int index = 0;
            for (LogEntry entry : ENTRIES) {
                if (index++ >= fromIndex) {
                    result.add(entry);
                }
            }
            return List.copyOf(result);
        }
    }

    public record LogEntry(long timestampMillis, String level, String content) {
    }
}
