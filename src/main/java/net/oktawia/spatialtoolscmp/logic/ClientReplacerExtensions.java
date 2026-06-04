package net.oktawia.spatialtoolscmp.logic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClientReplacerExtensions {

    private static final List<ClientReplacerExtension> EXTENSIONS = new CopyOnWriteArrayList<>();

    private ClientReplacerExtensions() {
    }

    public static void register(ClientReplacerExtension extension) {
        if (extension == null || EXTENSIONS.contains(extension)) return;
        EXTENSIONS.add(extension);
    }

    public static List<ClientReplacerExtension> get() {
        return EXTENSIONS;
    }
}
