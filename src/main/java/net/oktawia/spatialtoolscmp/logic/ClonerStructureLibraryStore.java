package net.oktawia.spatialtoolscmp.logic;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public final class ClonerStructureLibraryStore {

    public static final int MAX_NAME_LENGTH = 32;

    private static final String DIR_NAME = "crazyae2addons/structure_tools/cloner_library";
    private static final String STRUCTURES_DIR_NAME = "structures";
    private static final String INDEX_FILE_NAME = "index.nbt";

    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_CREATED = "created";
    private static final String KEY_UPDATED = "updated";
    private static final String KEY_BLOCK_COUNT = "blockCount";
    private static final String KEY_FOLDER = "folder";

    private ClonerStructureLibraryStore() {
    }

    private static boolean isValidId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public record Entry(
            String id,
            String name,
            long created,
            long updated,
            int blockCount,
            String folder) {
    }

    private static Path getRoot(MinecraftServer server) throws IOException {
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
        Files.createDirectories(root);
        return root;
    }

    private static Path getOwnerRoot(MinecraftServer server, UUID owner) throws IOException {
        Path root = getRoot(server).resolve(owner.toString());
        Files.createDirectories(root);
        Files.createDirectories(root.resolve(STRUCTURES_DIR_NAME));
        return root;
    }

    private static Path getIndexPath(MinecraftServer server, UUID owner) throws IOException {
        return getOwnerRoot(server, owner).resolve(INDEX_FILE_NAME);
    }

    private static Path getStructuresRoot(MinecraftServer server, UUID owner) throws IOException {
        Path root = getOwnerRoot(server, owner).resolve(STRUCTURES_DIR_NAME);
        Files.createDirectories(root);
        return root;
    }

    private static Path getStructurePath(MinecraftServer server, UUID owner, String id) throws IOException {
        return getStructuresRoot(server, owner).resolve(id + ".nbt");
    }

    public static List<Entry> list(MinecraftServer server, UUID owner) throws IOException {
        CompoundTag index = readIndex(server, owner);

        if (!index.contains(KEY_ENTRIES, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag entriesTag = index.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < entriesTag.size(); i++) {
            CompoundTag row = entriesTag.getCompound(i);
            String id = row.getString(KEY_ID);

            if (id.isBlank()) {
                continue;
            }

            entries.add(new Entry(
                    id,
                    sanitizeName(row.getString(KEY_NAME)),
                    row.getLong(KEY_CREATED),
                    row.getLong(KEY_UPDATED),
                    Math.max(0, row.getInt(KEY_BLOCK_COUNT)),
                    row.getString(KEY_FOLDER)));
        }

        entries.sort(Comparator
                .comparingLong(Entry::updated)
                .reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));

        return entries;
    }

    public static @Nullable Entry get(MinecraftServer server, UUID owner, String id) throws IOException {
        if (!isValidId(id)) {
            return null;
        }

        for (Entry entry : list(server, owner)) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }

        return null;
    }

    public static boolean exists(MinecraftServer server, UUID owner, String id) throws IOException {
        return isValidId(id) && Files.exists(getStructurePath(server, owner, id));
    }

    public static @Nullable CompoundTag load(MinecraftServer server, UUID owner, String id) throws IOException {
        if (!isValidId(id)) {
            return null;
        }

        Path path = getStructurePath(server, owner, id);

        if (!Files.exists(path)) {
            return null;
        }

        try (InputStream in = Files.newInputStream(path)) {
            return NbtIo.readCompressed(in);
        }
    }

    public static Entry saveNew(
            MinecraftServer server,
            UUID owner,
            CompoundTag tag,
            @Nullable String requestedName) throws IOException {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        List<Entry> entries = new ArrayList<>(list(server, owner));
        String name = sanitizeName(requestedName);

        if (name.isBlank()) {
            name = nextDefaultName(entries);
        }

        Entry entry = new Entry(
                id,
                name,
                now,
                now,
                countBlocks(tag),
                "");

        writeStructure(server, owner, id, tag);

        entries.add(entry);
        writeIndex(server, owner, entries);

        return entry;
    }

    public static @Nullable Entry saveExisting(
            MinecraftServer server,
            UUID owner,
            String id,
            CompoundTag tag) throws IOException {
        if (!isValidId(id)) {
            return null;
        }

        List<Entry> entries = new ArrayList<>(list(server, owner));
        long now = System.currentTimeMillis();

        for (int i = 0; i < entries.size(); i++) {
            Entry old = entries.get(i);

            if (!old.id().equals(id)) {
                continue;
            }

            Entry updated = new Entry(
                    old.id(),
                    old.name(),
                    old.created(),
                    now,
                    countBlocks(tag),
                    old.folder());

            writeStructure(server, owner, id, tag);
            entries.set(i, updated);
            writeIndex(server, owner, entries);

            return updated;
        }

        return null;
    }

    public static Entry saveForCurrentSelection(
            MinecraftServer server,
            UUID owner,
            ItemStack stack,
            CompoundTag tag) throws IOException {
        Entry created = saveNew(server, owner, tag, null);

        StructureToolStackState.setSelectedClonerLibraryEntry(
                stack,
                owner,
                created.id());

        return created;
    }

    public static boolean rename(
            MinecraftServer server,
            UUID owner,
            String id,
            String requestedName) throws IOException {
        if (!isValidId(id)) {
            return false;
        }

        String name = sanitizeName(requestedName);

        if (name.isBlank()) {
            return false;
        }

        List<Entry> entries = new ArrayList<>(list(server, owner));
        boolean changed = false;

        for (int i = 0; i < entries.size(); i++) {
            Entry old = entries.get(i);

            if (!old.id().equals(id)) {
                continue;
            }

            entries.set(i, new Entry(
                    old.id(),
                    name,
                    old.created(),
                    System.currentTimeMillis(),
                    old.blockCount(),
                    old.folder()));

            changed = true;
            break;
        }

        if (changed) {
            writeIndex(server, owner, entries);
        }

        return changed;
    }

    public static boolean delete(MinecraftServer server, UUID owner, String id) throws IOException {
        if (!isValidId(id)) {
            return false;
        }

        boolean deleted = Files.deleteIfExists(getStructurePath(server, owner, id));

        if (!deleted) {
            return false;
        }

        List<Entry> entries = new ArrayList<>(list(server, owner));
        entries.removeIf(entry -> entry.id().equals(id));
        writeIndex(server, owner, entries);

        return true;
    }

    public static @Nullable CompoundTag loadSelectedOrMigrateLegacy(
            MinecraftServer server,
            UUID fallbackOwner,
            ItemStack stack) throws IOException {
        String selectedId = StructureToolStackState.getStructureId(stack);

        if (selectedId.isBlank()) {
            return null;
        }

        UUID owner = StructureToolStackState.getClonerLibraryOwner(stack);

        if (owner != null) {
            CompoundTag tag = load(server, owner, selectedId);

            if (tag != null) {
                return tag;
            }

            return null;
        }

        CompoundTag legacyTag = StructureToolStructureStore.load(server, selectedId);

        if (legacyTag == null) {
            return null;
        }

        Entry migrated = saveNew(server, fallbackOwner, legacyTag, null);
        StructureToolStackState.setSelectedClonerLibraryEntry(stack, fallbackOwner, migrated.id());

        return legacyTag;
    }

    public static byte[] exportBytes(MinecraftServer server, UUID owner, String id) throws IOException {
        if (!isValidId(id)) {
            return new byte[0];
        }

        CompoundTag tag = load(server, owner, id);

        if (tag == null) {
            return new byte[0];
        }

        return TemplateUtil.compressNbt(tag);
    }

    private static final long IMPORT_SIZE_LIMIT = 64L * 1024 * 1024;

    public static Entry importBytes(
            MinecraftServer server,
            UUID owner,
            byte[] bytes,
            @Nullable String requestedName) throws IOException {
        CompoundTag tag;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes));
                DataInputStream data = new DataInputStream(gzip)) {
            tag = NbtIo.read(data, new NbtAccounter(IMPORT_SIZE_LIMIT));
        }

        if (tag == null) {
            throw new IOException("Failed to parse NBT from import bytes");
        }

        return saveNew(server, owner, tag, requestedName);
    }

    public static String sanitizeName(@Nullable String raw) {
        if (raw == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < raw.length() && out.length() < MAX_NAME_LENGTH; i++) {
            char c = raw.charAt(i);

            if (Character.isISOControl(c)) {
                continue;
            }

            out.append(c);
        }

        return out.toString().trim();
    }

    private static String nextDefaultName(List<Entry> existing) {
        int index = existing.size() + 1;

        while (true) {
            String candidate = "Clone " + index;
            boolean used = false;

            for (Entry entry : existing) {
                if (entry.name().equalsIgnoreCase(candidate)) {
                    used = true;
                    break;
                }
            }

            if (!used) {
                return candidate;
            }

            index++;
        }
    }

    private static int countBlocks(CompoundTag tag) {
        return TemplateUtil.parseRawBlocksFromTag(tag).size();
    }

    private static List<String> readFolderList(MinecraftServer server, UUID owner) throws IOException {
        CompoundTag index = readIndex(server, owner);

        if (!index.contains(KEY_FOLDERS, Tag.TAG_LIST)) {
            return new ArrayList<>();
        }

        ListTag foldersTag = index.getList(KEY_FOLDERS, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < foldersTag.size(); i++) {
            String name = foldersTag.getString(i);
            if (!name.isBlank()) {
                result.add(name);
            }
        }

        return result;
    }

    public static List<String> listFolders(MinecraftServer server, UUID owner) throws IOException {
        return readFolderList(server, owner);
    }

    public static boolean createFolder(MinecraftServer server, UUID owner, String name) throws IOException {
        String sanitized = sanitizeName(name);

        if (sanitized.isBlank()) {
            return false;
        }

        List<String> folders = new ArrayList<>(readFolderList(server, owner));

        for (String f : folders) {
            if (f.equalsIgnoreCase(sanitized)) {
                return false;
            }
        }

        folders.add(sanitized);
        writeIndex(server, owner, new ArrayList<>(list(server, owner)), folders);

        return true;
    }

    public static boolean deleteFolder(MinecraftServer server, UUID owner, String name) throws IOException {
        if (name == null || name.isBlank()) {
            return false;
        }

        List<Entry> entries = list(server, owner);

        for (Entry entry : entries) {
            if (name.equals(entry.folder())) {
                return false;
            }
        }

        List<String> folders = new ArrayList<>(readFolderList(server, owner));
        boolean removed = folders.removeIf(f -> f.equals(name));

        if (removed) {
            writeIndex(server, owner, new ArrayList<>(entries), folders);
        }

        return removed;
    }

    public static boolean moveToFolder(
            MinecraftServer server,
            UUID owner,
            String structureId,
            String folderName) throws IOException {
        if (!isValidId(structureId)) {
            return false;
        }

        String targetFolder = folderName == null ? "" : sanitizeName(folderName);

        List<Entry> entries = new ArrayList<>(list(server, owner));
        boolean changed = false;

        for (int i = 0; i < entries.size(); i++) {
            Entry old = entries.get(i);

            if (!old.id().equals(structureId)) {
                continue;
            }

            entries.set(i, new Entry(
                    old.id(), old.name(), old.created(), old.updated(), old.blockCount(), targetFolder));
            changed = true;
            break;
        }

        if (changed) {
            writeIndex(server, owner, entries);
        }

        return changed;
    }

    private static void writeStructure(
            MinecraftServer server,
            UUID owner,
            String id,
            CompoundTag tag) throws IOException {
        Path path = getStructurePath(server, owner, id);

        try (OutputStream out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(tag, out);
        }
    }

    private static CompoundTag readIndex(MinecraftServer server, UUID owner) throws IOException {
        Path path = getIndexPath(server, owner);

        if (!Files.exists(path)) {
            return new CompoundTag();
        }

        try (InputStream in = Files.newInputStream(path)) {
            CompoundTag tag = NbtIo.readCompressed(in);
            return tag == null ? new CompoundTag() : tag;
        }
    }

    private static void writeIndex(
            MinecraftServer server,
            UUID owner,
            List<Entry> entries) throws IOException {
        writeIndex(server, owner, entries, null);
    }

    private static void writeIndex(
            MinecraftServer server,
            UUID owner,
            List<Entry> entries,
            List<String> folders) throws IOException {
        CompoundTag root = new CompoundTag();
        ListTag entriesTag = new ListTag();

        entries.sort(Comparator
                .comparingLong(Entry::updated)
                .reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));

        for (Entry entry : entries) {
            CompoundTag row = new CompoundTag();

            row.putString(KEY_ID, entry.id());
            row.putString(KEY_NAME, sanitizeName(entry.name()));
            row.putLong(KEY_CREATED, entry.created());
            row.putLong(KEY_UPDATED, entry.updated());
            row.putInt(KEY_BLOCK_COUNT, Math.max(0, entry.blockCount()));
            row.putString(KEY_FOLDER, entry.folder() == null ? "" : entry.folder());

            entriesTag.add(row);
        }

        root.put(KEY_ENTRIES, entriesTag);

        List<String> folderList = folders != null ? folders : readFolderList(server, owner);
        ListTag foldersTag = new ListTag();
        for (String f : folderList) {
            foldersTag.add(StringTag.valueOf(f));
        }
        root.put(KEY_FOLDERS, foldersTag);

        Path path = getIndexPath(server, owner);

        try (OutputStream out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(root, out);
        }
    }
}
