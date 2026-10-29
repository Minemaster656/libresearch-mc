package com.minemaster.libresearch.data;

import com.minemaster.libresearch.item.InkwellDefinitions;
import com.minemaster.libresearch.content.table.TableTypes;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Подключает датапак-загрузчики мода к перезагрузке ресурсов и {@code /reload}. */
public final class DataEvents {
    private DataEvents() {
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(TableTypes.INSTANCE);
        event.addListener(InkwellDefinitions.INSTANCE);
    }
}
