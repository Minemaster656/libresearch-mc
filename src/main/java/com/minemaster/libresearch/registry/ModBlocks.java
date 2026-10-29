package com.minemaster.libresearch.registry;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.block.DebugResearchTableBlock;
import com.minemaster.libresearch.block.ResearchTableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Блоки столов.
 *
 * <p>Каждый блок лишь задаёт «тип стола по умолчанию» (id датапак-определения).
 * Сами дисциплины, карты и бонусные блоки живут в JSON — поэтому типов столов
 * может быть сколько угодно, в том числе из KubeJS.</p>
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LibResearch.MOD_ID);

    public static final DeferredBlock<ResearchTableBlock> RESEARCH_TABLE = BLOCKS.register(
            "research_table",
            () -> new ResearchTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    /** Отладочный стол: свои дисциплины (красители) и свои бонусные блоки. */
    public static final DeferredBlock<DebugResearchTableBlock> DEBUG_TABLE = BLOCKS.register(
            "debug_table",
            () -> new DebugResearchTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5F)
                    .sound(SoundType.STONE)));

    private ModBlocks() {
    }
}
