package com.minemaster.libresearch.client;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.item.InkwellDefinitions;
import com.minemaster.libresearch.menu.ModMenus;
import com.minemaster.libresearch.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Клиентские регистрации. */
@EventBusSubscriber(modid = LibResearch.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RESEARCH_TABLE.get(), ResearchTableScreen::new);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> InkwellDefinitions.INSTANCE.colorOf(stack.getItem()),
                ModItems.INKWELL.get());
    }
}
