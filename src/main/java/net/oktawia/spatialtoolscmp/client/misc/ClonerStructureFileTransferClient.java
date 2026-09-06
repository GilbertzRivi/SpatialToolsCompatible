package net.oktawia.spatialtoolscmp.client.misc;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureTransfer;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.ExportClonerStructurePacket;
import net.oktawia.spatialtoolscmp.network.packets.ImportClonerStructurePacket;

@OnlyIn(Dist.CLIENT)
public final class ClonerStructureFileTransferClient {

    private static final String EXTENSION = ".stcstr";

    private static Path pendingExportPath = null;
    private static String pendingExportId = null;
    private static ByteArrayOutputStream pendingExportBuffer = null;

    private ClonerStructureFileTransferClient() {
    }

    public static void beginExport(int containerId, String id, String displayName) {
        if (id == null || id.isBlank()) {
            return;
        }

        String safeName = ClonerStructureLibraryStore.sanitizeName(displayName);

        if (safeName.isBlank()) {
            safeName = "structure";
        }

        String selected = TinyFileDialogs.tinyfd_saveFileDialog(
                "Export structure",
                ensureExtension(safeName),
                null,
                "CrazyAE2 structure");

        if (selected == null || selected.isBlank()) {
            return;
        }

        try {
            pendingExportPath = ensurePathExtension(Path.of(stripQuotes(selected.trim())));
            pendingExportId = id;
            pendingExportBuffer = null;
        } catch (Throwable e) {
            SpatialToolsCMP.getLOGGER().debug("invalid cloner structure export path", e);
            cancelExport();
            return;
        }

        NetworkHandler.sendToServer(new ExportClonerStructurePacket(containerId, id));
    }

    public static void beginExportStream(String id) {
        if (pendingExportPath == null || id == null || !id.equals(pendingExportId)) {
            return;
        }

        pendingExportBuffer = new ByteArrayOutputStream();
    }

    public static void appendExportStream(String id, byte[] bytes) {
        if (pendingExportBuffer == null || bytes == null || id == null || !id.equals(pendingExportId)) {
            return;
        }

        if (pendingExportBuffer.size() + bytes.length > ClonerStructureTransfer.MAX_TRANSFER_BYTES) {
            cancelExport();
            return;
        }

        pendingExportBuffer.writeBytes(bytes);
    }

    public static void completeExport(String id) {
        if (pendingExportPath == null || pendingExportBuffer == null || id == null || !id.equals(pendingExportId)) {
            cancelExport();
            return;
        }

        try {
            Files.write(pendingExportPath, pendingExportBuffer.toByteArray());
        } catch (Throwable e) {
            SpatialToolsCMP.getLOGGER().debug("failed to export cloner structure", e);
        } finally {
            cancelExport();
        }
    }

    public static void cancelExport() {
        pendingExportPath = null;
        pendingExportId = null;
        pendingExportBuffer = null;
    }

    public static void beginImport(int containerId) {
        String selected = TinyFileDialogs.tinyfd_openFileDialog(
                "Import structure",
                "",
                null,
                "CrazyAE2 structure",
                false);

        if (selected == null || selected.isBlank()) {
            return;
        }

        try {
            Path path = Path.of(stripQuotes(selected.trim()));

            if (!Files.isRegularFile(path)) {
                return;
            }

            byte[] bytes = Files.readAllBytes(path);

            if (bytes.length <= 0 || bytes.length > ClonerStructureTransfer.MAX_TRANSFER_BYTES) {
                return;
            }

            NetworkHandler.sendToServer(ImportClonerStructurePacket.begin(
                    containerId,
                    nameFromFile(path.getFileName().toString())));

            for (int offset = 0; offset < bytes.length; offset += ClonerStructureTransfer.CHUNK_BYTES) {
                int end = Math.min(bytes.length, offset + ClonerStructureTransfer.CHUNK_BYTES);
                NetworkHandler.sendToServer(ImportClonerStructurePacket.data(
                        containerId,
                        Arrays.copyOfRange(bytes, offset, end)));
            }

            NetworkHandler.sendToServer(ImportClonerStructurePacket.end(containerId));
        } catch (Throwable e) {
            SpatialToolsCMP.getLOGGER().debug("failed to import cloner structure", e);
        }
    }

    private static String ensureExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "structure" + EXTENSION;
        }

        if (fileName.endsWith(EXTENSION)) {
            return fileName;
        }

        return fileName + EXTENSION;
    }

    private static Path ensurePathExtension(Path path) {
        String fileName = path.getFileName().toString();

        if (fileName.endsWith(EXTENSION)) {
            return path;
        }

        Path parent = path.getParent();

        if (parent == null) {
            return Path.of(fileName + EXTENSION);
        }

        return parent.resolve(fileName + EXTENSION);
    }

    private static String nameFromFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        String name = fileName;

        if (name.endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }

        int dot = name.lastIndexOf('.');

        if (dot > 0) {
            name = name.substring(0, dot);
        }

        return ClonerStructureLibraryStore.sanitizeName(name);
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }

        return s;
    }
}
