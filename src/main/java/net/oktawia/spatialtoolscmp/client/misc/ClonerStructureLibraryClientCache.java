package net.oktawia.spatialtoolscmp.client.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClonerStructureLibraryClientCache {

    private static List<Entry> entries = List.of();
    private static String selectedId = "";
    private static List<String> folders = List.of();

    private ClonerStructureLibraryClientCache() {
    }

    public record Entry(
            String id,
            String name,
            long created,
            long updated,
            int blockCount,
            String folder
    ) {
    }

    public static void set(
            List<Entry> newEntries,
            String newSelectedId
    ) {
        set(newEntries, newSelectedId, List.of());
    }

    public static void set(
            List<Entry> newEntries,
            String newSelectedId,
            List<String> newFolders
    ) {
        entries = List.copyOf(newEntries);
        selectedId = newSelectedId == null ? "" : newSelectedId;
        folders = List.copyOf(newFolders);
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static String selectedId() {
        return selectedId;
    }

    public static List<String> folders() {
        return folders;
    }

    public static boolean isSelected(String id) {
        if (id == null) {
            return selectedId.isBlank();
        }

        return selectedId.equals(id);
    }

    public static List<Entry> filtered(String query) {
        return filtered(query, null);
    }

    public static List<Entry> filtered(String query, String folderFilter) {
        List<Entry> base = entries;

        if (folderFilter != null) {
            List<Entry> inFolder = new ArrayList<>();
            for (Entry entry : base) {
                String entryFolder = entry.folder() == null ? "" : entry.folder();
                if (entryFolder.equals(folderFilter)) {
                    inFolder.add(entry);
                }
            }
            base = inFolder;
        }

        if (query == null || query.isBlank()) {
            return base;
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();

        for (Entry entry : base) {
            if (entry.name().toLowerCase(Locale.ROOT).contains(normalized)
                    || entry.id().toLowerCase(Locale.ROOT).contains(normalized)) {
                out.add(entry);
            }
        }

        return out;
    }

    public static void clear() {
        entries = List.of();
        selectedId = "";
        folders = List.of();
    }
}