package com.minemaster.libresearch;

import com.minemaster.libresearch.data.DataEvents;
import com.minemaster.libresearch.data.LibCommands;
import com.minemaster.libresearch.data.ModAttachments;
import com.minemaster.libresearch.registry.ModBlockEntities;
import com.minemaster.libresearch.registry.ModBlocks;
import com.minemaster.libresearch.registry.ModCreativeTabs;
import com.minemaster.libresearch.registry.ModDataComponents;
import com.minemaster.libresearch.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа мода.
 *
 * <p>Итерация 0: скелет — реестры, два отладочных стола, чернильница, загрузчики
 * датапак-определений. Игровая логика появится в итерациях 1–2.</p>
 */
@Mod(LibResearch.MOD_ID)
public class LibResearch {
    public static final String MOD_ID = "libresearch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public LibResearch(IEventBus modBus, ModContainer container) {
        // Порядок важен: типы компонентов регистрируются до предметов, которые их используют.
        ModDataComponents.COMPONENT_TYPES.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        modBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(DataEvents::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(LibCommands::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RESEARCH_TABLE.get(),
                (blockEntity, side) -> blockEntity.getInventory());
    }
}
