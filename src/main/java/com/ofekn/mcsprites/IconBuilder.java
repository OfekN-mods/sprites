package com.ofekn.mcsprites;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class IconBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final AtomicBoolean ALREADY_STARTED = new AtomicBoolean(false);
    private static final Path ATLAS_DIR = GhPagesSync.DIR.resolve("atlas");
    private static final Path ITEMS_DIR = GhPagesSync.DIR.resolve("items");

    public static void build() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (ALREADY_STARTED.getAndSet(true)) return;
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

        fetchAtlasImage(result.texture(), textureSize, atlas)
                .thenCompose(image -> IconBuilder.saveAtlas(image, result.positions(), ops))
                .thenRun(GhPagesSync::pushGHPages)
                .thenRun(IconBuilder::closeGame)
        ;
    }

    private static void closeGame() {
        if (SpritesToggles.CLOSE_GAME) {
            Minecraft.getInstance().stop();
        }
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

    private static CompletableFuture<NativeImage> fetchAtlasImage(GpuTexture texture, int textureSize, GuiItemAtlas atlas) {
        CompletableFuture<NativeImage> result = new CompletableFuture<>();
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
            result.complete(image);
        }, 0);
        return result;
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

    private static CompletableFuture<Void> saveAtlas(NativeImage image, List<ItemAtlasPosition> positions, RegistryOps<JsonElement> ops) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(ATLAS_DIR);
                image.writeToFile(ATLAS_DIR.resolve("atlas.png"));
                String json = ItemAtlasPosition.CODEC.listOf().encodeStart(ops, positions).getOrThrow().toString();
                Files.writeString(ATLAS_DIR.resolve("items.json"), json);
                saveItems(image, positions);
                LOGGER.info("exported {} items to {}", positions.size(), ATLAS_DIR.toAbsolutePath());
                result.complete(null);
            } catch (Throwable e) {
                LOGGER.error("export failed", e);
                result.completeExceptionally(e);
            } finally {
                image.close();
            }
        });
        return result;
    }

    private static void saveItems(NativeImage atlas, List<ItemAtlasPosition> positions) throws IOException {
        Files.createDirectories(ITEMS_DIR);
        int textureSize = atlas.getWidth();
        for (ItemAtlasPosition pos : positions) {
            List<Float> uv = pos.uv();
            int x0 = Math.round(uv.get(0) * textureSize);
            int y0 = Math.round(uv.get(1) * textureSize);
            int x1 = Math.round(uv.get(2) * textureSize);
            int y1 = Math.round(uv.get(3) * textureSize);
            int w = x1 - x0;
            int h = y1 - y0;
            if (w <= 0 || h <= 0) continue;

            String itemPath = pos.item().unwrapKey().orElseThrow().identifier().getPath();
            String sanitizedName = pos.name().replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f\\x7f]", "");
            String filename = itemPath + "-" + sanitizedName + ".png";

            NativeImage sprite = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    sprite.setPixel(x, y, atlas.getPixel(x0 + x, y0 + y));
                }
            }
            sprite.writeToFile(ITEMS_DIR.resolve(filename));
            sprite.close();
        }
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
