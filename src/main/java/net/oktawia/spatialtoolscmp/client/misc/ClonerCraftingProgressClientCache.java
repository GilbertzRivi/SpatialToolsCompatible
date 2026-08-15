package net.oktawia.spatialtoolscmp.client.misc;

import java.util.concurrent.ConcurrentHashMap;

import net.oktawia.spatialtoolscmp.logic.buffer.BufferRequestState;

public final class ClonerCraftingProgressClientCache {

    public record Progress(int state, long done, long total, String label) {

        public BufferRequestState requestState() {
            return BufferRequestState.byOrdinal(state);
        }

        public boolean visible() {
            BufferRequestState requestState = requestState();
            return total > 0 && (requestState.isBusy() || requestState == BufferRequestState.READY);
        }

        public float fraction() {
            if (total <= 0) {
                return 0.0F;
            }

            return Math.min(1.0F, (float) ((double) done / (double) total));
        }
    }

    private static final Progress NONE = new Progress(BufferRequestState.IDLE.ordinal(), 0, 0, "");

    private static final ConcurrentHashMap<Integer, Progress> CACHE = new ConcurrentHashMap<>();

    private ClonerCraftingProgressClientCache() {
    }

    public static void set(int containerId, Progress progress) {
        CACHE.put(containerId, progress == null ? NONE : progress);
    }

    public static Progress get(int containerId) {
        return CACHE.getOrDefault(containerId, NONE);
    }

    public static void clear(int containerId) {
        CACHE.remove(containerId);
    }
}
