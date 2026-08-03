package net.oktawia.spatialtoolscmp.client.misc.guide;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

public class GuideLoader implements ResourceManagerReloadListener {

    private static final String FALLBACK_LANGUAGE = "en_us";

    private static final Map<ResourceLocation, List<GuideBlock>> CACHE = new HashMap<>();

    public static List<GuideBlock> get(Item item) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);

        if (itemId == null) {
            return List.of();
        }

        return CACHE.computeIfAbsent(itemId, GuideLoader::load);
    }

    public static boolean hasGuide(Item item) {
        return !get(item).isEmpty();
    }

    private static List<GuideBlock> load(ResourceLocation itemId) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();

        List<String> lines = read(path(itemId, language));

        if (lines == null && !FALLBACK_LANGUAGE.equals(language)) {
            lines = read(path(itemId, FALLBACK_LANGUAGE));
        }

        return lines == null ? List.of() : GuideMarkdown.parse(lines);
    }

    private static ResourceLocation path(ResourceLocation itemId, String language) {
        return ResourceLocation.fromNamespaceAndPath(
                itemId.getNamespace(),
                "guides/" + language + "/" + itemId.getPath() + ".md");
    }

    private static List<String> read(ResourceLocation path) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(path);

        if (resource.isEmpty()) {
            return null;
        }

        try (BufferedReader reader = resource.get().openAsReader()) {
            return reader.lines().toList();
        } catch (IOException exception) {
            SpatialToolsCMP.getLOGGER().error("Failed to read guide {}", path, exception);
            return null;
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        CACHE.clear();
    }
}
