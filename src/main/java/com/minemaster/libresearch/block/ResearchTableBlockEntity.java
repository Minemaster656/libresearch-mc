package com.minemaster.libresearch.block;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.registry.ModBlockEntities;
import com.minemaster.libresearch.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * Состояние стола. Пока хранит только тип стола и два слота; теория,
 * карты и награды появятся в итерации 2.
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

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
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
