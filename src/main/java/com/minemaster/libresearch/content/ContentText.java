package com.minemaster.libresearch.content;

import net.minecraft.network.chat.Component;

/**
 * Тексты из данных (датапак, KubeJS): {@code display} дисциплин, карт, бонусов,
 * типов столов.
 *
 * <p>Правило: такая строка — <b>ключ перевода, если он есть, иначе обычный текст</b>.
 * Достигается бесплатно: {@link Component#translatable} с несуществующим ключом
 * отображается на клиенте как сам ключ, то есть как исходная строка. Поэтому
 * датапак без {@code lang} работает как раньше, а датапак с {@code lang} — переводится.</p>
 */
public final class ContentText {
    private ContentText() {
    }

    /** Строка контента → компонент (ключ перевода либо литерал с фолбэком). */
    public static Component display(String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        return Component.translatable(raw);
    }
}
