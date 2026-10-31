package com.minemaster.libresearch.client;

import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import com.minemaster.libresearch.content.bonus.BonusBlockDefinition;
import com.minemaster.libresearch.content.bonus.TableBonuses;
import com.minemaster.libresearch.content.card.Card;
import com.minemaster.libresearch.content.card.Cards;
import com.minemaster.libresearch.content.table.TableType;
import com.minemaster.libresearch.content.table.TableTypes;
import com.minemaster.libresearch.core.KnowledgeMath;
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
 * предметов. Текстуры появятся в итерации 6, логика — в движке, экран только
 * показывает состояние и отправляет действия пакетами.</p>
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    private static final int W = 340;
    private static final int H = 256;

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

    private Set<ResourceLocation> detected = Set.of();
    private int scanTicker;
    private List<Component> tooltip = List.of();

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
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

        graphics.fill(0, 0, W, H, BG);
        graphics.renderOutline(0, 0, W, H, BORDER);
        graphics.fill(0, 0, W, 40, PANEL);
        graphics.fill(0, 40, W, 41, BORDER);

        drawSlotFrames(graphics);

        ResearchTableBlockEntity table = menu.table();
        TheoryState theory = theory();
        if (table == null || theory == null) {
            graphics.drawString(font, "Нет данных стола", LEFT, 46, RED, false);
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
            graphics.drawString(font, "+" + (bonuses.size() - limit), LEFT + columns * cell, 58, DIM, false);
        }

        graphics.drawString(font, "Бонусы: " + theory.selectedBonuses().size()
                + " (−" + selectedCost() + " вдохн.)", LEFT, 122, TEXT, false);

        boolean canStart = canStart(theory);
        button(graphics, LEFT, 140, LEFT_W, 18, "Начать теорию", canStart, mx, my);
        if (in(LEFT, 140, LEFT_W, 18, mx, my)) {
            tooltip = startBlocker();
        }

        int textX = RIGHT;
        graphics.drawString(font, "Настройка теории", textX, 46, GOLD, false);
        graphics.drawString(font, "Выберите бонусные блоки рядом", textX, 60, TEXT, false);
        graphics.drawString(font, "со столом: каждый стоит", textX, 70, TEXT, false);
        graphics.drawString(font, "вдохновение и добавляет карты.", textX, 80, TEXT, false);
        graphics.drawString(font, "Недостающие блоки показаны", textX, 96, DIM, false);
        graphics.drawString(font, "тускло — их нужно построить.", textX, 106, DIM, false);
        graphics.drawString(font, "Бумага и чернильница — в слотах.", textX, 126, DIM, false);
        graphics.drawString(font, "Вдохновение: " + baseInspiration() + " − бонусы.", textX, 146, DIM, false);
    }

    private boolean canStart(TheoryState theory) {
        ResearchTableBlockEntity table = menu.table();
        if (table == null || theory.hasPendingRewards()) {
            return false;
        }
        int paper = table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_PAPER).getCount();
        ItemStack ink = table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_INKWELL);
        boolean hasInk = ink.getItem() instanceof com.minemaster.libresearch.item.InkwellItem
                && com.minemaster.libresearch.item.InkwellItem.data(ink).charges() > 0;
        return paper > 0 && hasInk && baseInspiration() - selectedCost() >= 1
                && !tableType().disciplines().isEmpty();
    }

    private List<Component> startBlocker() {
        ResearchTableBlockEntity table = menu.table();
        TheoryState theory = theory();
        List<Component> lines = new ArrayList<>();
        if (table == null || theory == null) {
            return lines;
        }
        if (theory.hasPendingRewards()) {
            lines.add(Component.literal("Сначала заберите награды").withStyle(ChatFormatting.RED));
            return lines;
        }
        if (tableType().disciplines().isEmpty()) {
            lines.add(Component.literal("У стола нет дисциплин").withStyle(ChatFormatting.RED));
            return lines;
        }
        int paper = table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_PAPER).getCount();
        if (paper < 1) {
            lines.add(Component.literal("Нужен лист бумаги").withStyle(ChatFormatting.RED));
        }
        ItemStack ink = table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_INKWELL);
        boolean hasInk = ink.getItem() instanceof com.minemaster.libresearch.item.InkwellItem
                && com.minemaster.libresearch.item.InkwellItem.data(ink).charges() > 0;
        if (!hasInk) {
            lines.add(Component.literal("Нужна чернильница с чернилами").withStyle(ChatFormatting.RED));
        }
        if (baseInspiration() - selectedCost() < 1) {
            lines.add(Component.literal("Слишком много бонусов").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private List<Component> bonusTooltip(ResourceLocation id, @Nullable BonusBlockDefinition definition,
                                         boolean present, boolean selected) {
        List<Component> lines = new ArrayList<>();
        String name = definition == null || definition.display().isBlank()
                ? id.toString() : definition.display();
        lines.add(Component.literal(name).withStyle(ChatFormatting.BOLD));
        lines.add(Component.literal(present ? "Блок найден рядом" : "Блока рядом нет")
                .withStyle(present ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (definition != null) {
            lines.add(Component.literal("Стоимость: " + definition.cost() + " вдохновения")
                    .withStyle(ChatFormatting.GRAY));
            if (!definition.cards().isEmpty()) {
                StringBuilder cards = new StringBuilder("Карты: ");
                List<String> names = new ArrayList<>();
                definition.cards().forEach(entry -> names.add(entry.count() + "× " + entry.card().getPath()));
                cards.append(String.join(", ", names));
                lines.add(Component.literal(cards.toString()).withStyle(ChatFormatting.GRAY));
            }
        }
        lines.add(Component.literal(selected ? "Выбран — клик снимет" : "Клик — выбрать")
                .withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        return lines;
    }

    // ----------------------------------------------------------------- play

    private void renderPlay(GuiGraphics graphics, ResearchTableBlockEntity table,
                            TheoryState theory, int mx, int my) {
        drawLamps(graphics, LEFT, 46, theory.inspiration(), theory.startInspiration());

        int paper = table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_PAPER).getCount();
        boolean canDraw = theory.hand().isEmpty() && theory.inspiration() > 0 && paper > 0;
        String drawLabel = theory.hand().isEmpty()
                ? "Добор — 1 бумага (" + paper + ")" : "Сначала выберите карту";
        button(graphics, LEFT, 58, LEFT_W, 26, drawLabel, canDraw, mx, my);
        if (in(LEFT, 58, LEFT_W, 26, mx, my)) {
            tooltip = theory.hand().isEmpty()
                    ? List.of(Component.literal("Взять новую руку карт").withStyle(ChatFormatting.GRAY))
                    : List.of(Component.literal("Разыграйте карту из руки").withStyle(ChatFormatting.GRAY));
        }

        int charges = inkCharges();
        int capacity = Math.max(1, inkCapacity());
        graphics.fill(LEFT, 90, LEFT + LEFT_W, 97, SLOT_BG);
        int width = (int) Math.round(LEFT_W * (charges / (double) capacity));
        graphics.fill(LEFT, 90, LEFT + width, 97, BLUE);
        graphics.renderOutline(LEFT, 90, LEFT_W, 7, BORDER);
        graphics.drawString(font, "Чернила " + charges + "/" + capacity, LEFT, 101, TEXT, false);

        drawDisciplines(graphics, theory, LEFT, 116, mx, my);

        List<DrawnCard> hand = theory.hand();
        for (int index = 0; index < hand.size(); index++) {
            int x = RIGHT + index * 62;
            renderCard(graphics, theory, hand.get(index), index, x, 44, mx, my);
        }
        if (hand.isEmpty()) {
            graphics.drawString(font, "Рука пуста", RIGHT, 60, DIM, false);
            graphics.drawString(font, "Нажмите «Добор»", RIGHT, 72, DIM, false);
        }

        boolean canFinish = theory.inspiration() <= 0;
        button(graphics, RIGHT, 152, 86, 18, "Завершить", canFinish, mx, my);
        if (in(RIGHT, 152, 86, 18, mx, my)) {
            tooltip = List.of(Component.literal(canFinish
                            ? "Завершить теорию и получить очки"
                            : "Доступно при вдохновении 0")
                    .withStyle(canFinish ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        button(graphics, RIGHT + 92, 152, 94, 18, "Сжечь теорию", true, mx, my);
        if (in(RIGHT + 92, 152, 94, 18, mx, my)) {
            tooltip = List.of(Component.literal("Сбросить теорию и потерять прогресс")
                    .withStyle(ChatFormatting.RED));
        }

        if (!theory.blockedSet().isEmpty()) {
            graphics.drawString(font, "Заблокировано: " + String.join(", ", theory.blockedSet()),
                    LEFT, 168, RED, false);
        }
    }

    private int inkCharges() {
        ResearchTableBlockEntity table = menu.table();
        if (table == null) {
            return 0;
        }
        ItemStack stack = table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_INKWELL);
        return stack.getItem() instanceof com.minemaster.libresearch.item.InkwellItem
                ? com.minemaster.libresearch.item.InkwellItem.data(stack).charges() : 0;
    }

    private int inkCapacity() {
        ResearchTableBlockEntity table = menu.table();
        if (table == null) {
            return 100;
        }
        ItemStack stack = table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_INKWELL);
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
        int lineY = y + 5;
        for (var line : font.split(Component.literal(name), 52)) {
            graphics.drawString(font, line, x + 4, lineY, TEXT, false);
            lineY += 10;
            if (lineY > y + 60) {
                break;
            }
        }

        int iconX = x + 4;
        if (optional.isPresent()) {
            for (var requirement : optional.get().requires()) {
                ItemStack icon = CardText.icon(requirement);
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, iconX, y + 66);
                    iconX += 18;
                }
            }
        }

        String cost = "Цена " + drawn.cost();
        graphics.drawString(font, cost, x + 4, y + 90, theory.inspiration() >= cost(drawn) ? GREEN : RED, false);

        if (hovered) {
            optional.ifPresent(card -> tooltip = CardText.lines(card, drawn, tableType()));
        }
    }

    private static int cost(DrawnCard drawn) {
        return Math.max(0, drawn.cost());
    }

    private void drawDisciplines(GuiGraphics graphics, TheoryState theory, int x, int y, int mx, int my) {
        Knowledge knowledge = PlayerKnowledge.get(minecraft.player);
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
            String label = discipline.display() + " " + percent + "%";
            graphics.drawString(font, label, x, ry, TEXT, false);
            if (theory.isBlocked(discipline.id())) {
                graphics.drawString(font, "×", x + LEFT_W - 8, ry, RED, false);
            }
            if (in(x, ry - 1, LEFT_W, 10, mx, my)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(discipline.display()).withStyle(ChatFormatting.BOLD));
                lines.add(Component.literal("Прогресс теории: " + percent + "%")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Всего очков: " + centi / KnowledgeMath.CENTI_PER_POINT)
                        .withStyle(ChatFormatting.GRAY));
                if (theory.isBlocked(discipline.id())) {
                    lines.add(Component.literal("Заблокирована до конца теории")
                            .withStyle(ChatFormatting.RED));
                }
                tooltip = lines;
            }
            row++;
        }
    }

    // -------------------------------------------------------------- rewards

    private void renderRewards(GuiGraphics graphics, TheoryState theory, int mx, int my) {
        graphics.drawString(font, "Заберите награды", RIGHT, 46, GOLD, false);
        graphics.drawString(font, "Новая теория начнётся", RIGHT, 58, DIM, false);
        graphics.drawString(font, "после забора целых очков.", RIGHT, 68, DIM, false);

        Knowledge knowledge = PlayerKnowledge.get(minecraft.player);
        int row = 0;
        for (TableType.Discipline discipline : tableType().disciplines()) {
            long reward = theory.reward(discipline.id());
            if (reward <= 0) {
                continue;
            }
            int x = RIGHT;
            int y = 86 + row * 22;
            long claimable = KnowledgeMath.claimablePoints(reward, knowledge.centi(discipline.id()));
            ItemStack stack = payout(discipline, claimable);
            int background = claimable > 0 ? 0xFF4A3C33 : 0xFF2A211C;
            graphics.fill(x, y, x + 182, y + 20, background);
            graphics.renderOutline(x, y, 182, 20, claimable > 0 ? BORDER : 0xFF3A2F29);
            graphics.renderItem(stack, x + 2, y + 2);
            if (claimable > 0) {
                graphics.renderItemDecorations(font, stack, x + 2, y + 2);
            }
            graphics.drawString(font, discipline.display() + " — " + claimable + " очк.",
                    x + 22, y + 6, claimable > 0 ? TEXT : DIM, false);

            if (in(x, y, 182, 20, mx, my)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(discipline.display()).withStyle(ChatFormatting.BOLD));
                lines.add(Component.literal("Начислено: " + reward + " санти-очков")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Доступно: " + claimable + " очк. ("
                                + claimable * Math.max(1, discipline.countPerPoint()) + " шт.)")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal(claimable > 0 ? "Клик — забрать" : "Нужно целое очко")
                        .withStyle(claimable > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
                tooltip = lines;
            }
            row++;
        }

        button(graphics, RIGHT, 152, 120, 18, "Забрать всё", true, mx, my);
        if (in(RIGHT, 152, 120, 18, mx, my)) {
            tooltip = List.of(Component.literal("Забрать все доступные награды")
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

    private void drawSlotFrames(GuiGraphics graphics) {
        frame(graphics, 10, 22);
        frame(graphics, 32, 22);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                frame(graphics, 89 + column * 18, 180 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            frame(graphics, 89 + column * 18, 236);
        }
    }

    private void frame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    private void button(GuiGraphics graphics, int x, int y, int width, int height,
                        String label, boolean enabled, int mx, int my) {
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
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || minecraft == null || minecraft.player == null) {
            return false;
        }
        TheoryState theory = theory();
        if (theory == null) {
            return false;
        }
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        Phase phase = phase(theory);

        if (phase == Phase.SETUP) {
            List<ResourceLocation> bonuses = tableType().bonusBlocks();
            int limit = Math.min(bonuses.size(), 18);
            for (int index = 0; index < limit; index++) {
                int x = LEFT + (index % 6) * 20;
                int y = 58 + (index / 6) * 20;
                if (in(x, y, 18, 18, mx, my)) {
                    ResourceLocation id = bonuses.get(index);
                    if (detected.contains(id)) {
                        send(ModNetwork.Action.TOGGLE_BONUS, 0, id.toString());
                    }
                    return true;
                }
            }
            if (in(LEFT, 140, LEFT_W, 18, mx, my)) {
                if (canStart(theory)) {
                    send(ModNetwork.Action.START, 0, "");
                }
                return true;
            }
        } else if (phase == Phase.PLAY) {
            if (in(LEFT, 58, LEFT_W, 26, mx, my)) {
                if (theory.hand().isEmpty() && theory.inspiration() > 0) {
                    send(ModNetwork.Action.DRAW, 0, "");
                }
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
                if (theory.inspiration() <= 0) {
                    send(ModNetwork.Action.FINISH, 0, "");
                }
                return true;
            }
            if (in(RIGHT + 92, 152, 94, 18, mx, my)) {
                send(ModNetwork.Action.DISCARD, 0, "");
                return true;
            }
        } else {
            Knowledge knowledge = PlayerKnowledge.get(minecraft.player);
            for (TableType.Discipline discipline : tableType().disciplines()) {
                long reward = theory.reward(discipline.id());
                if (reward <= 0) {
                    continue;
                }
                int y = 86 + rewardRow(theory, discipline.id()) * 22;
                if (in(RIGHT, y, 182, 20, mx, my)) {
                    if (KnowledgeMath.claimablePoints(reward, knowledge.centi(discipline.id())) > 0) {
                        send(ModNetwork.Action.CLAIM, 0, discipline.id());
                    }
                    return true;
                }
            }
            if (in(RIGHT, 152, 120, 18, mx, my)) {
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
        PacketDistributor.sendToServer(new ModNetwork.TableActionPayload(menu.pos(), action, index, arg));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tooltip = List.of();
        super.render(graphics, mouseX, mouseY, partialTick);
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
