package com.c0ur4g3.guilds.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class GuildInventoryHolder implements InventoryHolder {

    private Inventory inventory;

    private final Map<Integer, Consumer<InventoryClickEvent>> clickHandlers = new HashMap<>();

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setClickHandler(int slot, Consumer<InventoryClickEvent> handler) {
        clickHandlers.put(slot, handler);
    }

    public void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = clickHandlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}