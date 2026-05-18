package net.oktawia.spatialtoolscmp;

import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtensions;
import net.oktawia.spatialtoolscmp.client.renderer.PortableSpatialStoragePreviewRenderer;
import net.oktawia.spatialtoolscmp.client.renderer.extensions.*;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2BlockRegistrar;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2Compat;
import net.oktawia.spatialtoolscmp.defs.SpatialCreativeTabRegistrar;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.defs.SpatialScreenRegistrar;
import net.oktawia.spatialtoolscmp.logic.StructureToolExtensions;
import net.oktawia.spatialtoolscmp.logic.extensions.*;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import org.slf4j.Logger;

@Mod(SpatialToolsCMP.MODID)
public class SpatialToolsCMP {

    public static final String MODID = "spatialtoolscmp";
    @Getter
    private static final Logger LOGGER = LogUtils.getLogger();

    public SpatialToolsCMP() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SpatialConfig.COMMON_SPEC);

        if (IsModLoaded.AE2) {
            AE2BlockRegistrar.registerEventBus(modEventBus);
        }
        SpatialItemRegistrar.ITEMS.register(modEventBus);
        SpatialMenuRegistrar.MENU_TYPES.register(modEventBus);

        modEventBus.addListener(this::registerCreativeTab);

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static ResourceLocation makeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Spatial Tools Compatible loading...");
        event.enqueueWork(() -> {
            if (IsModLoaded.AE2) {
                AE2Compat.register();
                AE2BlockRegistrar.registerCreativeTabItems();
                StructureToolExtensions.registerClonerExtension(new AE2ClonerExtension());
            }
            if (IsModLoaded.GTCEU) {
                GTCEuStructureExtension gtceuExtension = new GTCEuStructureExtension();
                StructureToolExtensions.registerClonerExtension(gtceuExtension);
                StructureToolExtensions.registerPasteExtension(gtceuExtension);
                StructureToolExtensions.registerRemoveExtension(gtceuExtension);
            }
            if (IsModLoaded.FRAMED_BLOCKS) {
                StructureToolExtensions.registerClonerExtension(new FramedBlocksClonerExtension());
            }
            if (IsModLoaded.MEKANISM) {
                StructureToolExtensions.registerClonerExtension(new MekanismClonerExtension());
            }
            if (IsModLoaded.FASTSTONE) {
                StructureToolExtensions.registerClonerExtension(new FaststoneClonerExtension());
            }
            if (IsModLoaded.CB_MULTIPART) {
                StructureToolExtensions.registerClonerExtension(new CBMultipartStructureExtension());
            }
            NetworkHandler.registerMessages();
        });
    }

    private void registerCreativeTab(final RegisterEvent evt) {
        if (evt.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            evt.register(
                    Registries.CREATIVE_MODE_TAB,
                    SpatialCreativeTabRegistrar.ID,
                    () -> SpatialCreativeTabRegistrar.TAB
            );
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                if (IsModLoaded.AE2) {
                    BlockRenderExtensions.register(new AE2BlockRenderExtension());
                }
                if (IsModLoaded.GTCEU) {
                    BlockRenderExtensions.register(new GTCEuBlockRenderExtension());
                }
                if (IsModLoaded.FRAMED_BLOCKS) {
                    BlockRenderExtensions.register(new FramedBlocksRenderExtension());
                }
                if (IsModLoaded.MEKANISM) {
                    BlockRenderExtensions.register(new MekanismBlockRenderExtension());
                }
                if (IsModLoaded.FASTSTONE) {
                    BlockRenderExtensions.register(new FaststoneBlockRenderExtension());
                }
                if (IsModLoaded.CB_MULTIPART) {
                    BlockRenderExtensions.register(new CBMultipartBlockRenderExtension());
                }
                MinecraftForge.EVENT_BUS.register(new PortableSpatialStoragePreviewRenderer());
                SpatialScreenRegistrar.register();
            });
        }
    }
}
