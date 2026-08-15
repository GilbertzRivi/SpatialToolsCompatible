package net.oktawia.spatialtoolscmp.logic.buffer;

public enum BufferRequestState {
    IDLE,
    AWAITING_CONFIRM,
    CRAFTING,
    READY,
    ERROR;

    private static final BufferRequestState[] VALUES = values();

    public static BufferRequestState byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            return IDLE;
        }

        return VALUES[ordinal];
    }

    public boolean isBusy() {
        return this == AWAITING_CONFIRM || this == CRAFTING;
    }
}
