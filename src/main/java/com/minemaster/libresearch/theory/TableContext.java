package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import com.minemaster.libresearch.content.table.TableType;
import com.minemaster.libresearch.content.table.TableTypes;
import com.minemaster.libresearch.item.InkwellData;
import com.minemaster.libresearch.item.InkwellItem;
import com.minemaster.libresearch.item.PaperDefinition;
import com.minemaster.libresearch.item.Papers;
import com.minemaster.libresearch.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Всё, что нужно правилам карты: игрок, стол, его определение и текущая теория.
 * Заодно — доступ к слотам стола (чернильница и бумага) без дублирования кода.
 */
public record TableContext(ServerPlayer player, ResearchTableBlockEntity table,
                           TableType type, TheoryState theory) {

    public static Optional<TableContext> of(ServerPlayer player, ResearchTableBlockEntity table) {
        return TableTypes.INSTANCE.get(table.getTableType())
                .map(type -> new TableContext(player, table, type, table.getTheory()));
    }

    public RandomSource random() {
        return player.serverLevel().random;
    }

    public void markUpdated() {
        table.markUpdated();
    }

    public Optional<TableType.Discipline> discipline(String id) {
        return type.disciplines().stream().filter(discipline -> discipline.id().equals(id)).findFirst();
    }

    public ItemStack inkStack() {
        return table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_INKWELL);
    }

    public int inkCharges() {
        ItemStack stack = inkStack();
        return stack.getItem() instanceof InkwellItem ? InkwellItem.data(stack).charges() : 0;
    }

    /** @return {@code false}, если зарядов не хватило (тогда ничего не списано) */
    public boolean drainInk(int amount) {
        if (amount <= 0) {
            return true;
        }
        ItemStack stack = inkStack();
        if (!(stack.getItem() instanceof InkwellItem)) {
            return false;
        }
        InkwellData data = InkwellItem.data(stack);
        if (data.charges() < amount) {
            return false;
        }
        stack.set(ModDataComponents.INKWELL.get(), data.withCharges(data.charges() - amount));
        table.getInventory().setStackInSlot(ResearchTableBlockEntity.SLOT_INKWELL, stack);
        return true;
    }

    /** Определение бумаги этого стола (см. {@code libresearch/paper/<id>.json}). */
    public PaperDefinition paperDefinition() {
        return Papers.INSTANCE.forType(table.getTableType());
    }

    /** Сколько бумаги уходит на один добор руки. */
    public int paperPerDraw() {
        return Math.max(1, paperDefinition().perDraw());
    }

    public int paperCount() {
        return table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_PAPER).getCount();
    }

    public boolean consumePaper(int amount) {
        if (amount <= 0) {
            return true;
        }
        ItemStack stack = table.getInventory().getStackInSlot(ResearchTableBlockEntity.SLOT_PAPER);
        if (stack.getCount() < amount) {
            return false;
        }
        stack.shrink(amount);
        table.getInventory().setStackInSlot(ResearchTableBlockEntity.SLOT_PAPER, stack);
        return true;
    }
}
