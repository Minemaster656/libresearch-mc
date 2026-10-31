package com.minemaster.libresearch.menu;

import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/**
 * Контейнер стола: два слота (чернильница и бумага) плюс инвентарь игрока.
 * Сама теория в контейнер не входит — она живёт в BlockEntity и синхронизируется
 * через update-tag, поэтому экран читает её напрямую.
 */
public class ResearchTableMenu extends AbstractContainerMenu {
    public static final int TABLE_SLOTS = 2;
    public static final int PLAYER_SLOTS = 36;

    private static final int PLAYER_INV_X = 89;
    private static final int PLAYER_INV_Y = 180;
    private static final int HOTBAR_Y = 236;

    @Nullable
    private final ResearchTableBlockEntity table;
    private final BlockPos pos;

    /** Клиентский конструктор: BlockEntity берётся из уровня по позиции. */
    public ResearchTableMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory,
                inventory.player.level().getBlockEntity(pos) instanceof ResearchTableBlockEntity table ? table : null,
                pos);
    }

    public ResearchTableMenu(int id, Inventory inventory, ResearchTableBlockEntity table) {
        this(id, inventory, table, table.getBlockPos());
    }

    private ResearchTableMenu(int id, Inventory inventory, @Nullable ResearchTableBlockEntity table, BlockPos pos) {
        super(ModMenus.RESEARCH_TABLE.get(), id);
        this.table = table;
        this.pos = pos;

        IItemHandler handler = table != null ? table.getInventory() : new ItemStackHandler(TABLE_SLOTS);
        addSlot(new SlotItemHandler(handler, ResearchTableBlockEntity.SLOT_INKWELL, 10, 20));
        addSlot(new SlotItemHandler(handler, ResearchTableBlockEntity.SLOT_PAPER, 32, 20));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_INV_X + column * 18, HOTBAR_Y));
        }
    }

    @Nullable
    public ResearchTableBlockEntity table() {
        return table;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && player.level().getBlockEntity(pos) instanceof ResearchTableBlockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < TABLE_SLOTS) {
            if (!moveItemStackTo(stack, TABLE_SLOTS, TABLE_SLOTS + PLAYER_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Сначала пробуем положить в слоты стола, потом — в остальной инвентарь.
            if (!moveItemStackTo(stack, 0, TABLE_SLOTS, false)
                    && !moveItemStackTo(stack, TABLE_SLOTS, TABLE_SLOTS + PLAYER_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
