package com.minemaster.libresearch.registry;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.item.InkwellData;
import com.minemaster.libresearch.item.InkwellItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LibResearch.MOD_ID);

    public static final DeferredItem<BlockItem> RESEARCH_TABLE =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESEARCH_TABLE);

    public static final DeferredItem<BlockItem> DEBUG_TABLE =
            ITEMS.registerSimpleBlockItem(ModBlocks.DEBUG_TABLE);

    /**
     * Чернильница. Ёмкость и цвет берутся из датапак-определения
     * {@code libresearch/inkwell/<id>.json}; компонент хранит текущий заряд,
     * чтобы значение переживало перезаход и синхронизировалось клиенту.
     */
    public static final DeferredItem<InkwellItem> INKWELL = ITEMS.register(
            "inkwell",
            () -> new InkwellItem(new Item.Properties()
                    .stacksTo(1)
                    .component(ModDataComponents.INKWELL.get(), InkwellData.full(InkwellItem.DEFAULT_CAPACITY))));

    private ModItems() {
    }
}
