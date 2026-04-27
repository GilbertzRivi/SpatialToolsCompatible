package net.oktawia.spatialtoolscmp.compat.ae2;

import appeng.api.stacks.AEKey;
import appeng.menu.locator.MenuLocator;
import appeng.menu.me.crafting.CraftAmountMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.LoadingModList;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AE2CL {
        public static final boolean IS_AE2CL = LoadingModList.get().getMods().stream()
                .anyMatch(
                        info -> info.getModId().equals("ae2")
                                && info.getVersion().getQualifier() != null
                                && info.getVersion().getQualifier().contains("cosmolite")
                );


    private AE2CL() {
    }

    public static void open(ServerPlayer player, MenuLocator locator, AEKey whatToCraft, long amount) {
        if (AE2CL.IS_AE2CL) {
            openLong(player, locator, whatToCraft, amount);
        } else {
            openInt(player, locator, whatToCraft, amount);
        }
    }

    private static void openLong(ServerPlayer player, MenuLocator locator, AEKey whatToCraft, long amount) {
        try {
            Method method = CraftAmountMenu.class.getMethod(
                    "open",
                    ServerPlayer.class,
                    MenuLocator.class,
                    AEKey.class,
                    long.class
            );

            method.invoke(null, player, locator, whatToCraft, amount);
        } catch (NoSuchMethodException e) {
            openInt(player, locator, whatToCraft, amount);
        } catch (InvocationTargetException e) {
            SpatialToolsCMP.getLOGGER().debug("Fork detection went wrong? detected FORK", e.getCause());
        } catch (ReflectiveOperationException e) {
            SpatialToolsCMP.getLOGGER().debug("Couldn't invoke open menu with long arg", e.getCause());
        }
    }

    private static void openInt(ServerPlayer player, MenuLocator locator, AEKey whatToCraft, long amount) {
        try {
            Method method = CraftAmountMenu.class.getMethod(
                    "open",
                    ServerPlayer.class,
                    MenuLocator.class,
                    AEKey.class,
                    int.class
            );

            int clampedAmount = (int) Math.min(amount, Integer.MAX_VALUE);
            method.invoke(null, player, locator, whatToCraft, clampedAmount);
        } catch (NoSuchMethodException e) {
            openLong(player, locator, whatToCraft, amount);
        } catch (InvocationTargetException e) {
            SpatialToolsCMP.getLOGGER().debug("Fork detection went wrong? detected NO FORK", e.getCause());
        } catch (ReflectiveOperationException e) {
            SpatialToolsCMP.getLOGGER().debug("Couldn't invoke open menu with int arg", e.getCause());
        }
    }
}