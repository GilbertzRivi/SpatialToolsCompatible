package net.oktawia.spatialtoolscmp.logic;

public record ReplacerContext(int radius, int hardCapMax, ConnectivityMode connectivityMode, boolean strictBlockstate) {

    public enum ConnectivityMode {
        DIRECT,
        DIAGONAL
    }
}
