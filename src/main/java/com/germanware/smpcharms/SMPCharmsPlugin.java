package com.germanware.smpcharms;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMPCharmsPlugin extends JavaPlugin {
    private CharmService charmService;
    private BukkitTask upkeepTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.charmService = new CharmService(this);

        getServer().getPluginManager().registerEvents(new CharmListener(this, charmService), this);
        CharmCommand command = new CharmCommand(this, charmService);
        if (getCommand("charm") != null) {
            getCommand("charm").setExecutor(command);
            getCommand("charm").setTabCompleter(command);
        }

        upkeepTask = Bukkit.getScheduler().runTaskTimer(this, charmService::tickOnlinePlayers, 20L, 20L * 5L);

        Bukkit.getLogger().info("[SMPCharms] Enabled");
    }

    @Override
    public void onDisable() {
        if (upkeepTask != null) {
            upkeepTask.cancel();
        }
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            charmService.saveAllPlayerData(player);
        }
        Bukkit.getLogger().info("[SMPCharms] Saved all player data on disable");
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(this, value);
    }
}

