package com.ofekn.mcsprites.mixin;

import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiItemAtlas.class)
public class GuiItemAtlasMixin {
	@ModifyArg(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createTexture(Ljava/lang/String;ILcom/mojang/renderpearl/api/GpuFormat;IIII)Lcom/mojang/renderpearl/api/textures/GpuTexture;",
					ordinal = 0
			),
			index = 1
	)
	private int addCopySrcUsage(int usage) {
		return usage | GpuTexture.USAGE_COPY_SRC;
	}
}
