package com.ofekn.mcsprites;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class IconBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final AtomicBoolean SHOULD_BUILD = new AtomicBoolean(true);
    private static final Path GH_PAGES_DIR = Path.of("./gh-pages");
    private static final Path OUTPUT_DIR = GH_PAGES_DIR.resolve("items");

    public static void build() {
//        if (!SHOULD_BUILD.getAndSet(false)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
//            LOGGER.error("Can't build atlas outside a level");
            return;
        }
        if (!SHOULD_BUILD.getAndSet(false)) return;
        RegistryAccess registryAccess = minecraft.level.registryAccess();
        List<ItemStack> items = getAllItems(registryAccess);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);

        int guiScale = 4;
        int slotTextureSize = 16 * guiScale;
        int textureSize = Math.max(Mth.smallestEncompassingPowerOfTwo(Mth.smallestSquareSide(items.size()) * slotTextureSize), 512);
        GuiItemAtlas atlas = new GuiItemAtlas(minecraft.gameRenderer.featureRenderDispatcher(), textureSize, slotTextureSize);

        BuildResult result = buildPositions(minecraft, atlas, items);
        if (result == null) {
            LOGGER.error("No texture found");
            return;
        }

        downloadAndExport(result.texture(), textureSize, atlas, result.positions(), ops);
    }

    private record BuildResult(GpuTexture texture, List<ItemAtlasPosition> positions) {}

    private static BuildResult buildPositions(Minecraft minecraft, GuiItemAtlas atlas, List<ItemStack> items) {
        List<ItemAtlasPosition> positions = new ArrayList<>(items.size());
        GpuTexture texture = null;
        for (ItemStack item : items) {
            TrackingItemStackRenderState state = new TrackingItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(state, item, ItemDisplayContext.GUI, null, null, 0);
            GuiItemAtlas.SlotView view = atlas.getOrUpdate(state);
            if (view == null) continue;
            texture = view.textureView().texture();
            positions.add(new ItemAtlasPosition(
                    item.typeHolder(),
                    item.getHoverName().getString(),
                    // v0/v1 are OpenGL UV space (v=1 at top); convert to image space (v=0 at top) so uv=[left,top,right,bottom]
                    List.of(view.u0(), 1f - view.v0(), view.u1(), 1f - view.v1())
            ));
        }
        if (texture == null) return null;
        return new BuildResult(texture, positions);
    }

    private static void downloadAndExport(GpuTexture texture, int textureSize, GuiItemAtlas atlas, List<ItemAtlasPosition> positions, RegistryOps<JsonElement> ops) {
        int pixelSize = texture.getFormat().blockSize();

        GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(
                () -> "ItemIcons download", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                (long) textureSize * textureSize * pixelSize);

        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, gpuBuffer, 0L, () -> {
            NativeImage image = new NativeImage(textureSize, textureSize, false);
            try (GpuBufferSlice.MappedView mapped = gpuBuffer.map(true, false)) {
                copyPixelsFlipped(mapped, image, textureSize, pixelSize);
            }
            gpuBuffer.close();
            atlas.close();

            String json = ItemAtlasPosition.CODEC.listOf().encodeStart(ops, positions).getOrThrow().toString();
            saveFiles(image, json, positions.size());
        }, 0);
    }

    private static void copyPixelsFlipped(GpuBufferSlice.MappedView mapped, NativeImage image, int textureSize, int pixelSize) {
        long src = MemoryUtil.memAddress(mapped.data());
        long dst = image.getPointer();
        int rowBytes = textureSize * pixelSize;
        // Copy rows in reverse order to flip vertically
        for (int y = 0; y < textureSize; y++) {
            MemoryUtil.memCopy(src + (long)(textureSize - 1 - y) * rowBytes, dst + (long)y * rowBytes, rowBytes);
        }
    }

    private static void saveFiles(NativeImage image, String json, int itemCount) {
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(OUTPUT_DIR);
                image.writeToFile(OUTPUT_DIR.resolve("atlas.png"));
                Files.writeString(OUTPUT_DIR.resolve("items.json"), json);
                LOGGER.info("exported {} items to {}", itemCount, OUTPUT_DIR.toAbsolutePath());

                GhPagesSync.pushGHPages(GH_PAGES_DIR);
            } catch (IOException e) {
                LOGGER.error("export failed", e);
            } finally {
                image.close();
            }
        });
    }

    private static List<ItemStack> getAllItems(RegistryAccess registryAccess) {
        var flags = FeatureFlags.REGISTRY.allFlags();
        var parameters = new CreativeModeTab.ItemDisplayParameters(flags, true, registryAccess);
        var tabs = CreativeModeTabs.allTabs();
        for (CreativeModeTab tab : tabs) {
            tab.buildContents(parameters);
        }
        return tabs.stream()
                .map(CreativeModeTab::getDisplayItems)
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }
}
