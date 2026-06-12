package com.ofekn.mcsprites;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record ItemAtlasPosition(Holder<Item> item, String name, List<Float> uv) {
    public static final Codec<ItemAtlasPosition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Item.CODEC.fieldOf("item").forGetter(ItemAtlasPosition::item),
                    Codec.STRING.fieldOf("name").forGetter(ItemAtlasPosition::name),
                    Codec.FLOAT.listOf(4, 4).fieldOf("uv").forGetter(ItemAtlasPosition::uv)
            ).apply(instance, ItemAtlasPosition::new)
    );

    public static String asCsv(List<ItemAtlasPosition> entries) {
        return String.join("\n", entries.stream().map(ItemAtlasPosition::toCsvString).toList());
    }

    public @NonNull String toCsvString() {
        return item.getRegisteredName() + "," + name + "," + String.join(",", uv.stream().map(Object::toString).toList());
    }
}
