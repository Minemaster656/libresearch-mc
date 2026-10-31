package com.minemaster.libresearch.client;

import com.minemaster.libresearch.LibResearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Автотест интерфейса для разработки: запускается только с
 * {@code -Dlibresearch.uitest=true}. Сам ставит стол, открывает меню
 * и снимает скриншоты трёх фаз в {@code run/screenshots/}.
 *
 * <p>Запуск: {@code ./gradlew runClient -Puitest -PclientArgs="--quickPlaySingleplayer test"}.</p>
 */
@EventBusSubscriber(modid = LibResearch.MOD_ID, value = Dist.CLIENT)
public final class UiTest {
    private static final boolean ENABLED = Boolean.getBoolean("libresearch.uitest");

    private static int stage;
    private static int ticks;

    private UiTest() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ticks++;
        if (stage == 0 && ticks > 60) {
            command(minecraft, "libresearch prepare");
            next();
        } else if (stage == 1 && ticks > 40) {
            shot(minecraft, "uitest-1-setup");
            command(minecraft, "libresearch theory start");
            command(minecraft, "libresearch theory set copper 40");
            command(minecraft, "libresearch theory set iron 115");
            command(minecraft, "libresearch theory draw");
            next();
        } else if (stage == 2 && ticks > 40) {
            shot(minecraft, "uitest-2-play");
            command(minecraft, "libresearch theory inspiration 0");
            command(minecraft, "libresearch theory finish");
            next();
        } else if (stage == 3 && ticks > 40) {
            shot(minecraft, "uitest-3-rewards");
            next();
        } else if (stage == 4 && ticks > 20) {
            System.exit(0);
        }
    }

    private static void next() {
        stage++;
        ticks = 0;
    }

    private static void command(Minecraft minecraft, String command) {
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
                message -> LibResearch.LOGGER.info("UiTest: {}", message.getString()));
    }
}
