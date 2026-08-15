package net.oktawia.spatialtoolscmp.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.service.CraftingService;

import net.oktawia.spatialtoolscmp.compat.ae2.CraftingBufferRequests;

@Mixin(value = CraftingService.class, remap = false)
public class CraftingServiceCLMixin {

    @ModifyArg(method = "submitJob", index = 3, at = @At(value = "INVOKE", target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;submitJob(Lappeng/api/networking/IGrid;Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/security/IActionSource;Lappeng/api/networking/crafting/ICraftingRequester;Z)Lappeng/api/networking/crafting/ICraftingSubmitResult;"))
    private ICraftingRequester spatialtoolscmp$bufferRequester(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource src,
            ICraftingRequester requester,
            boolean isFollowing) {
        return CraftingBufferRequests.resolveRequester(plan, requester);
    }

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void spatialtoolscmp$onSubmitResult(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            boolean isFollowing,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        CraftingBufferRequests.onSubmitResult(job, cir.getReturnValue());
    }
}
