package net.oktawia.spatialtoolscmp.compat.ae2;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.ISubMenu;

import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;

public final class AE2ClonerCraftingHost extends ItemMenuHost implements ISubMenuHost, IActionHost {

    @Nullable
    private final IActionHost actionHost;

    public AE2ClonerCraftingHost(
            Player player,
            int slot,
            ItemStack itemStack,
            @Nullable IActionHost actionHost) {
        super(player, slot, itemStack);
        this.actionHost = actionHost;
    }

    public static AE2ClonerCraftingHost create(Player player, int slot, ItemStack itemStack) {
        IActionHost actionHost = null;

        if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverLevel) {
            IWirelessAccessPoint wap = AE2GridLinkableHandler.getLinkedWirelessAccessPoint(
                    serverLevel,
                    itemStack);

            if (wap != null && wap.isActive()) {
                actionHost = wap;
            }
        }

        return new AE2ClonerCraftingHost(player, slot, itemStack, actionHost);
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return this.actionHost == null ? null : this.actionHost.getActionableNode();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = getItemStack();

        NetworkHooks.openScreen(
                serverPlayer,
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return stack.getHoverName();
                    }

                    @Override
                    public @Nullable AbstractContainerMenu createMenu(
                            int containerId,
                            Inventory inventory,
                            Player menuPlayer) {
                        return SpatialMenuRegistrar.PORTABLE_SPATIAL_CLONER_MENU
                                .get()
                                .create(containerId, inventory);
                    }
                });
    }

    @Override
    public ItemStack getMainMenuIcon() {
        ItemStack icon = getItemStack().copy();
        icon.setCount(1);
        return icon;
    }
}
