package com.minemaster.libresearch.client;

import com.minemaster.libresearch.LibResearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Автотест интерфейса для разработки: запускается только с
 * {@code -Dlibresearch.uitest=true}.
 *
 * <p>Сценарий: поставить верстак и готовый стол, прокликать фазы
 * (бонус → старт → добор → карта → завершение → награда) и снять скриншоты
 * каждой фазы в {@code run/screenshots/}. Каждый клик идёт через настоящий
 * {@code mouseClicked}, поэтому проверяются и хитбоксы.</p>
 *
 * <p>Запуск: {@code ./gradlew runClient -Puitest -PclientArgs="--quickPlaySingleplayer test"}.</p>
 */
@EventBusSubscriber(modid = LibResearch.MOD_ID, value = Dist.CLIENT)
public final class UiTest {
    private static final boolean ENABLED = Boolean.getBoolean("libresearch.uitest");

    /** Совпадает с ResearchTableScreen: imageWidth/imageHeight. */
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 270;

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
        switch (stage) {
            case 0 -> {
                if (ticks > 60) {
                    command(minecraft, "setblock ~ ~ ~1 minecraft:crafting_table");
                    next();
                }
            }
            case 1 -> {
                if (ticks > 10) {
                    command(minecraft, "libresearch prepare");
                    next();
                }
            }
            case 2 -> {
                if (ticks > 20) {
                    logScreen(minecraft);
                    shot(minecraft, "uitest-1-setup");
                    click(minecraft, 10 + 9, 58 + 9); // первый бонус (верстак)
                    next();
                }
            }
            case 3 -> {
                if (ticks > 20) {
                    shot(minecraft, "uitest-2-setup-selected");
                    click(minecraft, 10 + 60, 140 + 9); // «Начать теорию»
                    next();
                }
            }
            case 4 -> {
                if (ticks > 20) {
                    shot(minecraft, "uitest-3-play");
                    click(minecraft, 10 + 60, 58 + 13); // «Добор»
                    next();
                }
            }
            case 5 -> {
                if (ticks > 20) {
                    shot(minecraft, "uitest-4-hand");
                    click(minecraft, 140 + 29, 44 + 50); // первая карта
                    next();
                }
            }
            case 6 -> {
                if (ticks > 20) {
                    shot(minecraft, "uitest-5-after-card");
                    command(minecraft, "libresearch theory set copper 40");
                    command(minecraft, "libresearch theory set iron 115");
                    command(minecraft, "libresearch theory inspiration 0");
                    command(minecraft, "libresearch theory finish");
                    next();
                }
            }
            case 7 -> {
                if (ticks > 20) {
                    shot(minecraft, "uitest-6-rewards");
                    // вторая строка = железо (115% = 1 целое очко); медь (40%) забрать нельзя
                    click(minecraft, 140 + 90, 68 + 10);
                    next();
                }
            }
            case 8 -> {
                if (ticks > 20) {
                    shot(minecraft, "uitest-7-after-claim");
                    next();
                }
            }
            case 9 -> {
                if (ticks > 20) {
                    LibResearch.LOGGER.info("UiTest: сценарий завершён");
                    System.exit(0);
                }
            }
            default -> {
            }
        }
    }

    private static void next() {
        stage++;
        ticks = 0;
    }

    private static void logScreen(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        LibResearch.LOGGER.info("UiTest: экран = {}",
                screen == null ? "нет" : screen.getClass().getName());
    }

    private static void command(Minecraft minecraft, String command) {
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    /** Клик в локальных координатах панели. */
    private static void click(Minecraft minecraft, int x, int y) {
        Screen screen = minecraft.screen;
        if (!(screen instanceof ResearchTableScreen)) {
            LibResearch.LOGGER.warn("UiTest: клик {}x{} пропущен — экран не открыт", x, y);
            return;
        }
        int left = (screen.width - PANEL_W) / 2;
        int top = (screen.height - PANEL_H) / 2;
        LibResearch.LOGGER.info("UiTest: клик {}x{}", x, y);
        screen.mouseClicked(left + x, top + y, 0);
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
                message -> LibResearch.LOGGER.info("UiTest: {}", message.getString()));
    }
}
