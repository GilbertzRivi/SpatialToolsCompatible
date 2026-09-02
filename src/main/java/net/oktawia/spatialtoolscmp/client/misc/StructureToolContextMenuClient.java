package net.oktawia.spatialtoolscmp.client.misc;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.client.screens.StructureToolContextMenuScreen;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.items.PortableSpatialTool;

@Mod.EventBusSubscriber(modid = SpatialToolsCMP.MODID, value = Dist.CLIENT)
public final class StructureToolContextMenuClient {

    public static final KeyMapping OPEN_CONTEXT_MENU = new KeyMapping(
            LangDefs.KEY_STRUCTURE_TOOL_CONTEXT_MENU.getTranslationKey(),
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            LangDefs.KEY_CATEGORY.getTranslationKey());

    private static boolean suppressUntilRelease = false;

    private StructureToolContextMenuClient() {
    }

    public static boolean isContextKeyDown() {
        InputConstants.Key key = OPEN_CONTEXT_MENU.getKey();

        if (key == InputConstants.UNKNOWN || key.getValue() < 0) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc == null || mc.getWindow() == null) {
            return OPEN_CONTEXT_MENU.isDown();
        }

        long window = mc.getWindow().getWindow();

        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }

        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }

        return OPEN_CONTEXT_MENU.isDown();
    }

    public static void suppressUntilRelease() {
        suppressUntilRelease = true;
    }

    public static boolean hasStructureToolInMainHand() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return false;
        }

        ItemStack held = mc.player.getMainHandItem();

        return !held.isEmpty()
                && (held.getItem() instanceof AbstractStructureCaptureToolItem
                        || held.getItem() instanceof PortableSpatialReplacer
                        || held.getItem() instanceof PortableSpatialTool);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        boolean keyDown = isContextKeyDown();

        if (!keyDown) {
            suppressUntilRelease = false;
            return;
        }

        if (suppressUntilRelease) {
            return;
        }

        if (!hasStructureToolInMainHand()) {
            return;
        }

        if (mc.screen == null) {
            mc.setScreen(new StructureToolContextMenuScreen());
        }
    }

    @Mod.EventBusSubscriber(modid = SpatialToolsCMP.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {

        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_CONTEXT_MENU);
        }
    }
}
