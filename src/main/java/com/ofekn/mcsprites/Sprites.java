package com.ofekn.mcsprites;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class Sprites implements ModInitializer {
	public static final String MOD_ID = "sprites";
	public static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");
		GhPagesSync.prepare();
	}
}