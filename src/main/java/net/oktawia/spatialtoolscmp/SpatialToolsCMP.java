package net.oktawia.spatialtoolscmp;

import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.oktawia.spatialtoolscmp.items.helpers.SpatialMultiTool;
import net.oktawia.spatialtoolscmp.recipes.SpatialRecipeSerializerRegistrar;
import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtensions;
import net.oktawia.spatialtoolscmp.client.renderer.PortableSpatialPiperPreviewRenderer;
import net.oktawia.spatialtoolscmp.client.renderer.PortableSpatialReplacerPreviewRenderer;
import net.oktawia.spatialtoolscmp.client.renderer.PortableSpatialStoragePreviewRenderer;
import net.oktawia.spatialtoolscmp.client.renderer.extensions.*;
import net.oktawia.spatialtoolscmp.client.screens.CraftingBufferScreen;
import net.oktawia.spatialtoolscmp.client.screens.SpatialConfigScreen;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2BlockRegistrar;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2Compat;
import net.oktawia.spatialtoolscmp.defs.SpatialCreativeTabRegistrar;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.defs.SpatialScreenRegistrar;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtensions;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtensions;
import net.oktawia.spatialtoolscmp.logic.PiperExtensions;
import net.oktawia.spatialtoolscmp.logic.ReplacerBlacklist;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtensions;
import net.oktawia.spatialtoolscmp.logic.StructureToolExtensions;
import net.oktawia.spatialtoolscmp.logic.extensions.*;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import org.slf4j.Logger;

@Mod(SpatialToolsCMP.MODID)
public class SpatialToolsCMP {

    public static final String MODID = "spatialtoolscmp";

    public static final String MULTI_TOOL_MODEL_PROPERTY = "multi_tool";
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
        SpatialRecipeSerializerRegistrar.RECIPE_SERIALIZERS.register(modEventBus);

