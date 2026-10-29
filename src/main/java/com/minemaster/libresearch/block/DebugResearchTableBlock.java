package com.minemaster.libresearch.block;

import com.minemaster.libresearch.LibResearch;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Отладочный стол: дисциплины — красители, бонусные блоки — камень, обсидиан, бедрок.
 * Нужен, чтобы проверять мульти-столы и подсветку бонусных блоков без реального контента.
 */
public class DebugResearchTableBlock extends ResearchTableBlock {
    public static final MapCodec<DebugResearchTableBlock> CODEC =
            simpleCodec(DebugResearchTableBlock::new);

    public DebugResearchTableBlock(Properties properties) {
        this(properties, LibResearch.id("debug"));
    }

    public DebugResearchTableBlock(Properties properties, ResourceLocation tableType) {
        super(properties, tableType);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return CODEC;
    }
}
