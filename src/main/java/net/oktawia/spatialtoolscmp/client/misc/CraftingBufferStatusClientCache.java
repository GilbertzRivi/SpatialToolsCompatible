package net.oktawia.spatialtoolscmp.client.misc;

import java.util.concurrent.ConcurrentHashMap;

public final class CraftingBufferStatusClientCache {

    public static final int UNKNOWN = -1;
    public static final int NO_BUFFER = 0;
    public static final int AVAILABLE = 1;
    public static final int ALL_BUSY = 2;
    public static final int CRAFTING_SCHEDULED = 3;

    private static final ConcurrentHashMap<Integer, Integer> cache = new ConcurrentHashMap<>();

    public static void set(int containerId, int status) {
        cache.put(containerId, status);
    }

    public static int get(int containerId) {
        return cache.getOrDefault(containerId, UNKNOWN);
    }

    public static void clear(int containerId) {
        cache.remove(containerId);
    }

    private CraftingBufferStatusClientCache() {}
}
