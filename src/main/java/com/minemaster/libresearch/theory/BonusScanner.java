package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.content.bonus.BonusBlockDefinition;
import com.minemaster.libresearch.content.bonus.TableBonuses;
import com.minemaster.libresearch.content.table.TableType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Поиск бонусных блоков и сущностей вокруг стола. Используется и сервером
 * (при старте теории), и клиентом (для подсветки списка в GUI).
 *
 * <p>Область — 9×9×3 блока (по −4…+4 по горизонтали, −1…+1 по вертикали)
 * и куб ±5 вокруг стола для сущностей. Один тип бонуса учитывается один раз.
 * Проверки видимости нет — как в оригинале.</p>
 */
public final class BonusScanner {
    public static final int HORIZONTAL = 4;
    public static final int VERTICAL = 1;
    public static final int ENTITY_RADIUS = 5;

    private BonusScanner() {
    }

    /** Найденные рядом бонусы из списка типа стола (в порядке определения). */
    public static Set<ResourceLocation> scan(Level level, BlockPos center, TableType type) {
        Set<ResourceLocation> found = new LinkedHashSet<>();
        if (type.bonusBlocks().isEmpty()) {
            return found;
        }

        Map<ResourceLocation, BonusBlockDefinition> definitions = new HashMap<>();
        for (ResourceLocation id : type.bonusBlocks()) {
            TableBonuses.INSTANCE.get(id).ifPresent(definition -> definitions.put(id, definition));
        }
        if (definitions.isEmpty()) {
            return found;
        }

        BlockPos min = center.offset(-HORIZONTAL, -VERTICAL, -HORIZONTAL);
        BlockPos max = center.offset(HORIZONTAL, VERTICAL, HORIZONTAL);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            for (Map.Entry<ResourceLocation, BonusBlockDefinition> entry : definitions.entrySet()) {
                if (!found.contains(entry.getKey()) && matchesBlock(entry.getValue(), state)) {
                    found.add(entry.getKey());
                }
            }
        }

        AABB box = new AABB(center).inflate(ENTITY_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, box, entity -> true)) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            for (Map.Entry<ResourceLocation, BonusBlockDefinition> entry : definitions.entrySet()) {
                if (entry.getValue().match().entities().contains(entityId)) {
                    found.add(entry.getKey());
                }
            }
        }
        return found;
    }

    public static boolean matchesBlock(BonusBlockDefinition definition, BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (definition.match().blocks().contains(blockId)) {
            return true;
        }
        for (ResourceLocation tag : definition.match().tags()) {
            if (state.is(TagKey.create(Registries.BLOCK, tag))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Иконка для списка бонусов: предмет первого блока, первого блока из тега
     * или бумага, если бонус задан только сущностью.
     */
    public static ItemStack icon(BonusBlockDefinition definition) {
        for (ResourceLocation id : definition.match().blocks()) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != Blocks.AIR) {
                return new ItemStack(block.asItem());
            }
        }
        for (ResourceLocation id : definition.match().tags()) {
            var tag = BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id));
            if (tag.isPresent() && tag.get().size() > 0) {
                Block block = tag.get().get(0).value();
                return new ItemStack(block.asItem());
            }
        }
        return new ItemStack(Items.PAPER);
    }
}
