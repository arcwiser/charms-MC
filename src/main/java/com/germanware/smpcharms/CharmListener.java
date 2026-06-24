package com.germanware.smpcharms;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;
import java.util.UUID;

public final class CharmListener implements Listener {
    private final SMPCharmsPlugin plugin;
    private final CharmService service;
    private final CharmMenu menu;
    private final Random random = new Random();

    public CharmListener(SMPCharmsPlugin plugin, CharmService service) {
        this.plugin = plugin;
        this.service = service;
        this.menu = new CharmMenu(service);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!service.hasActiveCharm(player)) {
            service.giveStarterPackage(player);
            player.sendMessage(ChatColor.GOLD + "You have joined as a Member.");
            player.sendMessage(ChatColor.YELLOW + "You received 1 free reroll and a random starter charm.");
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof CharmStorageHolder holder) {
            if (holder.owner().equals(player.getUniqueId())) {
                service.saveStorageFromInventory(player, player.getOpenInventory().getTopInventory().getStorageContents());
                player.getPersistentDataContainer().remove(service.storageOpenKey());
            }
        }
        CharmItem active = service.getActiveCharm(player);
        if (active != null && active.type() == CharmType.STORAGE && service.hasStorage(player)) {
            service.saveStorageFromInventory(player, service.getStoredItems(player));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        if (service.isMemberBadge(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (service.rerollStarter(event.getPlayer())) {
                decrementMemberBadge(event.getPlayer(), item);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Your starter charm was rerolled.");
            } else {
                event.getPlayer().sendMessage(ChatColor.RED + "You have no rerolls left.");
            }
            return;
        }

        if (service.isUpgradeCatalyst(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            
            // Check the other hand for a charm (not the hand holding the catalyst)
            ItemStack otherHand = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND 
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
            
            if (otherHand != null && service.isCharm(otherHand)) {
                CharmItem charm = service.readCharm(otherHand);
                if (charm != null && charm.level() < 2) {
                    ItemStack upgraded = service.createCharm(charm.type(), 2);
                    if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                        event.getPlayer().getInventory().setItemInOffHand(upgraded);
                    } else {
                        event.getPlayer().getInventory().setItemInMainHand(upgraded);
                    }
                    event.getItem().setAmount(event.getItem().getAmount() - 1);
                    event.getPlayer().sendMessage(ChatColor.GREEN + "Charm upgraded to Lv.2");
                    return;
                }
            }
            event.getPlayer().sendMessage(ChatColor.RED + "Hold a charm in your other hand to upgrade it.");
            return;
        }

        if (service.isSwapCatalyst(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            
            // Check if right-clicking a player
            if (event.getClickedBlock() == null && event.getAction() == Action.RIGHT_CLICK_AIR) {
                // Raycast to find if clicking on a player
                org.bukkit.util.RayTraceResult rayTrace = event.getPlayer().rayTraceEntities(5);
                if (rayTrace != null && rayTrace.getHitEntity() instanceof Player target) {
                    CharmItem targetCharm = service.getActiveCharm(target);
                    if (targetCharm != null) {
                        CharmItem newCharm = new CharmItem(service.pickRandomCharmType(), 1);
                        service.setActiveCharm(target, newCharm);
                        target.getInventory().setItemInMainHand(service.createCharm(newCharm.type(), 1));
                        event.getItem().setAmount(event.getItem().getAmount() - 1);
                        event.getPlayer().sendMessage(ChatColor.GREEN + "Swapped " + target.getName() + "'s charm to " + newCharm.type().displayName() + " Lv.1");
                        target.sendMessage(ChatColor.YELLOW + event.getPlayer().getName() + " swapped your charm to " + newCharm.type().displayName() + " Lv.1");
                        return;
                    } else {
                        event.getPlayer().sendMessage(ChatColor.RED + target.getName() + " has no active charm.");
                        return;
                    }
                }
            }
            
            // Check the other hand for a charm (not the hand holding the catalyst)
            ItemStack otherHand = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND 
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
            
            if (otherHand != null && service.isCharm(otherHand)) {
                CharmItem newCharm = new CharmItem(service.pickRandomCharmType(), 1);
                ItemStack swapped = service.createCharm(newCharm.type(), 1);
                if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                    event.getPlayer().getInventory().setItemInOffHand(swapped);
                } else {
                    event.getPlayer().getInventory().setItemInMainHand(swapped);
                }
                event.getItem().setAmount(event.getItem().getAmount() - 1);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Charm swapped to " + newCharm.type().displayName() + " Lv.1");
                return;
            }
            event.getPlayer().sendMessage(ChatColor.RED + "Hold a charm in your other hand to swap it.");
            return;
        }

        if (service.isGateway(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            UUID owner = service.getGatewayOwner(item);
            if (owner != null) {
                CharmItem active = service.getActiveCharm(event.getPlayer());
                int baseSize = 27; // Default to level 1 size
                if (active != null && active.type() == CharmType.STORAGE) {
                    baseSize = active.level() >= 2 ? 45 : 27;
                }
                service.openSharedStorage(event.getPlayer(), owner, baseSize);
            }
            return;
        }

        if (!service.isCharm(item)) {
            return;
        }

        CharmItem charm = service.readCharm(item);
        if (charm == null) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
        }

        service.setActiveCharm(event.getPlayer(), charm);

        if (charm.type() == CharmType.AIR && charm.level() >= 2 && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            boolean enabled = service.toggleAirBoost(event.getPlayer());
            event.getPlayer().sendMessage(ChatColor.GRAY + "Air boost " + (enabled ? "enabled" : "disabled") + ".");
            return;
        }

        if (charm.type() == CharmType.STORAGE && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            service.openStorage(event.getPlayer(), charm);
            return;
        }

        if (charm.type() == CharmType.PHANTOM) {
            service.togglePhantom(event.getPlayer());
            event.getPlayer().sendMessage(ChatColor.GRAY + "Phantom cloak toggled.");
            return;
        }

        event.getPlayer().sendMessage(ChatColor.GOLD + "Activated " + charm.type().displayName() + ChatColor.GRAY + " Lv." + charm.level());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (service.isCharm(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        CharmItem active = service.getActiveCharm(player);
        if (active == null) {
            return;
        }

        if (active.type() == CharmType.FEATHER && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setDamage(active.level() >= 2 ? 0.0 : event.getDamage() * 0.5);
        }

        if (active.type() == CharmType.WARDEN) {
            event.setDamage(event.getDamage() * (active.level() >= 2 ? 0.75 : 0.85));
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        CharmItem active = service.getActiveCharm(attacker);
        if (active == null) {
            return;
        }

        if (active.type() == CharmType.WEALTH || active.type() == CharmType.LUCK) {
            event.setDamage(event.getDamage() * (active.level() >= 2 ? 0.8 : 0.9));
        }

        if (active.type() == CharmType.PHANTOM) {
            service.revealPhantom(attacker);
            attacker.sendMessage(ChatColor.RED + "You were revealed!");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        CharmItem active = service.getActiveCharm(player);
        if (active == null || active.type() != CharmType.NATURE) {
            return;
        }

        Boolean autoReplant = player.getPersistentDataContainer().get(service.natureAutoReplantKey(), PersistentDataType.BOOLEAN);
        if (autoReplant == null || !autoReplant) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();

        // List of crops that can be auto-replanted
        if (isCrop(type)) {
            if (block.getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() == ageable.getMaximumAge()) {
                    // Crop is fully grown, drop seeds and replant
                    event.setDropItems(false); // Cancel default drops

                    // Get the seed item for this crop
                    ItemStack seed = getSeedForCrop(type);
                    if (seed != null) {
                        // Drop the harvested crop items
                        for (ItemStack drop : block.getDrops()) {
                            block.getWorld().dropItemNaturally(block.getLocation(), drop);
                        }

                        // Replant the seed
                        block.setType(type);
                        if (block.getBlockData() instanceof Ageable newAgeable) {
                            newAgeable.setAge(0);
                        }
                    }
                }
            }
        }
    }

    private boolean isCrop(Material type) {
        return type == Material.WHEAT ||
               type == Material.CARROTS ||
               type == Material.POTATOES ||
               type == Material.BEETROOTS ||
               type == Material.NETHER_WART ||
               type == Material.COCOA;
    }

    private ItemStack getSeedForCrop(Material crop) {
        return switch (crop) {
            case WHEAT -> new ItemStack(Material.WHEAT_SEEDS);
            case CARROTS -> new ItemStack(Material.CARROT);
            case POTATOES -> new ItemStack(Material.POTATO);
            case BEETROOTS -> new ItemStack(Material.BEETROOT_SEEDS);
            case NETHER_WART -> new ItemStack(Material.NETHER_WART);
            case COCOA -> new ItemStack(Material.COCOA_BEANS);
            default -> null;
        };
    }

    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        // Check if this is a crop block
        if (!isCrop(block.getType())) {
            return;
        }

        // Find the nearest player within 16 blocks
        for (org.bukkit.entity.Entity entity : block.getWorld().getNearbyEntities(block.getLocation(), 16, 16, 16)) {
            if (entity instanceof Player player) {
                CharmItem active = service.getActiveCharm(player);
                if (active != null && active.type() == CharmType.NATURE) {
                    Double multiplier = player.getPersistentDataContainer().get(service.natureGrowthMultiplierKey(), PersistentDataType.DOUBLE);
                    if (multiplier != null && multiplier > 1.0) {
                        // Apply growth multiplier by randomly advancing growth
                        if (block.getBlockData() instanceof Ageable ageable) {
                            if (random.nextDouble() < (multiplier - 1.0) * 0.5) {
                                int newAge = Math.min(ageable.getAge() + 1, ageable.getMaximumAge());
                                ageable.setAge(newAge);
                            }
                        }
                    }
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        CharmItem active = service.getActiveCharm(player);
        if (active == null) {
            return;
        }

        if (active.type() == CharmType.STRENGTH) {
            event.setFoodLevel(Math.max(0, event.getFoodLevel() - (active.level() >= 2 ? 2 : 1)));
        }
        if (active.type() == CharmType.PHANTOM && Boolean.FALSE.equals(player.getPersistentDataContainer().get(service.phantomVisibleKey(), PersistentDataType.BOOLEAN))) {
            event.setFoodLevel(Math.max(0, event.getFoodLevel() - (active.level() >= 2 ? 2 : 1)));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.getDrops().removeIf(service::isCharm);
            CharmItem active = service.getActiveCharm(player);
            if (active != null && service.isStorageCharm(active)) {
                service.splitStorageOnDeath(player);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        CharmItem active = service.getActiveCharm(player);
        if (active == null) {
            return;
        }

        Integer slot = service.getActiveSlot(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack charmItem = service.createCharm(active.type(), active.level());
            if (slot != null && slot >= 0 && slot < player.getInventory().getSize()) {
                player.getInventory().setItem(slot, charmItem.clone());
                player.getInventory().setHeldItemSlot(slot);
            } else {
                player.getInventory().setItemInMainHand(charmItem.clone());
            }
            service.applyEffects(player, active);
        });
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory inventory)) {
            return;
        }
        if (service.canCraftUpgrade(inventory.getMatrix())) {
            inventory.setResult(service.craftUpgradeResult(inventory.getMatrix()));
        } else if (service.canCraftSwap(inventory.getMatrix())) {
            inventory.setResult(service.craftSwapResult(service.pickRandomCharmType()));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof CharmStorageHolder holder)) {
            return;
        }
        // Save changes for both owner and shared users
        // Get the owner player to save to their data
        org.bukkit.entity.Player ownerPlayer = Bukkit.getPlayer(holder.owner());
        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            // Skip the gateway button slot when saving
            ItemStack[] contents = event.getInventory().getStorageContents();
            ItemStack[] storageContents = new ItemStack[holder.size()];
            System.arraycopy(contents, 0, storageContents, 0, holder.size());
            service.saveStorageFromInventory(ownerPlayer, storageContents);
        }
        if (holder.owner().equals(player.getUniqueId())) {
            player.getPersistentDataContainer().remove(service.storageOpenKey());
        }
    }

    @EventHandler
    public void onStorageClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CharmStorageHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // Only handle clicks in the storage inventory (top inventory), not player inventory
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            // Click is in player's inventory, allow normal behavior
            return;
        }
        
        int slot = event.getRawSlot();
        int totalSize = event.getInventory().getSize();
        int lastRowStart = totalSize - 9;
        
        // Check if clicking buttons in the last row
        if (slot >= lastRowStart) {
            event.setCancelled(true);
            
            // Previous page button
            if (slot == lastRowStart + 3 && holder.currentPage() > 0) {
                player.getPersistentDataContainer().set(service.storageCurrentPageKey(), PersistentDataType.INTEGER, holder.currentPage() - 1);
                player.closeInventory();
                CharmItem active = service.getActiveCharm(player);
                if (active != null && active.type() == CharmType.STORAGE) {
                    service.openStorage(player, active);
                }
                return;
            }
            
            // Next page button
            if (slot == lastRowStart + 5 && holder.currentPage() < holder.totalPages() - 1) {
                player.getPersistentDataContainer().set(service.storageCurrentPageKey(), PersistentDataType.INTEGER, holder.currentPage() + 1);
                player.closeInventory();
                CharmItem active = service.getActiveCharm(player);
                if (active != null && active.type() == CharmType.STORAGE) {
                    service.openStorage(player, active);
                }
                return;
            }
            
            // Gateway button
            if (slot == lastRowStart + 6) {
                if (holder.owner().equals(player.getUniqueId()) && !holder.isShared()) {
                    ItemStack gateway = service.createGateway(holder.owner());
                    player.getInventory().addItem(gateway);
                    player.sendMessage(ChatColor.GREEN + "Gateway created! Give it to friends to share your storage.");
                } else {
                    player.sendMessage(ChatColor.RED + "Only the storage owner can create gateways.");
                }
                return;
            }
            
            // Gateway config button
            if (slot == lastRowStart + 8) {
                if (holder.owner().equals(player.getUniqueId())) {
                    service.openGatewayConfig(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Only the storage owner can configure gateways.");
                }
                return;
            }
            
            // Buy page button
            if (slot == lastRowStart + 7) {
                if (holder.owner().equals(player.getUniqueId()) && holder.totalPages() < 10) {
                    // Check if player has required items
                    int netheriteCount = 0;
                    int diamondBlockCount = 0;
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null) {
                            if (item.getType() == Material.NETHERITE_INGOT) {
                                netheriteCount += item.getAmount();
                            } else if (item.getType() == Material.DIAMOND_BLOCK) {
                                diamondBlockCount += item.getAmount();
                            }
                        }
                    }
                    
                    if (netheriteCount >= 3 && diamondBlockCount >= 1) {
                        // Remove items
                        int remainingNetherite = 3;
                        int remainingDiamondBlock = 1;
                        for (ItemStack item : player.getInventory().getContents()) {
                            if (item != null && remainingNetherite > 0 && item.getType() == Material.NETHERITE_INGOT) {
                                int toRemove = Math.min(item.getAmount(), remainingNetherite);
                                item.setAmount(item.getAmount() - toRemove);
                                remainingNetherite -= toRemove;
                            }
                        }
                        for (ItemStack item : player.getInventory().getContents()) {
                            if (item != null && remainingDiamondBlock > 0 && item.getType() == Material.DIAMOND_BLOCK) {
                                int toRemove = Math.min(item.getAmount(), remainingDiamondBlock);
                                item.setAmount(item.getAmount() - toRemove);
                                remainingDiamondBlock -= toRemove;
                            }
                        }
                        
                        // Add page
                        int newPages = holder.totalPages() + 1;
                        player.getPersistentDataContainer().set(service.storagePagesKey(), PersistentDataType.INTEGER, newPages);
                        player.sendMessage(ChatColor.GREEN + "Purchased a new storage page! Total pages: " + newPages);
                        player.closeInventory();
                        CharmItem active = service.getActiveCharm(player);
                        if (active != null && active.type() == CharmType.STORAGE) {
                            service.openStorage(player, active);
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "You need 3 Netherite Ingots and 1 Diamond Block to buy a page.");
                    }
                }
                return;
            }
            
            // Page indicator - do nothing
            if (slot == lastRowStart + 4) {
                return;
            }
            
            // Other slots in last row are blocked
            return;
        }
        
        // Allow both owner and shared users to modify storage
        // Do not cancel - allow normal inventory operations
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (!menu.title().equals(view.getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 22) {
            player.closeInventory();
            return;
        }
        if (slot == 18) {
            if (player.hasPermission("smpcharms.admin")) {
                plugin.reloadConfig();
                player.sendMessage(ChatColor.GREEN + "SMP Charms config reloaded.");
            } else {
                player.sendMessage(ChatColor.RED + "No permission.");
            }
            return;
        }
        if (slot == 10) {
            CharmItem active = service.getActiveCharm(player);
            player.sendMessage(ChatColor.AQUA + "Active: " + (active == null ? "none" : active.type().displayName() + " Lv." + active.level()));
        } else if (slot == 12) {
            if (service.rerollStarter(player)) {
                player.sendMessage(ChatColor.GREEN + "Your starter charm has been rerolled.");
            } else {
                player.sendMessage(ChatColor.RED + "You have no rerolls left.");
            }
        } else if (slot == 14) {
            for (String line : service.getCharmCatalog()) {
                player.sendMessage(ChatColor.GRAY + "- " + line);
            }
        } else if (slot == 16) {
            player.sendMessage(ChatColor.GRAY + "Strength = burst damage");
            player.sendMessage(ChatColor.GRAY + "Health = tank");
            player.sendMessage(ChatColor.GRAY + "Feather = mobility");
            player.sendMessage(ChatColor.GRAY + "Wealth = pickaxe fortune + economy");
            player.sendMessage(ChatColor.GRAY + "Phantom = stealth");
            player.sendMessage(ChatColor.GRAY + "Luck = village favor + loot luck");
            player.sendMessage(ChatColor.GRAY + "Warden = damage soak");
            player.sendMessage(ChatColor.GRAY + "Miner = mining speed and vision");
            player.sendMessage(ChatColor.GRAY + "Ocean = underwater mobility");
            player.sendMessage(ChatColor.GRAY + "Air = aerial mobility");
            player.sendMessage(ChatColor.GRAY + "Sneak-right-click Air level 2 to toggle the speed boost.");
        } else if (slot == 20) {
            player.sendMessage(ChatColor.BLUE + "Upgrade Recipe:");
            player.sendMessage(ChatColor.GRAY + "1 base charm + 1 Netherite + 3 Diamond Blocks + 1 Totem");
            player.sendMessage(ChatColor.BLUE + "Swap Recipe:");
            player.sendMessage(ChatColor.GRAY + "1 base charm + 3 Netherite + 1 Diamond Block + 2 Totems");
        }
    }

    @EventHandler
    public void onGatewayConfigClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.BLUE + "Gateway Configuration")) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        
        // Page toggle buttons (slots 0-9)
        if (slot >= 0 && slot < 10) {
            Integer allowedPagesObj = player.getPersistentDataContainer().get(service.gatewayAllowedPagesKey(), PersistentDataType.INTEGER);
            int allowedPages = (allowedPagesObj == null) ? -1 : allowedPagesObj;
            
            if (allowedPages == -1) {
                allowedPages = 0; // Start with none if previously all
                // Set all pages as allowed
                for (int i = 0; i < 10; i++) {
                    allowedPages |= (1 << i);
                }
            }
            
            int pageBit = 1 << slot;
            allowedPages ^= pageBit; // Toggle the bit
            player.getPersistentDataContainer().set(service.gatewayAllowedPagesKey(), PersistentDataType.INTEGER, allowedPages);
            
            // Refresh the inventory
            service.openGatewayConfig(player);
            return;
        }
        
        // Allow All button (slot 45)
        if (slot == 45) {
            player.getPersistentDataContainer().set(service.gatewayAllowedPagesKey(), PersistentDataType.INTEGER, -1);
            service.openGatewayConfig(player);
            player.sendMessage(ChatColor.GREEN + "All pages allowed");
            return;
        }
        
        // Deny All button (slot 46)
        if (slot == 46) {
            player.getPersistentDataContainer().set(service.gatewayAllowedPagesKey(), PersistentDataType.INTEGER, 0);
            service.openGatewayConfig(player);
            player.sendMessage(ChatColor.RED + "All pages denied");
            return;
        }
        
        // Save button (slot 49)
        if (slot == 49) {
            Integer allowedPagesObj = player.getPersistentDataContainer().get(service.gatewayAllowedPagesKey(), PersistentDataType.INTEGER);
            int allowedPages = (allowedPagesObj == null) ? -1 : allowedPagesObj;
            
            // Apply to all gateways in player's inventory
            int updated = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (service.isGateway(item) && service.getGatewayOwner(item).equals(player.getUniqueId())) {
                    service.setGatewayAllowedPages(item, allowedPages);
                    updated++;
                }
            }
            
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Saved! Updated " + updated + " gateway(s).");
            return;
        }
    }

    private void decrementMemberBadge(Player player, ItemStack badge) {
        int rerolls = Math.max(0, service.getRerolls(player));
        service.setRerolls(player, rerolls);
        player.getInventory().removeItem(badge);
        if (rerolls > 0) {
            player.getInventory().addItem(service.createMemberBadge(rerolls));
        }
    }
}





