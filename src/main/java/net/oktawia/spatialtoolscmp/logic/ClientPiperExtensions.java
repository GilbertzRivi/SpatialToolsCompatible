package net.oktawia.spatialtoolscmp.logic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClientPiperExtensions {

    private static final CopyOnWriteArrayList<ClientPiperExtension> EXTENSIONS = new CopyOnWriteArrayList<>();

    private ClientPiperExtensions() {
    }

    public static void register(ClientPiperExtension extension) {
        if (extension != null && !EXTENSIONS.contains(extension)) {
            EXTENSIONS.add(extension);
        }
    }

    public static List<ClientPiperExtension> get() {
        return EXTENSIONS;
    }
}
