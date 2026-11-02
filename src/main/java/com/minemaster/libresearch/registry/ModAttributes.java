package com.minemaster.libresearch.registry;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.core.KnowledgeMath;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Атрибуты мода.
 *
 * <p>Стартовое вдохновение — обычный атрибут игрока: базовое значение 5, диапазон
 * 0…1024, синхронизируется на клиент (нужно для лампочек в GUI) и сохраняется в NBT
 * игрока вместе с остальными атрибутами. Влиять можно предметами, эффектами и
 * командой {@code /attribute <target> libresearch:starting_inspiration ...}.</p>
 */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, LibResearch.MOD_ID);

    public static final DeferredHolder<Attribute, Attribute> STARTING_INSPIRATION = ATTRIBUTES.register(
            "starting_inspiration",
            () -> new RangedAttribute("attribute.libresearch.starting_inspiration",
                    KnowledgeMath.BASE_INSPIRATION, 0.0D, KnowledgeMath.MAX_INSPIRATION).setSyncable(true));

    private ModAttributes() {
    }

    /**
     * Добавляет атрибут игроку. В 1.21.1 модовые атрибуты не попадают на сущности
     * автоматически — без этого {@code player.getAttribute(...)} вернёт {@code null}.
     */
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, STARTING_INSPIRATION);
    }

    /** Итоговое значение атрибута с модификаторами; при отсутствии — база из ядра. */
    public static double startingInspiration(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(STARTING_INSPIRATION);
        return instance == null ? KnowledgeMath.BASE_INSPIRATION : instance.getValue();
    }
}
