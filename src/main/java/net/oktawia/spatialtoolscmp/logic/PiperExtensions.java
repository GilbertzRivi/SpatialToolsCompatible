package net.oktawia.spatialtoolscmp.logic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PiperExtensions {

    private static final CopyOnWriteArrayList<PiperExtension> EXTENSIONS = new CopyOnWriteArrayList<>();

    private PiperExtensions() {
    }

    public static void register(PiperExtension extension) {
        if (extension != null && !EXTENSIONS.contains(extension)) {
            EXTENSIONS.add(extension);
        }
    }

    public static List<PiperExtension> get() {
        return EXTENSIONS;
    }
}
