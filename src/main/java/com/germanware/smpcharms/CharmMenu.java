package com.germanware.smpcharms;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class CharmMenu {
    private final CharmService service;

    public CharmMenu(CharmService service) {
        this.service = service;
    }

    public String title() {
        return service.getCharmMenuTitle();
    }

    public Inventory create(CharmItem active, int rerolls) {
        Inventory inv = Bukkit.createInventory(null, 27, title());

        inv.setItem(10, button(Material.NETHER_STAR, ChatColor.AQUA + "Active Charm", List.of(
                ChatColor.GRAY + (active == null ? "None" : active.type().displayName() + " Lv." + active.level()),
                ChatColor.GRAY + "Right-click your charm item to activate it"
        )));

        inv.setItem(12, button(Material.PAPER, ChatColor.GOLD + "Member Badge", List.of(
                ChatColor.GRAY + "Free rerolls: " + rerolls,
                ChatColor.GRAY + "Right-click the badge to reroll"
        )));

        inv.setItem(14, button(Material.BOOK, ChatColor.YELLOW + "Charm List", List.of(
                ChatColor.GRAY + "Strength, Health, Feather",
                ChatColor.GRAY + "Wealth, Phantom, Luck, Warden"
        )));

        inv.setItem(16, button(Material.COMPASS, ChatColor.GREEN + "Balance Guide", List.of(
                ChatColor.GRAY + "Each charm has one clear role",
                ChatColor.GRAY + "No stacking, no duplicate dominance"
        )));

        inv.setItem(18, button(Material.NAME_TAG, ChatColor.LIGHT_PURPLE + "Reload Config", List.of(
                ChatColor.GRAY + "Admin only",
                ChatColor.GRAY + "Reloads charm balance values"
        )));

        inv.setItem(22, button(Material.BARRIER, ChatColor.RED + "Close", List.of(ChatColor.GRAY + "Click to close")));

        return inv;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}