        modEventBus.addListener(this::registerCreativeTab);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
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
                ReplacerExtensions.register(new AE2ReplacerExtension());
                PiperExtensions.register(new AE2PiperExtension());
            }
            if (IsModLoaded.GTCEU) {
                GTCEuStructureExtension gtceuExtension = new GTCEuStructureExtension();
                StructureToolExtensions.registerClonerExtension(gtceuExtension);
                StructureToolExtensions.registerPasteExtension(gtceuExtension);
                StructureToolExtensions.registerRemoveExtension(gtceuExtension);
                ReplacerExtensions.register(new GTCEuReplacerExtension());
                PiperExtensions.register(new GTCEuPiperExtension());
            }
            if (IsModLoaded.FRAMED_BLOCKS) {
                StructureToolExtensions.registerClonerExtension(new FramedBlocksClonerExtension());
                ReplacerExtensions.register(new FramedBlocksReplacerExtension());
            }
            if (IsModLoaded.MEKANISM) {
                StructureToolExtensions.registerClonerExtension(new MekanismClonerExtension());
            }
            if (IsModLoaded.FASTSTONE) {
                StructureToolExtensions.registerClonerExtension(new FaststoneClonerExtension());
            }
            if (IsModLoaded.CB_MULTIPART) {
                StructureToolExtensions.registerClonerExtension(new CBMultipartStructureExtension());
                ReplacerExtensions.register(new CBMultipartReplacerExtension());
                PiperExtensions.register(new CBMultipartPiperExtension());
            }
            if (IsModLoaded.LASERIO) {
                StructureToolExtensions.registerClonerExtension(new LaserIOStructureExtension());
            }
            if (IsModLoaded.FLUXNETWORKS) {
                StructureToolExtensions.registerClonerExtension(new FluxNetworksStructureExtension());
            }
            if (IsModLoaded.EXTENDEDAE) {
                StructureToolExtensions.registerClonerExtension(new ExtendedAEStructureExtension());
            }
            if (IsModLoaded.LOOTR) {
                LootrStructureExtension lootrExtension = new LootrStructureExtension();
                StructureToolExtensions.registerClonerExtension(lootrExtension);
                StructureToolExtensions.registerPasteExtension(lootrExtension);
            }
            if (IsModLoaded.PRODUCTIVE_BEES) {
                StructureToolExtensions.registerClonerExtension(new ProductiveBeesClonerExtension());
            }
            if (IsModLoaded.CHISELS_BITS) {
                ChiseledBitsStructureExtension chiseledExtension = new ChiseledBitsStructureExtension();
                StructureToolExtensions.registerClonerExtension(chiseledExtension);
                StructureToolExtensions.registerPasteExtension(chiseledExtension);
            }
            if (IsModLoaded.INTEGRATED_DYNAMICS) {
                IntegratedDynamicsClonerExtension integratedDynamicsExtension = new IntegratedDynamicsClonerExtension();
                StructureToolExtensions.registerClonerExtension(integratedDynamicsExtension);
                StructureToolExtensions.registerPasteExtension(integratedDynamicsExtension);
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

    private void onConfigLoad(final ModConfigEvent.Loading event) {
        rebuildReplacerBlacklist(event.getConfig());
    }

    private void onConfigReload(final ModConfigEvent.Reloading event) {
        rebuildReplacerBlacklist(event.getConfig());
    }

    private void rebuildReplacerBlacklist(ModConfig config) {
        if (config.getSpec() == SpatialConfig.COMMON_SPEC) {
            ReplacerBlacklist.rebuild();
        }
    }

    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        ReplacerBlacklist.rebuild();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                if (IsModLoaded.CLOTH_CONFIG) {
                    ModLoadingContext.get().registerExtensionPoint(
                            ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory(
                                    (mc, parent) -> SpatialConfigScreen.create(parent)
                            )
                    );
                }
                if (IsModLoaded.AE2) {
                    BlockRenderExtensions.register(new AE2BlockRenderExtension());
                    ClientReplacerExtensions.register(new AE2ClientReplacerExtension());
                    ClientPiperExtensions.register(new AE2ClientPiperExtension());
                    MenuScreens.register(AE2BlockRegistrar.CRAFTING_BUFFER_MENU_TYPE.get(), CraftingBufferScreen::new);
                }
                if (IsModLoaded.GTCEU) {
                    BlockRenderExtensions.register(new GTCEuBlockRenderExtension());
                    ClientReplacerExtensions.register(new GTCEuClientReplacerExtension());
                }
                if (IsModLoaded.FRAMED_BLOCKS) {
                    BlockRenderExtensions.register(new FramedBlocksRenderExtension());

                    FramedBlocksClientExtension framedBlocks = new FramedBlocksClientExtension();

                    ClientReplacerExtensions.register(framedBlocks);
                    ClientPiperExtensions.register(framedBlocks);
                }
                if (IsModLoaded.MEKANISM) {
                    BlockRenderExtensions.register(new MekanismBlockRenderExtension());
                    ClientPiperExtensions.register(new MekanismClientPiperExtension());
                }
                if (IsModLoaded.FASTSTONE) {
                    BlockRenderExtensions.register(new FaststoneBlockRenderExtension());
                }
                if (IsModLoaded.CB_MULTIPART) {
                    BlockRenderExtensions.register(new CBMultipartBlockRenderExtension());

                    CBMultipartClientExtension cbMultipart = new CBMultipartClientExtension();

                    ClientReplacerExtensions.register(cbMultipart);
                    ClientPiperExtensions.register(cbMultipart);
                }
                if (IsModLoaded.CHISELS_BITS) {
                    BlockRenderExtensions.register(new ChiseledBitsBlockRenderExtension());
                }
                if (IsModLoaded.INTEGRATED_DYNAMICS) {
                    BlockRenderExtensions.register(new IntegratedDynamicsBlockRenderExtension());

                    IntegratedDynamicsClientCableExtension integratedDynamicsCables =
                            new IntegratedDynamicsClientCableExtension();

                    ClientReplacerExtensions.register(integratedDynamicsCables);
                    ClientPiperExtensions.register(integratedDynamicsCables);
                }
                MinecraftForge.EVENT_BUS.register(new PortableSpatialStoragePreviewRenderer());
                MinecraftForge.EVENT_BUS.register(new PortableSpatialReplacerPreviewRenderer());
                MinecraftForge.EVENT_BUS.register(new PortableSpatialPiperPreviewRenderer());
                SpatialScreenRegistrar.register();
                registerMultiToolModelProperty();
            });
        }

        private static void registerMultiToolModelProperty() {
            for (SpatialMultiTool.Mode mode : SpatialMultiTool.MODES) {
                ItemProperties.register(
                        mode.item(),
                        makeId(MULTI_TOOL_MODEL_PROPERTY),
                        (stack, level, entity, seed) -> SpatialMultiTool.isMultiTool(stack) ? 1.0F : 0.0F
                );
            }
        }
    }
}
