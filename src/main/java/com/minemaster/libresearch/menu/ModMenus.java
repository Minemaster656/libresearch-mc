package com.minemaster.libresearch.menu;

import com.minemaster.libresearch.LibResearch;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Меню (контейнеры) мода. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, LibResearch.MOD_ID);

    /** Позиция стола передаётся в открывающем пакете. */
    public static final DeferredHolder<MenuType<?>, MenuType<ResearchTableMenu>> RESEARCH_TABLE =
            MENUS.register("research_table", () -> IMenuTypeExtension.create(
                    (id, inventory, buffer) -> new ResearchTableMenu(id, inventory, buffer.readBlockPos())));

    private ModMenus() {
    }
}
