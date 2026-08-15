package net.oktawia.spatialtoolscmp.mixin;

import java.util.List;
import java.util.Set;

import com.mojang.logging.LogUtils;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;

public class Plugin implements IMixinConfigPlugin {

    public static final Logger LOGGER = LogUtils.getLogger();

    private boolean ae2Loaded;
    private boolean ae2Cosmolite;
    private boolean cbMultipartLoaded;

    private static boolean isModLoaded(String modId) {
        try {
            var modList = ModList.get();
            if (modList != null) {
                return modList.isLoaded(modId);
            }

            return LoadingModList.get().getMods().stream()
                    .map(ModInfo::getModId)
                    .anyMatch(modId::equals);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isCosmoliteAe2() {
        try {
            return LoadingModList.get().getMods().stream()
                    .filter(info -> info.getModId().equals("ae2"))
                    .anyMatch(info -> {
                        String qualifier = info.getVersion().getQualifier();
                        return qualifier != null && qualifier.contains("cosmolite");
                    });
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
        this.ae2Loaded = isModLoaded("ae2");
        this.ae2Cosmolite = this.ae2Loaded && isCosmoliteAe2();
        this.cbMultipartLoaded = isModLoaded("cb_multipart");

        LOGGER.debug("ae2 loaded: {}, cosmolite fork: {}", this.ae2Loaded, this.ae2Cosmolite);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean doload = true;

        if (mixinClassName.startsWith("net.oktawia.spatialtoolscmp.mixin.ae2.")) {
            doload = ae2Loaded;
        }

        if (mixinClassName.equals("net.oktawia.spatialtoolscmp.mixin.ae2.CraftingServiceMixin")) {
            doload = ae2Loaded && !ae2Cosmolite;
        }

        if (mixinClassName.equals("net.oktawia.spatialtoolscmp.mixin.ae2.CraftingServiceCLMixin")) {
            doload = ae2Loaded && ae2Cosmolite;
        }

        if (mixinClassName.startsWith("net.oktawia.spatialtoolscmp.mixin.cbmultipart.")) {
            doload = cbMultipartLoaded;
        }

        LOGGER.info("{} load status: {}", mixinClassName, doload);
        return doload;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
