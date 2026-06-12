package com.ofekn.mcsprites.mixin;

import com.ofekn.mcsprites.IconBuilder;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBuffers;endFrame()V"), method = "render")
	private void render(CallbackInfo info) {
		IconBuilder.build();
	}
}