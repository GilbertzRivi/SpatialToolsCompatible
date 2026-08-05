package net.oktawia.spatialtoolscmp.compat.gtceu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.fml.ModList;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

public final class GTCEuVersion {

    public static final String MOD_ID = "gtceu";

    private static final int REWRITE_MAJOR = 8;
    private static final String MAJOR_OVERRIDE_PROPERTY = "spatialtoolscmp.gtceu.major";
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    public static final boolean LOADED = IsModLoaded.GTCEU;
    public static final String RAW_VERSION = readRawVersion();

    private static final int[] NUMBERS = parseNumbers(RAW_VERSION);

    public static final int MAJOR = NUMBERS[0];
    public static final int MINOR = NUMBERS[1];
    public static final int PATCH = NUMBERS[2];

    public static final boolean UNKNOWN = LOADED && MAJOR == 0;
    public static final boolean IS_8_OR_NEWER = LOADED && MAJOR >= REWRITE_MAJOR;
    public static final boolean IS_LEGACY = LOADED && !IS_8_OR_NEWER;

    private GTCEuVersion() {
    }

    public static void logDetection() {
        if (!LOADED) {
            return;
        }

        if (UNKNOWN) {
            SpatialToolsCMP.getLOGGER().warn(
                    "Could not parse GregTech version '{}', assuming pre-{}.0.0 behaviour. Override with -D{}=<major>",
                    RAW_VERSION,
                    REWRITE_MAJOR,
                    MAJOR_OVERRIDE_PROPERTY);
            return;
        }

        SpatialToolsCMP.getLOGGER().info(
                "Detected GregTech {} ({}), using {} compatibility layer",
                RAW_VERSION,
                MAJOR,
                IS_8_OR_NEWER ? "8+" : "legacy");
    }

    private static String readRawVersion() {
        if (!LOADED) {
            return "";
        }

        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    private static int[] parseNumbers(String rawVersion) {
        int override = Integer.getInteger(MAJOR_OVERRIDE_PROPERTY, 0);

        if (override > 0) {
            return new int[] { override, 0, 0 };
        }

        Matcher matcher = VERSION.matcher(stripMinecraftPrefix(rawVersion));

        if (!matcher.find()) {
            return new int[] { 0, 0, 0 };
        }

        return new int[] {
                parseGroup(matcher, 1),
                parseGroup(matcher, 2),
                parseGroup(matcher, 3)
        };
    }

    private static int parseGroup(Matcher matcher, int group) {
        String value = matcher.group(group);

        return value == null ? 0 : Integer.parseInt(value);
    }

    private static String stripMinecraftPrefix(String rawVersion) {
        String minecraftVersion = ModList.get().getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");

        if (!minecraftVersion.isEmpty() && rawVersion.startsWith(minecraftVersion + "-")) {
            return rawVersion.substring(minecraftVersion.length() + 1);
        }

        return rawVersion;
    }
}
