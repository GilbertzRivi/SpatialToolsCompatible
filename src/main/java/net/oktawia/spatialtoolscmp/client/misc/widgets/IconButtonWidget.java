package net.oktawia.spatialtoolscmp.client.misc.widgets;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import lombok.Getter;
import lombok.Setter;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.client.misc.Icon;

public class IconButtonWidget extends AbstractWidget {

    private static final int DEFAULT_SIZE = 16;

    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpatialToolsCMP.MODID,
            "textures/gui/icons/button_bg.png");

    private static final ResourceLocation BACKGROUND_TEXTURE_DARK = ResourceLocation.fromNamespaceAndPath(
            SpatialToolsCMP.MODID,
            "textures/gui/icons/button_bg_dark.png");

    @Setter
    @Getter
    private Icon icon;

    @Setter
    private boolean darkBackground = false;

    private Consumer<IconButtonWidget> onPress;

    public IconButtonWidget(Icon icon, Runnable onPress) {
        this(0, 0, DEFAULT_SIZE, DEFAULT_SIZE, icon, onPress);
    }

    public IconButtonWidget(Icon icon, Consumer<IconButtonWidget> onPress) {
        this(0, 0, DEFAULT_SIZE, DEFAULT_SIZE, icon, onPress);
    }

    public IconButtonWidget(int x, int y, int width, int height, Icon icon, Runnable onPress) {
        this(x, y, width, height, icon, button -> {
            if (onPress != null) {
                onPress.run();
            }
        });
    }

    public IconButtonWidget(int x, int y, int width, int height, Icon icon, Consumer<IconButtonWidget> onPress) {
        super(x, y, width, height, Component.empty());

        this.icon = icon;
        this.onPress = onPress == null ? button -> {
        } : onPress;
    }

    public void setOnPress(Runnable onPress) {
        this.onPress = button -> {
            if (onPress != null) {
                onPress.run();
            }
        };
    }

    public void setOnPress(Consumer<IconButtonWidget> onPress) {
        this.onPress = onPress == null ? button -> {
        } : onPress;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) {
            return false;
        }

        if (button != 0) {
            return false;
        }

        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        this.playDownSound(Minecraft.getInstance().getSoundManager());
        this.onPress.accept(this);

        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        int x = this.getX();
        int y = this.getY();

        guiGraphics.blit(
                this.darkBackground ? BACKGROUND_TEXTURE_DARK : BACKGROUND_TEXTURE,
                x,
                y,
                0,
                0,
                this.width,
                this.height,
                this.width,
                this.height);

        if (this.icon != null) {
            guiGraphics.blit(
                    this.icon.texture(),
                    x,
                    y,
                    0,
                    0,
                    this.width,
                    this.height,
                    this.width,
                    this.height);
        }

        if (!this.active) {
            guiGraphics.fill(x, y, x + this.width, y + this.height, 0x88000000);
            return;
        }

        if (this.isMouseOver(mouseX, mouseY)) {
            guiGraphics.fill(x, y, x + this.width, y + this.height, 0x22FFFFFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
