package net.oktawia.spatialtoolscmp.client.misc;

import net.minecraft.resources.ResourceLocation;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

public enum Icon {
    CRAFT_HAMMER("textures/gui/icons/craft_hammer.png"),
    CHECK("textures/gui/icons/check.png"),
    CROSS("textures/gui/icons/cross.png"),
    PLUS("textures/gui/icons/plus.png"),
    MINUS("textures/gui/icons/minus.png"),
    ARROW_LEFT("textures/gui/icons/arrow_left.png"),
    ARROW_RIGHT("textures/gui/icons/arrow_right.png"),
    ARROW_UP("textures/gui/icons/arrow_up.png"),
    ARROW_DOWN("textures/gui/icons/arrow_down.png"),
    ARROW_FRONT("textures/gui/icons/arrow_front.png"),
    ARROW_BACK("textures/gui/icons/arrow_back.png"),
    ROTATE("textures/gui/icons/rotate.png");

    private final ResourceLocation texture;

    Icon(String path) {
        this.texture = ResourceLocation.fromNamespaceAndPath(SpatialToolsCMP.MODID, path);
    }

    public ResourceLocation texture() {
        return this.texture;
    }
}