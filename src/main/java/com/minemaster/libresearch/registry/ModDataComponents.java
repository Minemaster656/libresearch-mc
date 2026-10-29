package com.minemaster.libresearch.registry;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.item.InkwellData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LibResearch.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InkwellData>> INKWELL =
            COMPONENT_TYPES.register("inkwell", () -> DataComponentType.<InkwellData>builder()
                    .persistent(InkwellData.CODEC)
                    .networkSynchronized(InkwellData.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }
}
