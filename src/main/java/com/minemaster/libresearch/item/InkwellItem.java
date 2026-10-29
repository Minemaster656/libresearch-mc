package com.minemaster.libresearch.item;

import com.minemaster.libresearch.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Чернильница: 100 зарядов по умолчанию. Ёмкость, цвет и расход на розыгрыш
 * задаются датапак-определением (см. {@link InkwellDefinitions}).
 */
public class InkwellItem extends Item {
    public static final int DEFAULT_CAPACITY = 100;

    public InkwellItem(Properties properties) {
        super(properties);
    }

    public static InkwellData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.INKWELL.get(), InkwellData.full(DEFAULT_CAPACITY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return data(stack).charges() < data(stack).capacity();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        InkwellData ink = data(stack);
        return Math.round(13.0F * ink.fraction());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return InkwellDefinitions.INSTANCE.colorOf(stack.getItem());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        InkwellData ink = data(stack);
        tooltip.add(Component.translatable("tooltip.libresearch.inkwell_charges", ink.charges(), ink.capacity())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
