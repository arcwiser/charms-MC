package com.germanware.smpcharms;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class CharmStorageHolder implements InventoryHolder {
    private final UUID owner;
    private final int size;
    private final String title;
    private final boolean isShared;
    private final int currentPage;
    private final int totalPages;
    private final int allowedPages; // For gateways: bitmask of allowed pages
    private Inventory inventory;

    public CharmStorageHolder(UUID owner, int size, String title) {
        this(owner, size, title, false, 0, 1, -1);
    }

    public CharmStorageHolder(UUID owner, int size, String title, boolean isShared) {
        this(owner, size, title, isShared, 0, 1, -1);
    }

    public CharmStorageHolder(UUID owner, int size, String title, boolean isShared, int currentPage, int totalPages, int allowedPages) {
        this.owner = owner;
        this.size = size;
        this.title = title;
        this.isShared = isShared;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.allowedPages = allowedPages;
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

    public int currentPage() {
        return currentPage;
    }

    public int totalPages() {
        return totalPages;
    }

    public int allowedPages() {
        return allowedPages;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
