package com.ofekn.mcsprites;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

public class SpritesClient implements ClientModInitializer {
	public static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitializeClient() {
		LOGGER.info("Hello Fabric Client world!");
	}
}