package com.germanware.smpcharms;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class CharmStorageHolder implements InventoryHolder {
    private final UUID owner;
    private final int size;
    private final String title;
    private final boolean isShared;
    private Inventory inventory;

    public CharmStorageHolder(UUID owner, int size, String title) {
        this(owner, size, title, false);
    }

    public CharmStorageHolder(UUID owner, int size, String title, boolean isShared) {
        this.owner = owner;
        this.size = size;
        this.title = title;
        this.isShared = isShared;
    }

    public UUID owner() {
        return owner;
    }

    public int size() {
        return size;
    }

    public String title() {
        return title;
    }

    public boolean isShared() {
        return isShared;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
