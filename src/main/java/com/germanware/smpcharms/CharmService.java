package com.germanware.smpcharms;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class CharmService {
    private static final int STARTER_REROLLS = 1;
    private static final int STORAGE_LEVEL1_SLOTS = 27;
    private static final int STORAGE_LEVEL2_SLOTS = 45;

    private final SMPCharmsPlugin plugin;
    private final Random random = new Random();
    private final NamespacedKey typeKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey activeKey;
    private final NamespacedKey activeSlotKey;
    private final NamespacedKey rerollsKey;
    private final NamespacedKey phantomVisibleKey;
    private final NamespacedKey airBoostKey;
    private final NamespacedKey storageOpenKey;
    private final NamespacedKey storageDataKey;
    private final NamespacedKey storageSizeKey;
    private final NamespacedKey charmTokenKey;
    private final NamespacedKey memberTokenKey;
    private final NamespacedKey upgradeCatalystKey;
    private final NamespacedKey swapCatalystKey;
    private final NamespacedKey gatewayKey;
    private final NamespacedKey gatewayOwnerKey;
    private final NamespacedKey storagePagesKey;
    private final NamespacedKey storageCurrentPageKey;
    private final NamespacedKey gatewayAllowedPagesKey;
    private final NamespacedKey storageIdKey;

    public CharmService(SMPCharmsPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = plugin.key("charm_type");
        this.levelKey = plugin.key("charm_level");
        this.activeKey = plugin.key("active_charm");
        this.activeSlotKey = plugin.key("active_slot");
        this.rerollsKey = plugin.key("rerolls");
        this.phantomVisibleKey = plugin.key("phantom_visible");
        this.airBoostKey = plugin.key("air_boost");
        this.storageOpenKey = plugin.key("storage_open");
        this.storageDataKey = plugin.key("storage_data");
        this.storageSizeKey = plugin.key("storage_size");
        this.charmTokenKey = plugin.key("charm_token");
        this.memberTokenKey = plugin.key("member_token");
        this.upgradeCatalystKey = plugin.key("upgrade_catalyst");
        this.swapCatalystKey = plugin.key("swap_catalyst");
        this.gatewayKey = plugin.key("gateway_token");
        this.gatewayOwnerKey = plugin.key("gateway_owner");
        this.storagePagesKey = plugin.key("storage_pages");
        this.storageCurrentPageKey = plugin.key("storage_current_page");
        this.gatewayAllowedPagesKey = plugin.key("gateway_allowed_pages");
        this.storageIdKey = plugin.key("storage_id");
    }

    public void tickOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            CharmItem active = getActiveCharm(player);
            if (active != null) {
                applyEffects(player, active);
            }
        }
    }

    public CharmItem randomStarterCharm() {
        CharmType[] pool = CharmType.values();
        return new CharmItem(pool[random.nextInt(pool.length)], 1);
    }

    public ItemStack createCharm(CharmType type, int level) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + type.displayName() + ChatColor.GRAY + " [Lv." + level + "]");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + type.role());
        lore.add(ChatColor.GRAY + "Power: " + getPowerLine(type, level));
        lore.add(ChatColor.GRAY + "Debuff: " + getDebuffLine(type, level));
        lore.add(ChatColor.DARK_GRAY + "Right-click to activate");
        meta.setLore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pdc.set(charmTokenKey, PersistentDataType.BYTE, (byte) 1);
        
        // Assign unique storage ID for storage charms
        if (type == CharmType.STORAGE) {
            String storageId = UUID.randomUUID().toString();
            pdc.set(storageIdKey, PersistentDataType.STRING, storageId);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMemberBadge(int rerolls) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Member Badge");
        meta.setLore(List.of(
                ChatColor.GRAY + "Starter identity token",
                ChatColor.GREEN + "Free rerolls: " + rerolls,
                ChatColor.YELLOW + "Right-click to reroll your starter charm"
        ));
        meta.getPersistentDataContainer().set(memberTokenKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(rerollsKey, PersistentDataType.INTEGER, rerolls);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMemberBadge(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(memberTokenKey, PersistentDataType.BYTE);
    }

    public boolean isCharm(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(charmTokenKey, PersistentDataType.BYTE);
    }

    public CharmItem readCharm(ItemStack item) {
        if (!isCharm(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String typeName = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        Integer level = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        if (typeName == null || level == null) {
            return null;
        }
        return new CharmItem(CharmType.valueOf(typeName), level);
    }

    public ItemStack upgradeCharm(ItemStack item) {
        CharmItem charm = readCharm(item);
        if (charm == null || charm.level() >= 2) {
            return item;
        }
        return createCharm(charm.type(), 2);
    }

    public ItemStack swapCharm(CharmType newType) {
        return createCharm(newType, 1);
    }

    public void fullySwapCharm(Player player, CharmType newType) {
        CharmItem previous = getActiveCharm(player);
        if (previous != null) {
            removeOneCharmFromInventory(player, previous);
        }
        setActiveCharm(player, new CharmItem(newType, 1));
        player.getInventory().setItemInMainHand(createCharm(newType, 1));
    }

    public boolean hasActiveCharm(Player player) {
        return player.getPersistentDataContainer().has(activeKey, PersistentDataType.STRING);
    }

    public CharmItem getActiveCharm(Player player) {
        String raw = player.getPersistentDataContainer().get(activeKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length != 2) {
            return null;
        }
        return new CharmItem(CharmType.valueOf(parts[0]), Integer.parseInt(parts[1]));
    }

    public void setActiveCharm(Player player, CharmItem charm) {
        player.getPersistentDataContainer().set(activeKey, PersistentDataType.STRING, charm.type().name() + ":" + charm.level());
        player.getPersistentDataContainer().set(activeSlotKey, PersistentDataType.INTEGER, player.getInventory().getHeldItemSlot());
        applyEffects(player, charm);
    }

    public Integer getActiveSlot(Player player) {
        return player.getPersistentDataContainer().get(activeSlotKey, PersistentDataType.INTEGER);
    }

    public int getRerolls(Player player) {
        Integer rerolls = player.getPersistentDataContainer().get(rerollsKey, PersistentDataType.INTEGER);
        return rerolls == null ? 0 : rerolls;
    }

    public void setRerolls(Player player, int amount) {
        player.getPersistentDataContainer().set(rerollsKey, PersistentDataType.INTEGER, Math.max(0, amount));
    }

    public boolean consumeReroll(Player player) {
        int rerolls = getRerolls(player);
        if (rerolls <= 0) {
            return false;
        }
        setRerolls(player, rerolls - 1);
        return true;
    }

    public void giveStarterPackage(Player player) {
        CharmItem starter = randomStarterCharm();
        setActiveCharm(player, starter);
        player.getInventory().addItem(createCharm(starter.type(), starter.level()));
        player.getInventory().addItem(createMemberBadge(STARTER_REROLLS));
    }

    public ItemStack createUpgradeCatalyst() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Upgrade Catalyst");
        List<String> lore = new ArrayList<>();
        if (isCraftingRequired()) {
            lore.add(ChatColor.GRAY + "Admin-spawned helper item");
            lore.add(ChatColor.YELLOW + "Use your normal crafting recipe to upgrade a charm");
        } else {
            lore.add(ChatColor.GRAY + "Right-click while holding a charm to upgrade it");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(upgradeCatalystKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSwapCatalyst() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Swap Catalyst");
        List<String> lore = new ArrayList<>();
        if (isCraftingRequired()) {
            lore.add(ChatColor.GRAY + "Admin-spawned helper item");
            lore.add(ChatColor.YELLOW + "Use your normal crafting recipe to swap a charm");
        } else {
            lore.add(ChatColor.GRAY + "Right-click while holding a charm to swap it");
            lore.add(ChatColor.GRAY + "Right-click a player to swap their charm");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(swapCatalystKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGateway(UUID owner) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Storage Gateway");
        
        // Get the storage ID from the owner's active storage charm
        String storageId = null;
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            storageId = getStorageId(ownerPlayer);
        }
        
        if (storageId == null) {
            storageId = getStorageIdOffline(owner);
        }
        
        if (storageId == null) {
            storageId = owner.toString(); // Fallback to owner UUID
        }
        
        meta.setLore(List.of(
            ChatColor.GRAY + "Right-click to access shared storage",
            ChatColor.GRAY + "Owner: " + Bukkit.getOfflinePlayer(owner).getName()
        ));
        meta.getPersistentDataContainer().set(gatewayKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(gatewayOwnerKey, PersistentDataType.STRING, owner.toString());
        meta.getPersistentDataContainer().set(storageIdKey, PersistentDataType.STRING, storageId);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGateway(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(gatewayKey, PersistentDataType.BYTE);
    }

    public UUID getGatewayOwner(ItemStack item) {
        if (!isGateway(item)) {
            return null;
        }
        String ownerString = item.getItemMeta().getPersistentDataContainer().get(gatewayOwnerKey, PersistentDataType.STRING);
        if (ownerString == null) {
            return null;
        }
        try {
            return UUID.fromString(ownerString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int getGatewayAllowedPages(ItemStack item) {
        if (!isGateway(item)) {
            return -1;
        }
        String allowedPagesStr = item.getItemMeta().getPersistentDataContainer().get(gatewayAllowedPagesKey, PersistentDataType.STRING);
        if (allowedPagesStr == null) {
            return -1; // Default to all pages
        }
        try {
            return Integer.parseInt(allowedPagesStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public void setGatewayAllowedPages(ItemStack item, int allowedPages) {
        if (!isGateway(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(gatewayAllowedPagesKey, PersistentDataType.STRING, String.valueOf(allowedPages));
        item.setItemMeta(meta);
    }

    public boolean isUpgradeCatalyst(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(upgradeCatalystKey, PersistentDataType.BYTE);
    }

    public boolean isSwapCatalyst(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(swapCatalystKey, PersistentDataType.BYTE);
    }
    public void rerollPlayerCharm(Player player) {
        CharmItem previous = getActiveCharm(player);
        CharmItem next = randomStarterCharm();
        if (previous != null) {
            removeOneCharmFromInventory(player, previous);
        }
        setActiveCharm(player, next);
        player.getInventory().setItemInMainHand(createCharm(next.type(), next.level()));
    }

    public int getStorageSlotCount(Player player) {
        CharmItem active = getActiveCharm(player);
        if (active == null || active.type() != CharmType.STORAGE) {
            return 0;
        }
        return active.level() >= 2 ? STORAGE_LEVEL2_SLOTS : STORAGE_LEVEL1_SLOTS;
    }
    public boolean rerollStarter(Player player) {
        if (!consumeReroll(player)) {
            return false;
        }
        CharmItem previous = getActiveCharm(player);
        CharmItem next = randomStarterCharm();
        if (previous != null) {
            removeOneCharmFromInventory(player, previous);
        }
        setActiveCharm(player, next);
        player.getInventory().setItemInMainHand(createCharm(next.type(), next.level()));
        player.getInventory().addItem(createMemberBadge(getRerolls(player)));
        return true;
    }

    public void clearActiveCharm(Player player) {
        player.getPersistentDataContainer().remove(activeKey);
        player.getPersistentDataContainer().remove(activeSlotKey);
        player.getPersistentDataContainer().remove(phantomVisibleKey);
        player.getPersistentDataContainer().remove(airBoostKey);
        player.getPersistentDataContainer().remove(storageOpenKey);
        player.getPersistentDataContainer().remove(storageDataKey);
        player.getPersistentDataContainer().remove(storageSizeKey);
        resetAttributes(player);
        clearNonPermanentEffects(player);
    }

    public void applyEffects(Player player, CharmItem charm) {
        resetAttributes(player);
        clearNonPermanentEffects(player);

        switch (charm.type()) {
            case STRENGTH -> applyStrength(player, charm.level());
            case HEALTH -> applyHealth(player, charm.level());
            case FEATHER -> applyFeather(player, charm.level());
            case WEALTH -> applyWealth(player, charm.level());
            case PHANTOM -> applyPhantom(player, charm.level());
            case LUCK -> applyLuck(player, charm.level());
            case WARDEN -> applyWarden(player, charm.level());
            case MINER -> applyMiner(player, charm.level());
            case OCEAN -> applyOcean(player, charm.level());
            case AIR -> applyAir(player, charm.level());
            case STORAGE -> applyStorage(player, charm.level());
            case TRADER -> applyTrader(player, charm.level());
            case NATURE -> applyNature(player, charm.level());
        }
    }

    private void applyStrength(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 20, level >= 2 ? 1 : 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 20 * 20, 0, true, false, false));
    }

    private void applyHealth(Player player, int level) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            double desired = getHealthMax(level);
            double currentMax = attr.getBaseValue();
            attr.setBaseValue(desired);
            if (currentMax != desired) {
                double currentHealth = player.getHealth();
                double ratio = currentMax <= 0.0 ? 1.0 : Math.min(1.0, currentHealth / currentMax);
                player.setHealth(Math.min(desired, Math.max(1.0, desired * ratio)));
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 20, level == 1 ? 0 : 1, true, false, false));
    }

    private void applyFeather(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, level == 1 ? 0 : 1, true, false, false));
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(getFeatherMax(level));
        }
    }

    private void applyWealth(Player player, int level) {
        applyFortuneToHeldPickaxe(player, getWealthFortuneLevel(level));
    }

    private void applyPhantom(Player player, int level) {
        Boolean visible = player.getPersistentDataContainer().get(phantomVisibleKey, PersistentDataType.BOOLEAN);
        boolean isVisible = visible == null || visible;
        if (!isVisible) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 20, 0, true, false, false));
            if (level >= 2) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 1, true, false, false));
            }
            AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(level == 1 ? 6.0 : 10.0);
            }
        }
    }

    private void applyLuck(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20 * 20, level == 1 ? 0 : 1, true, false, false));
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 20 * 20, getLuckHeroLevel(level), true, false, false));
        }
    }

    private void applyFortuneToHeldPickaxe(Player player, int desiredLevel) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            return;
        }
        if (!held.getType().name().endsWith("PICKAXE")) {
            return;
        }
        int current = held.getEnchantmentLevel(Enchantment.FORTUNE);
        if (current >= 3) {
            return;
        }
        if (current != desiredLevel) {
            held.addUnsafeEnchantment(Enchantment.FORTUNE, desiredLevel);
            player.getInventory().setItemInMainHand(held);
        }
    }

    private void applyWarden(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 20, level == 1 ? 0 : 1, true, false, false));
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 20, 1, true, false, false));
        }
    }

    private void applyMiner(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 20, level == 1 ? 0 : 1, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 20 * 20, plugin.getConfig().getInt(level == 1 ? "charms.miner.level1.hunger-amplifier" : "charms.miner.level2.hunger-amplifier", level == 1 ? 0 : 1), true, false, false));
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 20, 0, true, false, false));
        }
    }

    private void applyOcean(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 20, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, plugin.getConfig().getInt(level == 1 ? "charms.ocean.level1.weakness-amplifier" : "charms.ocean.level2.weakness-amplifier", level == 1 ? 0 : 1), true, false, false));
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 20 * 20, 0, true, false, false));
        }
    }

    private void applyAir(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 20, level == 1 ? 0 : 1, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 0, true, false, false));
        if (level >= 2 && isAirBoostActive(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 1, true, false, false));
        }
    }

    private void applyStorage(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 20, 0, true, false, false));
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 0, true, false, false));
        }
    }

    private void applyTrader(Player player, int level) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            double desired = plugin.getConfig().getDouble("charms.trader.level1.max-health", 17.0);
            attr.setBaseValue(desired);
            double currentHealth = player.getHealth();
            double ratio = currentHealth <= 0.0 ? 1.0 : Math.min(1.0, currentHealth / 20.0);
            player.setHealth(Math.min(desired, Math.max(1.0, desired * ratio)));
        }
        int heroLevel = plugin.getConfig().getInt(level == 1 ? "charms.trader.level1.hero-of-the-village" : "charms.trader.level2.hero-of-the-village", level == 1 ? 2 : 4);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 20 * 20, heroLevel, true, false, false));
        if (level >= 2) {
            int weaknessAmp = plugin.getConfig().getInt("charms.trader.level2.weakness-amplifier", 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, weaknessAmp, true, false, false));
        }
    }

    private void applyNature(Player player, int level) {
        player.getPersistentDataContainer().set(plugin.key("nature_auto_replant"), PersistentDataType.BOOLEAN, true);
        player.getPersistentDataContainer().set(plugin.key("nature_growth_multiplier"), PersistentDataType.DOUBLE, plugin.getConfig().getDouble("charms.nature.level1.crop-growth-multiplier", 2.0));
        if (level >= 2) {
            player.getPersistentDataContainer().set(plugin.key("nature_bonemeal_bonus"), PersistentDataType.BOOLEAN, true);
        }
    }

    private void clearNonPermanentEffects(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void resetAttributes(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0);
        }
        player.getPersistentDataContainer().remove(plugin.key("nature_auto_replant"));
        player.getPersistentDataContainer().remove(plugin.key("nature_growth_multiplier"));
        player.getPersistentDataContainer().remove(plugin.key("nature_bonemeal_bonus"));
    }

    private void removeOneCharmFromInventory(Player player, CharmItem charm) {
        for (ItemStack item : player.getInventory().getContents()) {
            CharmItem found = readCharm(item);
            if (found != null && found.type() == charm.type() && found.level() == charm.level()) {
                int amount = item.getAmount() - 1;
                if (amount <= 0) {
                    player.getInventory().remove(item);
                } else {
                    item.setAmount(amount);
                }
                return;
            }
        }
    }

    public void revealPhantom(Player player) {
        player.getPersistentDataContainer().set(phantomVisibleKey, PersistentDataType.BOOLEAN, true);
        resetAttributes(player);
        clearNonPermanentEffects(player);
    }

    public void togglePhantom(Player player) {
        Boolean visible = player.getPersistentDataContainer().get(phantomVisibleKey, PersistentDataType.BOOLEAN);
        boolean nextVisible = visible == null || !visible;
        player.getPersistentDataContainer().set(phantomVisibleKey, PersistentDataType.BOOLEAN, nextVisible);
        applyEffects(player, getActiveCharm(player));
    }

    public boolean toggleAirBoost(Player player) {
        Boolean active = player.getPersistentDataContainer().get(airBoostKey, PersistentDataType.BOOLEAN);
        boolean next = active == null || !active;
        player.getPersistentDataContainer().set(airBoostKey, PersistentDataType.BOOLEAN, next);
        CharmItem current = getActiveCharm(player);
        if (current != null) {
            applyEffects(player, current);
        }
        return next;
    }

    public boolean isAirBoostActive(Player player) {
        return Boolean.TRUE.equals(player.getPersistentDataContainer().get(airBoostKey, PersistentDataType.BOOLEAN));
    }

    public boolean isCraftingRequired() {
        return plugin.getConfig().getBoolean("recipes.require-crafting", true);
    }

    public boolean canCraftUpgrade(ItemStack[] matrix) {
        if (!isCraftingRequired()) {
            return false;
        }
        return countCharmItems(matrix, 1) == 1
                && count(Material.NETHERITE_INGOT, matrix) == 1
                && count(Material.DIAMOND_BLOCK, matrix) == 3
                && count(Material.TOTEM_OF_UNDYING, matrix) == 1;
    }

    public boolean canCraftSwap(ItemStack[] matrix) {
        if (!isCraftingRequired()) {
            return false;
        }
        return countCharmItems(matrix, 1) == 1
                && count(Material.NETHERITE_INGOT, matrix) == 3
                && count(Material.DIAMOND_BLOCK, matrix) == 1
                && count(Material.TOTEM_OF_UNDYING, matrix) == 2;
    }

    public ItemStack craftUpgradeResult(ItemStack[] matrix) {
        CharmItem charm = firstCharm(matrix);
        if (charm == null || charm.level() >= 2) {
            return null;
        }
        return createCharm(charm.type(), 2);
    }

    public ItemStack craftSwapResult(CharmType outputType) {
        return createCharm(outputType, 1);
    }

    public CharmType pickRandomCharmType() {
        CharmType[] values = CharmType.values();
        return values[random.nextInt(values.length)];
    }

    public List<String> getCharmCatalog() {
        List<String> lines = new ArrayList<>();
        for (CharmType type : CharmType.values()) {
            lines.add(type.name() + " - " + type.displayName() + " (" + type.role() + ")");
        }
        return lines;
    }

    public boolean hasStorage(Player player) {
        return player.getPersistentDataContainer().has(storageDataKey, PersistentDataType.STRING);
    }

    public int getStorageSize(Player player) {
        Integer size = player.getPersistentDataContainer().get(storageSizeKey, PersistentDataType.INTEGER);
        return size == null ? 27 : size;
    }

    public ItemStack[] getStoredItems(Player player) {
        String raw = player.getPersistentDataContainer().get(storageDataKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return new ItemStack[0];
        }
        try {
            byte[] data = Base64.getDecoder().decode(raw);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
            java.io.InputStreamReader reader = new java.io.InputStreamReader(bais);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            int size = config.getInt("size", 0);
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                if (config.contains("items." + i)) {
                    items[i] = config.getItemStack("items." + i);
                }
            }
            return items;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load storage charm data for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return new ItemStack[0];
    }

    public void setStoredItems(Player player, ItemStack[] items, int size) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("size", size);
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && !items[i].getType().isAir()) {
                    config.set("items." + i, items[i]);
                }
            }
            String yaml = config.saveToString();
            String encoded = Base64.getEncoder().encodeToString(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            player.getPersistentDataContainer().set(storageDataKey, PersistentDataType.STRING, encoded);
            player.getPersistentDataContainer().set(storageSizeKey, PersistentDataType.INTEGER, size);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save storage charm data for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ItemStack[] getStoredItemsOffline(UUID owner) {
        java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "storage_" + owner.toString() + ".yml");
        if (!dataFile.exists()) {
            return new ItemStack[0];
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            int size = config.getInt("size", 0);
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                if (config.contains("items." + i)) {
                    items[i] = config.getItemStack("items." + i);
                }
            }
            return items;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load offline storage data for " + owner + ": " + e.getMessage());
            e.printStackTrace();
        }
        return new ItemStack[0];
    }

    public void saveStorageToFile(UUID owner, ItemStack[] items, int size) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "storage_" + owner.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.set("size", size);
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && !items[i].getType().isAir()) {
                    config.set("items." + i, items[i]);
                }
            }
            config.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save offline storage data for " + owner + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveStorageToFile(String storageId, ItemStack[] items, int size) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "storage_" + storageId + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.set("size", size);
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && !items[i].getType().isAir()) {
                    config.set("items." + i, items[i]);
                }
            }
            config.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save offline storage data for " + storageId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getStoragePages(Player player) {
        Integer pages = player.getPersistentDataContainer().get(storagePagesKey, PersistentDataType.INTEGER);
        return pages == null ? 1 : pages;
    }

    public int getStoragePagesOffline(UUID owner) {
        java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "storage_" + owner.toString() + ".yml");
        if (!dataFile.exists()) {
            return 1;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            return config.getInt("pages", 1);
        } catch (Exception e) {
            return 1;
        }
    }

    public ItemStack[] getStoredItemsForPage(Player player, int page, int baseSize) {
        String raw = player.getPersistentDataContainer().get(storageDataKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return new ItemStack[baseSize];
        }
        try {
            byte[] data = Base64.getDecoder().decode(raw);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
            java.io.InputStreamReader reader = new java.io.InputStreamReader(bais);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            int size = config.getInt("size", 0);
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                if (config.contains("items." + i)) {
                    items[i] = config.getItemStack("items." + i);
                }
            }
            // Extract items for the specific page
            int pageStart = page * baseSize;
            ItemStack[] pageItems = new ItemStack[baseSize];
            for (int i = 0; i < baseSize && (pageStart + i) < items.length; i++) {
                pageItems[i] = items[pageStart + i];
            }
            return pageItems;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load storage charm data for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return new ItemStack[baseSize];
    }

    public ItemStack[] getStoredItemsOfflineForPage(UUID owner, int page, int baseSize) {
        // Try to get storage ID first
        String storageId = getStorageIdOffline(owner);
        if (storageId != null) {
            return getStoredItemsByIdForPage(storageId, page, baseSize);
        }
        
        // Fallback to owner UUID
        java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "storage_" + owner.toString() + ".yml");
        if (!dataFile.exists()) {
            return new ItemStack[baseSize];
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            int size = config.getInt("size", 0);
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                if (config.contains("items." + i)) {
                    items[i] = config.getItemStack("items." + i);
                }
            }
            // Extract items for the specific page
            int pageStart = page * baseSize;
            ItemStack[] pageItems = new ItemStack[baseSize];
            for (int i = 0; i < baseSize && (pageStart + i) < items.length; i++) {
                pageItems[i] = items[pageStart + i];
            }
            return pageItems;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load offline storage data for " + owner + ": " + e.getMessage());
            e.printStackTrace();
        }
        return new ItemStack[baseSize];
    }

    public ItemStack[] getStoredItemsByIdForPage(String storageId, int page, int baseSize) {
        java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "storage_" + storageId + ".yml");
        if (!dataFile.exists()) {
            return new ItemStack[baseSize];
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            int size = config.getInt("size", 0);
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                if (config.contains("items." + i)) {
                    items[i] = config.getItemStack("items." + i);
                }
            }
            // Extract items for the specific page
            int pageStart = page * baseSize;
            ItemStack[] pageItems = new ItemStack[baseSize];
            for (int i = 0; i < baseSize && (pageStart + i) < items.length; i++) {
                pageItems[i] = items[pageStart + i];
            }
            return pageItems;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load offline storage data for " + storageId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return new ItemStack[baseSize];
    }

    public void setStoredItemsForPage(Player player, int page, ItemStack[] items, int baseSize) {
        // Load all existing items
        String raw = player.getPersistentDataContainer().get(storageDataKey, PersistentDataType.STRING);
        ItemStack[] allItems = new ItemStack[baseSize * getStoragePages(player)];
        
        if (raw != null && !raw.isBlank()) {
            try {
                byte[] data = Base64.getDecoder().decode(raw);
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
                java.io.InputStreamReader reader = new java.io.InputStreamReader(bais);
                YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
                int size = config.getInt("size", 0);
                for (int i = 0; i < size; i++) {
                    if (config.contains("items." + i)) {
                        allItems[i] = config.getItemStack("items." + i);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load storage charm data for " + player.getName() + ": " + e.getMessage());
            }
        }
        
        // Update the specific page
        int pageStart = page * baseSize;
        for (int i = 0; i < items.length; i++) {
            allItems[pageStart + i] = items[i];
        }
        
        // Save all items
        setStoredItems(player, allItems, allItems.length);
    }

    public void addStorageButtons(org.bukkit.inventory.Inventory inv, int size, int currentPage, int totalPages, Player player, boolean isOwner) {
        int lastRowStart = size - 9;
        
        // Previous page button
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(org.bukkit.Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevButton.setItemMeta(prevMeta);
            inv.setItem(lastRowStart + 3, prevButton);
        }
        
        // Next page button
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(org.bukkit.Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextButton.setItemMeta(nextMeta);
            inv.setItem(lastRowStart + 5, nextButton);
        }
        
        // Page indicator
        ItemStack pageIndicator = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta pageMeta = pageIndicator.getItemMeta();
        pageMeta.setDisplayName(ChatColor.WHITE + "Page " + (currentPage + 1) + "/" + totalPages);
        pageIndicator.setItemMeta(pageMeta);
        inv.setItem(lastRowStart + 4, pageIndicator);
        
        // Gateway button (owner only)
        if (isOwner) {
            ItemStack gatewayButton = new ItemStack(org.bukkit.Material.ENDER_EYE);
            ItemMeta gatewayMeta = gatewayButton.getItemMeta();
            gatewayMeta.setDisplayName(ChatColor.DARK_PURPLE + "Gateway");
            gatewayMeta.setLore(List.of(
                ChatColor.GRAY + "Click to create a gateway item",
                ChatColor.GRAY + "Give it to friends to share your storage"
            ));
            gatewayButton.setItemMeta(gatewayMeta);
            inv.setItem(lastRowStart + 6, gatewayButton);
        }
        
        // Buy page button (owner only)
        if (isOwner && totalPages < 10) { // Max 10 pages
            ItemStack buyButton = new ItemStack(org.bukkit.Material.GOLD_BLOCK);
            ItemMeta buyMeta = buyButton.getItemMeta();
            buyMeta.setDisplayName(ChatColor.GREEN + "Buy New Page");
            buyMeta.setLore(List.of(
                ChatColor.GRAY + "Cost: 3 Netherite Ingots + 1 Diamond Block",
                ChatColor.GRAY + "Click to purchase"
            ));
            buyButton.setItemMeta(buyMeta);
            inv.setItem(lastRowStart + 7, buyButton);
        }
    }

    public void openStorage(Player player, CharmItem charm) {
        int baseSize = charm.level() >= 2 ? STORAGE_LEVEL2_SLOTS : STORAGE_LEVEL1_SLOTS;
        int totalPages = getStoragePages(player);
        Integer currentPageObj = player.getPersistentDataContainer().get(storageCurrentPageKey, PersistentDataType.INTEGER);
        int currentPage = (currentPageObj == null || currentPageObj < 0 || currentPageObj >= totalPages) ? 0 : currentPageObj;
        
        // Use next multiple of 9 to accommodate buttons
        int size = charm.level() >= 2 ? 54 : 36;
        ItemStack[] items = getStoredItemsForPage(player, currentPage, baseSize);
        CharmStorageHolder holder = new CharmStorageHolder(player.getUniqueId(), baseSize, getCharmMenuTitle() + " Storage", false, currentPage, totalPages, -1);
        var inv = Bukkit.createInventory(holder, size, holder.title());
        holder.bind(inv);
        for (int i = 0; i < Math.min(items.length, baseSize); i++) {
            inv.setItem(i, items[i]);
        }
        
        // Add buttons in the last row
        addStorageButtons(inv, size, currentPage, totalPages, player, true);
        
        player.getPersistentDataContainer().set(storageOpenKey, PersistentDataType.BOOLEAN, true);
        player.openInventory(inv);
    }

    public void openSharedStorage(Player viewer, UUID owner, int baseSize) {
        // Use next multiple of 9 to accommodate buttons
        int size = baseSize == 45 ? 54 : 36;
        
        // Get storage ID from gateway item in viewer's hand
        ItemStack gatewayItem = viewer.getInventory().getItemInMainHand();
        String storageId = null;
        int allowedPages = -1; // -1 means all pages
        if (isGateway(gatewayItem)) {
            storageId = gatewayItem.getItemMeta().getPersistentDataContainer().get(storageIdKey, PersistentDataType.STRING);
            String allowedPagesStr = gatewayItem.getItemMeta().getPersistentDataContainer().get(gatewayAllowedPagesKey, PersistentDataType.STRING);
            if (allowedPagesStr != null) {
                allowedPages = Integer.parseInt(allowedPagesStr);
            }
        }
        
        if (storageId == null) {
            storageId = owner.toString(); // Fallback
        }
        
        int totalPages = 1;
        int currentPage = 0;
        
        ItemStack[] items = getStoredItemsByIdForPage(storageId, currentPage, baseSize);
        CharmStorageHolder holder = new CharmStorageHolder(owner, baseSize, getCharmMenuTitle() + " Shared Storage", true, currentPage, totalPages, allowedPages);
        var inv = Bukkit.createInventory(holder, size, holder.title());
        holder.bind(inv);
        for (int i = 0; i < Math.min(items.length, baseSize); i++) {
            inv.setItem(i, items[i]);
        }
        
        // Add buttons in the last row
        addStorageButtons(inv, size, currentPage, totalPages, viewer, false);
        
        viewer.openInventory(inv);
    }

    public void saveStorageFromInventory(Player player, ItemStack[] contents) {
        int size = getStorageSize(player);
        if (size == 0) {
            CharmItem active = getActiveCharm(player);
            if (active != null && active.type() == CharmType.STORAGE) {
                size = active.level() >= 2 ? STORAGE_LEVEL2_SLOTS : STORAGE_LEVEL1_SLOTS;
            }
        }
        
        Integer currentPageObj = player.getPersistentDataContainer().get(storageCurrentPageKey, PersistentDataType.INTEGER);
        int currentPage = (currentPageObj == null) ? 0 : currentPageObj;
        
        ItemStack[] stored = new ItemStack[size];
        for (int i = 0; i < size && i < contents.length; i++) {
            stored[i] = contents[i];
        }
        setStoredItemsForPage(player, currentPage, stored, size);
        
        // Save using storage ID for proper isolation
        String storageId = getStorageId(player);
        if (storageId != null) {
            saveStorageToFile(storageId, stored, size);
            saveStorageIdToFile(player.getUniqueId(), storageId);
        } else {
            // Fallback to player UUID if no storage ID
            saveStorageToFile(player.getUniqueId(), stored, size);
        }
    }

    public void saveAllPlayerData(Player player) {
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof CharmStorageHolder holder) {
            if (holder.owner().equals(player.getUniqueId())) {
                saveStorageFromInventory(player, player.getOpenInventory().getTopInventory().getStorageContents());
            }
        }
    }

    public void splitStorageOnDeath(Player player) {
        ItemStack[] stored = getStoredItems(player);
        if (stored.length == 0) {
            return;
        }
        List<ItemStack> kept = new ArrayList<>();
        List<ItemStack> dropped = new ArrayList<>();
        for (ItemStack item : stored) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (random.nextBoolean()) {
                dropped.add(item);
            } else {
                kept.add(item);
            }
        }
        if (kept.isEmpty() && !dropped.isEmpty()) {
            kept.add(dropped.remove(dropped.size() - 1));
        }
        player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.CHEST));
        for (ItemStack item : dropped) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        setStoredItems(player, kept.toArray(new ItemStack[0]), getStorageSize(player));
    }

    public boolean isStorageCharm(CharmItem charm) {
        return charm != null && charm.type() == CharmType.STORAGE;
    }

    private int count(Material material, ItemStack[] matrix) {
        int total = 0;
        for (ItemStack item : matrix) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private int countCharmItems(ItemStack[] matrix, int minimumLevel) {
        int total = 0;
        for (ItemStack item : matrix) {
            CharmItem charm = readCharm(item);
            if (charm != null && charm.level() >= minimumLevel) {
                total++;
            }
        }
        return total;
    }

    private CharmItem firstCharm(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            CharmItem charm = readCharm(item);
            if (charm != null) {
                return charm;
            }
        }
        return null;
    }

    public NamespacedKey activeKey() {
        return activeKey;
    }

    public NamespacedKey phantomVisibleKey() {
        return phantomVisibleKey;
    }

    public NamespacedKey storageOpenKey() {
        return storageOpenKey;
    }

    public NamespacedKey storagePagesKey() {
        return storagePagesKey;
    }

    public NamespacedKey storageCurrentPageKey() {
        return storageCurrentPageKey;
    }

    public NamespacedKey gatewayAllowedPagesKey() {
        return gatewayAllowedPagesKey;
    }

    public NamespacedKey storageIdKey() {
        return storageIdKey;
    }

    public String getStorageId(Player player) {
        CharmItem active = getActiveCharm(player);
        if (active == null || active.type() != CharmType.STORAGE) {
            return null;
        }
        ItemStack charm = player.getInventory().getItemInMainHand();
        if (charm == null || !isCharm(charm)) {
            charm = player.getInventory().getItemInOffHand();
        }
        if (charm == null || !isCharm(charm)) {
            return null;
        }
        return charm.getItemMeta().getPersistentDataContainer().get(storageIdKey, PersistentDataType.STRING);
    }

    public String getStorageIdFromCharm(ItemStack charm) {
        if (charm == null || !isCharm(charm)) {
            return null;
        }
        return charm.getItemMeta().getPersistentDataContainer().get(storageIdKey, PersistentDataType.STRING);
    }

    public String getStorageIdOffline(UUID owner) {
        java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "storage_" + owner.toString() + ".yml");
        if (!dataFile.exists()) {
            return null;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            return config.getString("storage_id");
        } catch (Exception e) {
            return null;
        }
    }

    public void saveStorageIdToFile(UUID owner, String storageId) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "storage_" + owner.toString() + ".yml");
            YamlConfiguration config;
            if (dataFile.exists()) {
                config = YamlConfiguration.loadConfiguration(dataFile);
            } else {
                config = new YamlConfiguration();
            }
            config.set("storage_id", storageId);
            config.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save storage ID for " + owner + ": " + e.getMessage());
        }
    }

    public NamespacedKey natureAutoReplantKey() {
        return plugin.key("nature_auto_replant");
    }

    public NamespacedKey natureGrowthMultiplierKey() {
        return plugin.key("nature_growth_multiplier");
    }

    public NamespacedKey natureBonemealBonusKey() {
        return plugin.key("nature_bonemeal_bonus");
    }

    public String getCharmMenuTitle() {
        return color(plugin.getConfig().getString("menu.title", "&6SMP Charms"));
    }

    public double getHealthMax(int level) {
        return plugin.getConfig().getDouble(level == 1 ? "charms.health.level1.max-health" : "charms.health.level2.max-health", level == 1 ? 26.0 : 30.0);
    }

    public double getFeatherMax(int level) {
        return plugin.getConfig().getDouble(level == 1 ? "charms.feather.level1.max-health" : "charms.feather.level2.max-health", level == 1 ? 16.0 : 14.0);
    }

    public int getWealthFortuneLevel(int level) {
        return plugin.getConfig().getInt(level == 1 ? "charms.wealth.level1.fortune" : "charms.wealth.level2.fortune", level == 1 ? 2 : 3);
    }

    public int getLuckHeroLevel(int level) {
        return plugin.getConfig().getInt(level == 1 ? "charms.luck.level1.hero-of-the-village" : "charms.luck.level2.hero-of-the-village", level == 1 ? 0 : 2);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String getPowerLine(CharmType type, int level) {
        return switch (type) {
            case STRENGTH -> level == 1 ? "Strength I" : "Strength II burst";
            case HEALTH -> level == 1 ? "+3 hearts" : "+5 hearts";
            case FEATHER -> level == 1 ? "Speed I + fall dampening" : "Speed II + no fall damage";
            case WEALTH -> level == 1 ? "Fortune II" : "Fortune III + loot boost";
            case PHANTOM -> level == 1 ? "Toggle invisibility" : "Toggle invisibility + speed";
            case LUCK -> level == 1 ? "Luck boosts and better drops" : "Stronger loot luck and village favor";
            case WARDEN -> level == 1 ? "Resistance and guard power" : "Higher resistance and absorption";
            case MINER -> level == 1 ? "Haste I" : "Haste II + night vision";
            case OCEAN -> level == 1 ? "Water breathing" : "Water breathing + dolphin's grace";
            case AIR -> level == 1 ? "Slow falling + jump boost" : "Toggle speed boost";
            case STORAGE -> level == 1 ? "27-slot vault" : "45-slot vault";
            case TRADER -> level == 1 ? "Hero of the Village II" : "Hero of the Village IV";
            case NATURE -> level == 1 ? "Auto-replant crops + 2x growth" : "Auto-replant + 2x growth + bonemeal bonus";
        };
    }

    private String getDebuffLine(CharmType type, int level) {
        return switch (type) {
            case STRENGTH -> level == 1 ? "Increased hunger drain" : "Hunger drain + heart bleed on burst";
            case HEALTH -> level == 1 ? "Slowness I" : "Slowness II";
            case FEATHER -> level == 1 ? "-2 hearts max HP" : "-3 hearts max HP";
            case WEALTH -> level == 1 ? "Reduced PvP damage" : "Stronger PvP damage reduction";
            case PHANTOM -> level == 1 ? "Lower max HP while invisible" : "Higher hunger drain and reveal on hit";
            case LUCK -> level == 1 ? "Slight combat weakness" : "Higher combat weakness";
            case WARDEN -> level == 1 ? "Lower movement speed" : "Heavier movement penalty";
            case MINER -> level == 1 ? "Hunger I" : "Hunger II";
            case OCEAN -> level == 1 ? "Weakness I" : "Weakness II";
            case AIR -> level == 1 ? "Weakness I" : "Weakness II (boosted)";
            case STORAGE -> level == 1 ? "Slowness I" : "Slowness I + Weakness I";
            case TRADER -> level == 1 ? "-3 hearts max HP" : "-3 hearts max HP + Weakness I";
            case NATURE -> level == 1 ? "None" : "None";
        };
    }
}


