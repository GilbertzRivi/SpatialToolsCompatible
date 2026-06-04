package net.oktawia.spatialtoolscmp.logic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ReplacerExtensions {

    private static final CopyOnWriteArrayList<ReplacerExtension> EXTENSIONS = new CopyOnWriteArrayList<>();

    private ReplacerExtensions() {
    }

    public static void register(ReplacerExtension extension) {
        if (!EXTENSIONS.contains(extension)) {
            EXTENSIONS.add(extension);
        }
    }

    public static List<ReplacerExtension> get() {
        return EXTENSIONS;
    }
}
