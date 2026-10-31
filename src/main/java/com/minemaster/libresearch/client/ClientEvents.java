package com.minemaster.libresearch.client;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.menu.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
}
