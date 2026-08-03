package net.oktawia.spatialtoolscmp.compat.ae2;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.menu.locator.MenuLocator;

public record AE2ClonerCraftingLocator(int inventorySlot) implements MenuLocator {

    @Override
    public @Nullable <T> T locate(Player player, Class<T> hostInterface) {
        if (this.inventorySlot < 0 || this.inventorySlot >= player.getInventory().getContainerSize()) {
            return null;
        }

        ItemStack stack = player.getInventory().getItem(this.inventorySlot);

        if (stack.isEmpty()) {
            return null;
        }

        AE2ClonerCraftingHost host = AE2ClonerCraftingHost.create(
                player,
                this.inventorySlot,
                stack);

        if (!hostInterface.isInstance(host)) {
            return null;
        }

        return hostInterface.cast(host);
    }

    public void writeToPacket(FriendlyByteBuf buffer) {
        buffer.writeInt(this.inventorySlot);
    }

    public static AE2ClonerCraftingLocator readFromPacket(FriendlyByteBuf buffer) {
        return new AE2ClonerCraftingLocator(buffer.readInt());
    }
}
