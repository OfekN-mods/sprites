package com.ofekn.mcsprites.mixin;

import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(AnimationMetadataSection.class)
public class AnimationMetadataSectionMixin {
	@Inject(at = @At("HEAD"), method = "frames", cancellable = true)
	private void frames(CallbackInfoReturnable<Optional<List<AnimationFrame>>> cir) {
		// make sure there are no animations
		cir.setReturnValue(Optional.of(List.of(new AnimationFrame(0), new AnimationFrame(0))));
		cir.cancel();
	}
}