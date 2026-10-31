package com.minemaster.libresearch.block;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.menu.ResearchTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;

import javax.annotation.Nullable;

/**
 * Стол исследований. Один и тот же класс обслуживает все «типы столов»:
 * тип — это id датапак-определения, которое задаёт дисциплины, карты и бонусные блоки.
 */
public class ResearchTableBlock extends BaseEntityBlock {
    public static final MapCodec<ResearchTableBlock> CODEC = simpleCodec(ResearchTableBlock::new);

    private final ResourceLocation defaultTableType;

    public ResearchTableBlock(Properties properties) {
        this(properties, LibResearch.id("research"));
    }

    protected ResearchTableBlock(Properties properties, ResourceLocation defaultTableType) {
        super(properties);
        this.defaultTableType = defaultTableType;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public ResourceLocation getDefaultTableType() {
        return defaultTableType;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchTableBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ResearchTableBlockEntity table) {
            table.setTableType(defaultTableType);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ResearchTableBlockEntity table) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inventory, p) -> new ResearchTableMenu(id, inventory, table),
                    Component.translatable("container.libresearch.research_table"));
            serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }
}
