package net.oktawia.spatialtoolscmp.logic;

public final class ClonerStructureTransfer {

    public static final int SIGNAL_BEGIN = 0;
    public static final int SIGNAL_DATA = 1;
    public static final int SIGNAL_END = 2;

    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_TRANSFER_BYTES = 16 * 1024 * 1024;
    public static final int MAX_ID_LENGTH = 128;

    private ClonerStructureTransfer() {
    }
}
