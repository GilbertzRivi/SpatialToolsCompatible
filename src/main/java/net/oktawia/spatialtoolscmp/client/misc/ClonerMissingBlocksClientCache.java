package net.oktawia.spatialtoolscmp.client.misc;

public final class ClonerMissingBlocksClientCache {

    private static volatile boolean missing = false;

    private ClonerMissingBlocksClientCache() {
    }

    public static void set(boolean value) {
        missing = value;
    }

    public static boolean isMissing() {
        return missing;
    }
}
