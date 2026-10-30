package com.minemaster.libresearch.block;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.registry.ModBlockEntities;
import com.minemaster.libresearch.registry.ModItems;
import com.minemaster.libresearch.theory.TheoryState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * Состояние стола: тип, два слота и текущая теория. Теория и награды живут
 * в блоке, поэтому переживают закрытие интерфейса и перезаход, но не слом блока.
 */
public class ResearchTableBlockEntity extends BlockEntity {
    public static final int SLOT_INKWELL = 0;
    public static final int SLOT_PAPER = 1;

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_INKWELL -> stack.is(ModItems.INKWELL.get());
                case SLOT_PAPER -> stack.is(net.minecraft.world.item.Items.PAPER);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private ResourceLocation tableType = LibResearch.id("research");
    private final TheoryState theory = new TheoryState();

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public TheoryState getTheory() {
        return theory;
    }

    /** Сохраняет изменения и рассылает их клиенту (для GUI итерации 3). */
    public void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public ResourceLocation getTableType() {
        return tableType;
    }

    public void setTableType(ResourceLocation tableType) {
        this.tableType = tableType;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putString("table_type", tableType.toString());
        CompoundTag theoryTag = new CompoundTag();
        theory.save(theoryTag);
        tag.put("theory", theoryTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        if (tag.contains("table_type")) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("table_type"));
            if (parsed != null) {
                tableType = parsed;
            }
        }
        theory.load(tag.getCompound("theory"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Не используется, но полезно иметь под рукой при отладке. */
    public int getComparatorOutput() {
        int filled = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                filled++;
            }
        }
        return filled;
    }
}
