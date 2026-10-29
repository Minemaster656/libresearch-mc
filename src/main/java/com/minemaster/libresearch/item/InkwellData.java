package com.minemaster.libresearch.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Заряд чернильницы. Хранится в компоненте предмета, поэтому переживает
 * перезаход и синхронизируется клиенту без отдельного пакета.
 */
public record InkwellData(int charges, int capacity) {
    public static final Codec<InkwellData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("charges").forGetter(InkwellData::charges),
            Codec.INT.fieldOf("capacity").forGetter(InkwellData::capacity)
    ).apply(instance, InkwellData::new));

    public static final StreamCodec<ByteBuf, InkwellData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InkwellData::charges,
            ByteBufCodecs.VAR_INT, InkwellData::capacity,
            InkwellData::new);

    public static InkwellData full(int capacity) {
        return new InkwellData(capacity, capacity);
    }

    public InkwellData withCharges(int newCharges) {
        return new InkwellData(Math.max(0, Math.min(capacity, newCharges)), capacity);
    }

    public boolean isEmpty() {
        return charges <= 0;
    }

    public float fraction() {
        return capacity <= 0 ? 0.0F : (float) charges / (float) capacity;
    }
}
