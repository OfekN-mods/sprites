package com.ofekn.mcsprites.mixin;

import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnimationFrame.class)
public class AnimationFrameMixin {
	@Inject(at = @At("HEAD"), method = "index", cancellable = true)
	private void index(CallbackInfoReturnable<Integer> cir) {
		// make sure there are no animations
		cir.setReturnValue(0);
		cir.cancel();
	}
}