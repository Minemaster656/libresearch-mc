package com.minemaster.libresearch.registry;

import com.minemaster.libresearch.LibResearch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LibResearch.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.libresearch"))
            .icon(() -> new ItemStack(ModItems.INKWELL.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.INKWELL.get());
                output.accept(ModItems.RESEARCH_TABLE.get());
                output.accept(ModItems.DEBUG_TABLE.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
