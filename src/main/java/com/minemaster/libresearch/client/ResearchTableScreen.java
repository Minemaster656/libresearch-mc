package com.minemaster.libresearch.client;

import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import com.minemaster.libresearch.content.ContentText;
import com.minemaster.libresearch.content.bonus.BonusBlockDefinition;
import com.minemaster.libresearch.content.bonus.TableBonuses;
import com.minemaster.libresearch.content.card.Card;
import com.minemaster.libresearch.content.card.Cards;
import com.minemaster.libresearch.content.table.TableType;
import com.minemaster.libresearch.content.table.TableTypes;
import com.minemaster.libresearch.core.KnowledgeMath;
import com.minemaster.libresearch.item.Papers;
import com.minemaster.libresearch.menu.ResearchTableMenu;
import com.minemaster.libresearch.network.ModNetwork;
import com.minemaster.libresearch.theory.BonusScanner;
import com.minemaster.libresearch.theory.DrawnCard;
import com.minemaster.libresearch.theory.Knowledge;
import com.minemaster.libresearch.theory.PlayerKnowledge;
import com.minemaster.libresearch.theory.TheoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Экран стола исследований. Три фазы на одном экране:
 * настройка бонусов → игра → забор наград.
 *
 * <p>Рендер примитивный: прямоугольники {@link GuiGraphics} и ванильный рендер
 * предметов. Логика — в {@code TheoryEngine}, экран только показывает состояние и
 * отправляет действия пакетами.</p>
 *
 * <p>Все подписи — ключи перевода {@code libresearch.gui.*}; строки из данных
 * (имена карт и дисциплин) идут через {@link ContentText}. Полоса иконок дисциплин
 * рисуется <b>слева за пределами панели</b>, чтобы не пересекаться с JEI справа.</p>
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    private static final int W = 340;
    private static final int H = 270;

    private static final int BG = 0xF02B2320;
    private static final int PANEL = 0xFF3A2F29;
    private static final int BORDER = 0xFF6B5646;
    private static final int SLOT_BG = 0xFF171210;
    private static final int SLOT_BORDER = 0xFF5A4636;
    private static final int TEXT = 0xFFEDE3D8;
    private static final int DIM = 0xFF9A8978;
    private static final int GOLD = 0xFFE8B84B;
    private static final int RED = 0xFFD05A4A;
    private static final int GREEN = 0xFF7FBF6A;
    private static final int BLUE = 0xFF5B8FD6;

    private static final int LEFT = 10;
    private static final int LEFT_W = 120;
    private static final int RIGHT = 140;

    /** Полоса иконок дисциплин: слева от панели, вне зоны JEI. */
    private static final int STRIP_W = 20;
    private static final int STRIP_X = -26;
    private static final int STRIP_TOP = 46;
    private static final int STRIP_STEP = 22;
    private static final int STRIP_MAX = 9;

    private Set<ResourceLocation> detected = Set.of();
    private int scanTicker;
    private List<Component> tooltip = List.of();

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelX = 8;
        this.titleLabelY = 10000;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        rescan();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (++scanTicker % 20 == 0) {
            rescan();
        }
    }

    private void rescan() {
        ResearchTableBlockEntity table = menu.table();
        if (table == null || minecraft == null || minecraft.level == null) {
            detected = Set.of();
            return;
        }
        detected = BonusScanner.scan(minecraft.level, table.getBlockPos(), tableType());
    }

    private TableType tableType() {
        ResearchTableBlockEntity table = menu.table();
        if (table == null) {
            return TableType.EMPTY;
        }
        return TableTypes.INSTANCE.get(table.getTableType()).orElse(TableType.EMPTY);
    }

    @Nullable
    private TheoryState theory() {
        ResearchTableBlockEntity table = menu.table();
        return table == null ? null : table.getTheory();
    }

    private Phase phase(TheoryState theory) {
        if (theory.isActive()) {
            return Phase.PLAY;
        }
        if (theory.hasPendingRewards()) {
            return Phase.REWARDS;
        }
        return Phase.SETUP;
    }

    private int baseInspiration() {
        return KnowledgeMath.startInspiration(0, 0);
    }

    private int selectedCost() {
        TheoryState theory = theory();
        if (theory == null) {
            return 0;
        }
        int cost = 0;
        for (String id : theory.selectedBonuses()) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !detected.contains(key)) {
                continue;
            }
            cost += TableBonuses.INSTANCE.get(key).map(BonusBlockDefinition::cost).orElse(0);
        }
        return cost;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;

        // renderBg вызывается ДО сдвига на leftPos/topPos (в отличие от слотов),
        // поэтому весь наш рендер идёт в локальных координатах панели.
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0);

        graphics.fill(0, 0, W, H, BG);
        graphics.renderOutline(0, 0, W, H, BORDER);
        graphics.fill(0, 0, W, 40, PANEL);
        graphics.fill(0, 40, W, 41, BORDER);
        graphics.drawString(font, this.title, 8, 7, 0xFFFFE0A0, false);

        drawSlotFrames(graphics);

        ResearchTableBlockEntity table = menu.table();
        TheoryState theory = theory();
        if (table == null || theory == null) {
            graphics.drawString(font, Component.translatable("libresearch.gui.no_table_data"),
                    LEFT, 46, RED, false);
            graphics.pose().popPose();
            return;
        }

        Phase phase = phase(theory);
        if (phase == Phase.SETUP) {
            renderSetup(graphics, table, theory, mx, my);
        } else if (phase == Phase.PLAY) {
            renderPlay(graphics, table, theory, mx, my);
        } else {
            renderRewards(graphics, theory, mx, my);
        }

        drawDisciplineStrip(graphics, theory, mx, my);

        graphics.pose().popPose();
    }

    // ---------------------------------------------------------------- setup

    private void renderSetup(GuiGraphics graphics, ResearchTableBlockEntity table,
                             TheoryState theory, int mx, int my) {
        drawLamps(graphics, LEFT, 46, baseInspiration() - selectedCost(), baseInspiration());

        List<ResourceLocation> bonuses = tableType().bonusBlocks();
        int columns = 6;
        int cell = 20;
        int limit = Math.min(bonuses.size(), columns * 3);
        for (int index = 0; index < limit; index++) {
            ResourceLocation id = bonuses.get(index);
            int x = LEFT + (index % columns) * cell;
            int y = 58 + (index / columns) * cell;
            boolean present = detected.contains(id);
            boolean selected = theory.selectedBonuses().contains(id.toString());

            int background = selected ? 0xFF6B5A2E : present ? 0xFF4A3C33 : 0xFF2A211C;
            graphics.fill(x, y, x + 18, y + 18, background);
            graphics.renderOutline(x, y, 18, 18, selected ? GOLD : present ? BORDER : 0xFF3A2F29);

            BonusBlockDefinition definition = TableBonuses.INSTANCE.get(id).orElse(null);
            if (definition != null) {
                graphics.renderItem(BonusScanner.icon(definition), x + 1, y + 1);
            }
            if (!present) {
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xB0201814);
            }

            if (in(x, y, 18, 18, mx, my)) {
                tooltip = bonusTooltip(id, definition, present, selected);
            }
        }
        if (bonuses.size() > limit) {
            graphics.drawString(font, Component.literal("+" + (bonuses.size() - limit)),
                    LEFT + columns * cell, 58, DIM, false);
        }

        drawWrapped(graphics, Component.translatable("libresearch.gui.bonus_count",
                theory.selectedBonuses().size(), selectedCost()), LEFT, 122, LEFT_W, TEXT);

        boolean canStart = canStart(theory);
        button(graphics, LEFT, 146, LEFT_W, 18,
                Component.translatable("libresearch.gui.start"), canStart, mx, my);
        if (in(LEFT, 146, LEFT_W, 18, mx, my)) {
            tooltip = startBlocker();
        }

        // Подсказка, если ресурсы лежат в инвентаре, а не в слотах стола.
        if (minecraft != null && minecraft.player != null) {
            boolean paperInInv = minecraft.player.getInventory().contains(
                    new ItemStack(paperItem()));
            int hintY = 168;
            if (paperInSlots() < 1 && paperInInv) {
                drawWrapped(graphics, Component.translatable("libresearch.gui.hint.paper"),
                        LEFT, hintY, LEFT_W, GOLD);
            } else if (inkInSlots().isEmpty() && minecraft.player.getInventory().contains(
                    new ItemStack(com.minemaster.libresearch.registry.ModItems.INKWELL.get()))) {
                drawWrapped(graphics, Component.translatable("libresearch.gui.hint.inkwell"),
                        LEFT, hintY, LEFT_W, GOLD);
            }
        }

        int textX = RIGHT;
        int textW = W - RIGHT - 10;
        int y = 46;
        graphics.drawString(font, Component.translatable("libresearch.gui.setup.title"),
                textX, y, GOLD, false);
        y += 16;
        y = drawWrapped(graphics, Component.translatable("libresearch.gui.setup.help"), textX, y, textW, TEXT);
        y += 4;
        y = drawWrapped(graphics, Component.translatable("libresearch.gui.setup.missing"), textX, y, textW, DIM);
        y += 4;
        y = drawWrapped(graphics, Component.translatable("libresearch.gui.setup.slots"), textX, y, textW, DIM);
        y += 4;
        drawWrapped(graphics, Component.translatable("libresearch.gui.setup.inspiration", baseInspiration()),
                textX, y, textW, DIM);
    }

    /**
     * Слоты стола читаем из меню, а не из BlockEntity: клиентский BlockEntity может
     * не знать о содержимом (игрок положил предмет после открытия экрана), а слоты
     * меню синхронизируются сервером всегда.
     */
    private ItemStack tableSlot(int slot) {
        if (slot < 0 || slot >= menu.slots.size()) {
            return ItemStack.EMPTY;
        }
        return menu.getSlot(slot).getItem();
    }

    private int paperInSlots() {
        return tableSlot(ResearchTableBlockEntity.SLOT_PAPER).getCount();
    }

    private ItemStack inkInSlots() {
        return tableSlot(ResearchTableBlockEntity.SLOT_INKWELL);
    }

    /** Предмет бумаги, который ждёт этот тип стола (датапак может его заменить). */
    private net.minecraft.world.item.Item paperItem() {
        return Papers.item(Papers.INSTANCE.getOrDefault(tableType().paper()));
    }

    private boolean canStart(TheoryState theory) {
        if (theory.hasPendingRewards()) {
            return false;
        }
        ItemStack ink = inkInSlots();
        boolean hasInk = ink.getItem() instanceof com.minemaster.libresearch.item.InkwellItem
                && com.minemaster.libresearch.item.InkwellItem.data(ink).charges() > 0;
        return paperInSlots() > 0 && hasInk && baseInspiration() - selectedCost() >= 1
                && !tableType().disciplines().isEmpty();
    }

    private List<Component> startBlocker() {
        TheoryState theory = theory();
        List<Component> lines = new ArrayList<>();
        if (theory == null) {
            return lines;
        }
        if (theory.hasPendingRewards()) {
            lines.add(Component.translatable("libresearch.gui.blocker.rewards").withStyle(ChatFormatting.RED));
            return lines;
        }
        if (tableType().disciplines().isEmpty()) {
            lines.add(Component.translatable("libresearch.gui.blocker.no_disciplines").withStyle(ChatFormatting.RED));
            return lines;
        }
        if (paperInSlots() < 1) {
            lines.add(Component.translatable("libresearch.gui.blocker.paper").withStyle(ChatFormatting.RED));
        }
        ItemStack ink = inkInSlots();
        boolean hasInk = ink.getItem() instanceof com.minemaster.libresearch.item.InkwellItem
                && com.minemaster.libresearch.item.InkwellItem.data(ink).charges() > 0;
        if (!hasInk) {
            lines.add(Component.translatable("libresearch.gui.blocker.ink").withStyle(ChatFormatting.RED));
        }
        if (baseInspiration() - selectedCost() < 1) {
            lines.add(Component.translatable("libresearch.gui.blocker.too_many_bonuses")
                    .withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private List<Component> bonusTooltip(ResourceLocation id, @Nullable BonusBlockDefinition definition,
                                         boolean present, boolean selected) {
        List<Component> lines = new ArrayList<>();
        String name = definition == null || definition.display().isBlank()
                ? id.toString() : definition.display();
        lines.add(ContentText.display(name).copy().withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable(present
                        ? "libresearch.gui.bonus.found" : "libresearch.gui.bonus.missing")
                .withStyle(present ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (definition != null) {
            lines.add(Component.translatable("libresearch.gui.bonus.cost", definition.cost())
                    .withStyle(ChatFormatting.GRAY));
            if (!definition.cards().isEmpty()) {
                List<String> names = new ArrayList<>();
                definition.cards().forEach(entry -> names.add(entry.count() + "× " + entry.card().getPath()));
                lines.add(Component.translatable("libresearch.gui.bonus.cards", String.join(", ", names))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        lines.add(Component.translatable(selected
                        ? "libresearch.gui.bonus.selected" : "libresearch.gui.bonus.click")
                .withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        return lines;
    }

    // ----------------------------------------------------------------- play

    private void renderPlay(GuiGraphics graphics, ResearchTableBlockEntity table,
                            TheoryState theory, int mx, int my) {
        drawLamps(graphics, LEFT, 46, theory.inspiration(), theory.startInspiration());

        int paper = paperInSlots();
        boolean canDraw = theory.hand().isEmpty() && theory.inspiration() > 0 && paper > 0;
        Component drawLabel = theory.hand().isEmpty()
                ? Component.translatable("libresearch.gui.draw.with_paper", paper)
                : Component.translatable("libresearch.gui.draw.select_card");
        button(graphics, LEFT, 58, LEFT_W, 26, drawLabel, canDraw, mx, my);
        if (in(LEFT, 58, LEFT_W, 26, mx, my)) {
            tooltip = List.of(Component.translatable(theory.hand().isEmpty()
                            ? "libresearch.gui.draw.tip" : "libresearch.gui.draw.play_tip")
                    .withStyle(ChatFormatting.GRAY));
        }

        int charges = inkCharges();
        int capacity = Math.max(1, inkCapacity());
        graphics.fill(LEFT, 90, LEFT + LEFT_W, 97, SLOT_BG);
        int width = (int) Math.round(LEFT_W * (charges / (double) capacity));
        graphics.fill(LEFT, 90, LEFT + width, 97, BLUE);
        graphics.renderOutline(LEFT, 90, LEFT_W, 7, BORDER);
        graphics.drawString(font, Component.translatable("libresearch.gui.ink", charges, capacity),
                LEFT, 101, TEXT, false);

        drawDisciplines(graphics, theory, LEFT, 116, mx, my);

        List<DrawnCard> hand = theory.hand();
        for (int index = 0; index < hand.size(); index++) {
            int x = RIGHT + index * 62;
            renderCard(graphics, theory, hand.get(index), index, x, 44, mx, my);
        }
        if (hand.isEmpty()) {
            graphics.drawString(font, Component.translatable("libresearch.gui.hand.empty"),
                    RIGHT, 60, DIM, false);
            graphics.drawString(font, Component.translatable("libresearch.gui.hand.hint"),
                    RIGHT, 72, DIM, false);
        }

        boolean canFinish = theory.inspiration() <= 0;
        button(graphics, RIGHT, 152, 86, 18,
                Component.translatable("libresearch.gui.finish"), canFinish, mx, my);
        if (in(RIGHT, 152, 86, 18, mx, my)) {
            tooltip = List.of(Component.translatable(canFinish
                            ? "libresearch.gui.finish.tip" : "libresearch.gui.finish.tip_blocked")
                    .withStyle(canFinish ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        button(graphics, RIGHT + 92, 152, 94, 18,
                Component.translatable("libresearch.gui.discard"), true, mx, my);
        if (in(RIGHT + 92, 152, 94, 18, mx, my)) {
            tooltip = List.of(Component.translatable("libresearch.gui.discard.tip")
                    .withStyle(ChatFormatting.RED));
        }

        if (!theory.blockedSet().isEmpty()) {
            drawWrapped(graphics, Component.translatable("libresearch.gui.blocked",
                    String.join(", ", theory.blockedSet())), LEFT, 168, LEFT_W, RED);
        }
    }

    private int inkCharges() {
        ItemStack stack = inkInSlots();
        return stack.getItem() instanceof com.minemaster.libresearch.item.InkwellItem
                ? com.minemaster.libresearch.item.InkwellItem.data(stack).charges() : 0;
    }

    private int inkCapacity() {
        ItemStack stack = inkInSlots();
        return com.minemaster.libresearch.item.InkwellDefinitions.INSTANCE.capacityOf(stack.getItem());
    }

    private void renderCard(GuiGraphics graphics, TheoryState theory, DrawnCard drawn, int index,
                            int x, int y, int mx, int my) {
        Optional<Card> optional = Cards.INSTANCE.get(drawn.card());
        boolean bonus = optional.map(Card::bonusOnly).orElse(false);
        boolean hovered = in(x, y, 58, 104, mx, my);

        graphics.fill(x, y, x + 58, y + 104, hovered ? 0xFF4A3C33 : PANEL);
        graphics.renderOutline(x, y, 58, 104, bonus ? GOLD : BORDER);

        String name = optional.map(Card::display)
                .filter(text -> !text.isBlank())
                .orElse(drawn.card().getPath());
        drawFitted(graphics, ContentText.display(name), x + 4, y + 5, 50, TEXT);

        int iconX = x + 4;
        if (optional.isPresent()) {
            for (var requirement : optional.get().requires()) {
                ItemStack icon = CardText.icon(requirement, paperItem());
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, iconX, y + 66);
                    iconX += 18;
                }
            }
        }

        Component cost = Component.translatable("libresearch.gui.card.cost", drawn.cost());
        graphics.drawString(font, cost, x + 4, y + 90,
                theory.inspiration() >= drawn.cost() ? GREEN : RED, false);

        if (hovered) {
            optional.ifPresent(card -> tooltip = CardText.lines(card, drawn, tableType()));
        }
    }

    private void drawDisciplines(GuiGraphics graphics, TheoryState theory, int x, int y, int mx, int my) {
        Knowledge knowledge = knowledge();
        int row = 0;
        for (TableType.Discipline discipline : tableType().disciplines()) {
            int percent = theory.progress(discipline.id());
            long centi = knowledge.centi(discipline.id());
            if (percent <= 0 && centi <= 0 && theory.reward(discipline.id()) <= 0) {
                continue;
            }
            if (row >= 6) {
                break;
            }
            int ry = y + row * 10;
            graphics.drawString(font, Component.translatable("libresearch.gui.discipline.line",
                    ContentText.display(discipline.display()), percent), x, ry, TEXT, false);
            if (theory.isBlocked(discipline.id())) {
                graphics.drawString(font, Component.literal("×"), x + LEFT_W - 8, ry, RED, false);
            }
            if (in(x, ry - 1, LEFT_W, 10, mx, my)) {
                tooltip = disciplineTooltip(theory, discipline, knowledge, percent);
            }
            row++;
        }
    }

    private Knowledge knowledge() {
        return minecraft == null || minecraft.player == null
                ? Knowledge.EMPTY : PlayerKnowledge.get(minecraft.player);
    }

    private List<Component> disciplineTooltip(TheoryState theory, TableType.Discipline discipline,
                                              Knowledge knowledge, int percent) {
        List<Component> lines = new ArrayList<>();
        lines.add(ContentText.display(discipline.display()).copy().withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable("libresearch.gui.discipline.progress", percent)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("libresearch.gui.discipline.total",
                        knowledge.centi(discipline.id()) / KnowledgeMath.CENTI_PER_POINT)
                .withStyle(ChatFormatting.GRAY));
        if (theory.isBlocked(discipline.id())) {
            lines.add(Component.translatable("libresearch.gui.discipline.blocked")
                    .withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /**
     * Полоса иконок дисциплин слева от панели. Иконка берётся из {@code icon}
     * дисциплины (id предмета), видимость — по правилу «нет очков → не показываем».
     */
    private void drawDisciplineStrip(GuiGraphics graphics, TheoryState theory, int mx, int my) {
        int stripX = leftPos >= 34 ? STRIP_X : -STRIP_W - 4;
        if (leftPos < 34) {
            // Окно слишком узкое для полосы слева — не рисуем, чтобы не уехать за экран.
            return;
        }
        Knowledge knowledge = knowledge();
        List<TableType.Discipline> visible = new ArrayList<>();
        for (TableType.Discipline discipline : tableType().disciplines()) {
            int percent = theory.progress(discipline.id());
            if (percent > 0 || knowledge.centi(discipline.id()) > 0 || theory.reward(discipline.id()) > 0) {
                visible.add(discipline);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        int height = Math.min(visible.size(), STRIP_MAX) * STRIP_STEP;
        graphics.fill(stripX, STRIP_TOP - 2, stripX + STRIP_W, STRIP_TOP + height, PANEL);
        graphics.renderOutline(stripX, STRIP_TOP - 2, STRIP_W, height + 2, BORDER);

        int index = 0;
        for (TableType.Discipline discipline : visible) {
            if (index >= STRIP_MAX) {
                break;
            }
            int y = STRIP_TOP + index * STRIP_STEP;
            boolean hovered = in(stripX, y, STRIP_W, STRIP_STEP - 2, mx, my);
            if (hovered) {
                graphics.fill(stripX + 1, y, stripX + STRIP_W - 1, y + STRIP_STEP - 2, 0xFF4A3C33);
            }
            ItemStack icon = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(discipline.icon()));
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, stripX + 2, y + 2);
            }
            graphics.fill(stripX + 2, y + STRIP_STEP - 4, stripX + STRIP_W - 2, y + STRIP_STEP - 3,
                    discipline.color() | 0xFF000000);

            if (hovered) {
                tooltip = disciplineTooltip(theory, discipline, knowledge, theory.progress(discipline.id()));
            }
            index++;
        }
    }

    // -------------------------------------------------------------- rewards

    private void renderRewards(GuiGraphics graphics, TheoryState theory, int mx, int my) {
        int x = LEFT;
        int y = 46;
        graphics.drawString(font, Component.translatable("libresearch.gui.rewards.title"), x, y, GOLD, false);
        y += 16;
        y = drawWrapped(graphics, Component.translatable("libresearch.gui.rewards.note"),
                x, y, LEFT_W, DIM);

        Knowledge knowledge = knowledge();
        int row = 0;
        for (TableType.Discipline discipline : tableType().disciplines()) {
            long reward = theory.reward(discipline.id());
            if (reward <= 0) {
                continue;
            }
            int rowX = RIGHT;
            int rowY = 46 + row * 22;
            long claimable = KnowledgeMath.claimablePoints(reward, knowledge.centi(discipline.id()));
            ItemStack stack = payout(discipline, claimable);
            int background = claimable > 0 ? 0xFF4A3C33 : 0xFF2A211C;
            graphics.fill(rowX, rowY, rowX + 182, rowY + 20, background);
            graphics.renderOutline(rowX, rowY, 182, 20, claimable > 0 ? BORDER : 0xFF3A2F29);
            graphics.renderItem(stack, rowX + 2, rowY + 2);
            if (claimable > 0) {
                graphics.renderItemDecorations(font, stack, rowX + 2, rowY + 2);
            }
            Component rowLabel = claimable > 0
                    ? Component.translatable("libresearch.gui.rewards.row",
                            ContentText.display(discipline.display()), claimable)
                    : Component.translatable("libresearch.gui.rewards.row_partial",
                            ContentText.display(discipline.display()), reward);
            graphics.drawString(font, rowLabel, rowX + 22, rowY + 6,
                    claimable > 0 ? TEXT : DIM, false);

            if (in(rowX, rowY, 182, 20, mx, my)) {
                List<Component> lines = new ArrayList<>();
                lines.add(ContentText.display(discipline.display()).copy().withStyle(ChatFormatting.BOLD));
                lines.add(Component.translatable("libresearch.gui.rewards.tip.granted", reward)
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("libresearch.gui.rewards.tip.available", claimable,
                                claimable * Math.max(1, discipline.countPerPoint()))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(claimable > 0
                                ? "libresearch.gui.rewards.claim" : "libresearch.gui.rewards.need_point")
                        .withStyle(claimable > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
                tooltip = lines;
            }
            row++;
        }

        button(graphics, LEFT, 108, LEFT_W, 18,
                Component.translatable("libresearch.gui.rewards.claim_all"), true, mx, my);
        if (in(LEFT, 108, LEFT_W, 18, mx, my)) {
            tooltip = List.of(Component.translatable("libresearch.gui.rewards.claim_all_tip")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private ItemStack payout(TableType.Discipline discipline, long points) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(discipline.payout());
        int count = (int) Math.max(0, Math.min(Integer.MAX_VALUE,
                points * Math.max(1, discipline.countPerPoint())));
        return count <= 0 ? new ItemStack(item) : new ItemStack(item, count);
    }

    // ------------------------------------------------------------- helpers

    private void drawLamps(GuiGraphics graphics, int x, int y, int lit, int total) {
        for (int index = 0; index < total; index++) {
            int lx = x + index * 8;
            graphics.fill(lx, y, lx + 7, y + 7, index < lit ? GOLD : 0xFF2A211C);
            graphics.renderOutline(lx, y, 7, 7, index < lit ? 0xFFFFE7A0 : 0xFF4A3C33);
        }
    }

    /** Рисует текст с переносом по ширине; возвращает Y следующей строки. */
    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    /**
     * Однострочный текст, ужатый по ширине: длинные названия карт не переносятся
     * посреди слова («Calibratio n»), а рисуются мельче.
     */
    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        int textWidth = font.width(text);
        if (textWidth <= width) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = (float) width / textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawSlotFrames(GuiGraphics graphics) {
        frame(graphics, 10, 20);
        frame(graphics, 32, 20);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                frame(graphics, 89 + column * 18, 194 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            frame(graphics, 89 + column * 18, 250);
        }
    }

    private void frame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    private void button(GuiGraphics graphics, int x, int y, int width, int height,
                        Component label, boolean enabled, int mx, int my) {
        boolean hovered = in(x, y, width, height, mx, my);
        int background = !enabled ? 0xFF2E2620 : hovered ? 0xFF7A6552 : 0xFF5A4636;
        graphics.fill(x, y, x + width, y + height, background);
        graphics.renderOutline(x, y, width, height, enabled ? (hovered ? GOLD : BORDER) : 0xFF3A2F29);
        int color = enabled ? TEXT : 0xFF6A5A4B;
        graphics.drawString(font, label, x + (width - font.width(label)) / 2, y + (height - 8) / 2, color, false);
    }

    private static boolean in(int x, int y, int width, int height, int mx, int my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // AbstractContainerScreen.mouseClicked() возвращает true всегда, поэтому
        // вызывать его первым нельзя — иначе наши кнопки никогда не сработают.
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && minecraft != null && minecraft.player != null
                && !overSlot(mouseX, mouseY)) {
            TheoryState theory = theory();
            if (theory != null) {
                int mx = (int) mouseX - leftPos;
                int my = (int) mouseY - topPos;
                if (handlePanelClick(mx, my, theory, phase(theory))) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean overSlot(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            double x = leftPos + slot.x;
            double y = topPos + slot.y;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
                return true;
            }
        }
        return false;
    }

    /** Обработка кликов по нашим кнопкам/ячейкам. Возвращает true, если клик наш. */
    private boolean handlePanelClick(int mx, int my, TheoryState theory, Phase phase) {
        if (phase == Phase.SETUP) {
            List<ResourceLocation> bonuses = tableType().bonusBlocks();
            int limit = Math.min(bonuses.size(), 18);
            for (int index = 0; index < limit; index++) {
                int x = LEFT + (index % 6) * 20;
                int y = 58 + (index / 6) * 20;
                if (in(x, y, 18, 18, mx, my)) {
                    // Шлём всегда: если блока рядом нет, сервер ответит причиной.
                    send(ModNetwork.Action.TOGGLE_BONUS, 0, bonuses.get(index).toString());
                    return true;
                }
            }
            if (in(LEFT, 146, LEFT_W, 18, mx, my)) {
                // Клиентская проверка только для подсказки. Решение — за сервером:
                // он ответит причиной отказа в actionbar, если начать нельзя.
                if (!canStart(theory)) {
                    for (Component line : startBlocker()) {
                        minecraft.player.displayClientMessage(line, true);
                    }
                }
                send(ModNetwork.Action.START, 0, "");
                return true;
            }
        } else if (phase == Phase.PLAY) {
            if (in(LEFT, 58, LEFT_W, 26, mx, my)) {
                send(ModNetwork.Action.DRAW, 0, "");
                return true;
            }
            for (int index = 0; index < theory.hand().size(); index++) {
                int x = RIGHT + index * 62;
                if (in(x, 44, 58, 104, mx, my)) {
                    send(ModNetwork.Action.PLAY, index, "");
                    return true;
                }
            }
            if (in(RIGHT, 152, 86, 18, mx, my)) {
                send(ModNetwork.Action.FINISH, 0, "");
                return true;
            }
            if (in(RIGHT + 92, 152, 94, 18, mx, my)) {
                send(ModNetwork.Action.DISCARD, 0, "");
                return true;
            }
        } else {
            Knowledge knowledge = knowledge();
            for (TableType.Discipline discipline : tableType().disciplines()) {
                long reward = theory.reward(discipline.id());
                if (reward <= 0) {
                    continue;
                }
                int y = 46 + rewardRow(theory, discipline.id()) * 22;
                if (in(RIGHT, y, 182, 20, mx, my)) {
                    // Строка с нулём целых очков некликабельна (B15): забирать нечего.
                    long claimable = KnowledgeMath.claimablePoints(reward, knowledge.centi(discipline.id()));
                    if (claimable <= 0) {
                        return true;
                    }
                    // Сколько очков доступно, решает сервер (у клиента может быть
                    // устаревшая копия знаний). Клиент только отправляет запрос.
                    send(ModNetwork.Action.CLAIM, 0, discipline.id());
                    return true;
                }
            }
            if (in(LEFT, 108, LEFT_W, 18, mx, my)) {
                send(ModNetwork.Action.CLAIM_ALL, 0, "");
                return true;
            }
        }
        return false;
    }

    private int rewardRow(TheoryState theory, String disciplineId) {
        int row = 0;
        for (TableType.Discipline discipline : tableType().disciplines()) {
            if (theory.reward(discipline.id()) <= 0) {
                continue;
            }
            if (discipline.id().equals(disciplineId)) {
                return row;
            }
            row++;
        }
        return row;
    }

    private void send(ModNetwork.Action action, int index, String arg) {
        com.minemaster.libresearch.LibResearch.LOGGER.info("UI send {} {} (стол {})", action, arg, menu.pos());
        PacketDistributor.sendToServer(new ModNetwork.TableActionPayload(menu.pos(), action, index, arg));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tooltip = List.of();
        super.render(graphics, mouseX, mouseY, partialTick);
        // В 1.21.1 AbstractContainerScreen больше не рисует тултип слота сам —
        // это обязанность конкретного экрана.
        this.renderTooltip(graphics, mouseX, mouseY);
        if (!tooltip.isEmpty() && hoveredSlot == null) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private enum Phase {
        SETUP,
        PLAY,
        REWARDS
    }
}
