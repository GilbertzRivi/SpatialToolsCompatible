package net.oktawia.spatialtoolscmp.compat.gtceu;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

final class GTCEuBridgeLoader {

    private GTCEuBridgeLoader() {
    }

    static <T> T load(String v7ClassName, String v8ClassName, Class<T> type, T fallback) {
        if (!GTCEuVersion.LOADED) {
            return fallback;
        }

        String className = GTCEuVersion.IS_8_OR_NEWER ? v8ClassName : v7ClassName;

        try {
            return type.cast(Class.forName(className).getDeclaredConstructor().newInstance());
        } catch (Throwable error) {
            SpatialToolsCMP.getLOGGER().error(
                    "Failed to load GregTech bridge {} for version {}",
                    className,
                    GTCEuVersion.RAW_VERSION,
                    error);
            return fallback;
        }
    }
}
