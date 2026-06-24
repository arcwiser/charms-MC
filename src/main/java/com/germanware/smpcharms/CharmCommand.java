package com.germanware.smpcharms;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CharmCommand implements CommandExecutor, TabCompleter {
    private final SMPCharmsPlugin plugin;
    private final CharmService service;
    private final Map<UUID, UUID> tradeRequests = new HashMap<>();

    public CharmCommand(SMPCharmsPlugin plugin, CharmService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run this in-game.");
            return true;
        }

        String cmd = command.getName().toLowerCase();
        if (cmd.equals("recipe")) {
            showRecipe(player);
            return true;
        }
        if (cmd.equals("trade")) {
            return handleTrade(player, args);
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> showHelp(player);
            case "list" -> showList(player);
            case "info" -> {
                CharmItem active = service.getActiveCharm(player);
                player.sendMessage(ChatColor.AQUA + "Active: " + (active == null ? "none" : active.type().displayName() + " Lv." + active.level()));
                player.sendMessage(ChatColor.AQUA + "Rerolls: " + service.getRerolls(player));
            }
            case "reroll" -> {
                if (service.rerollStarter(player)) {
                    player.sendMessage(ChatColor.GREEN + "Your starter charm has been rerolled.");
                } else {
                    player.sendMessage(ChatColor.RED + "You have no rerolls left.");
                }
            }
            case "upgrade" -> {
                if (!player.hasPermission("smpcharms.admin")) {
                    noPerm(player);
                    return true;
                }
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (!service.isCharm(inHand)) {
                    player.sendMessage(ChatColor.RED + "Hold a charm first.");
                    return true;
                }
                player.getInventory().setItemInMainHand(service.upgradeCharm(inHand));
                player.sendMessage(ChatColor.GREEN + "Charm upgraded.");
            }
            case "give" -> {
                if (!player.hasPermission("smpcharms.admin")) {
                    noPerm(player);
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /charm give <type> [level]");
                    return true;
                }
                CharmType type = CharmType.valueOf(args[1].toUpperCase());
                int level = args.length > 2 ? Integer.parseInt(args[2]) : 1;
                player.getInventory().addItem(service.createCharm(type, level));
                player.sendMessage(ChatColor.GREEN + "Given " + type.displayName() + " Lv." + level);
            }
            case "swap" -> {
                if (!player.hasPermission("smpcharms.admin")) {
                    noPerm(player);
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /charm swap <player> <type> or /charm swap <type>");
                    return true;
                }
                // Check if first arg is a player name or charm type
                Player target = plugin.getServer().getPlayer(args[1]);
                CharmType type;
                if (target != null && target.isOnline() && args.length >= 3) {
                    // /charm swap <player> <type>
                    try {
                        type = CharmType.valueOf(args[2].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "Invalid charm type: " + args[2]);
                        return true;
                    }
                    service.fullySwapCharm(target, type);
                    player.sendMessage(ChatColor.GREEN + "Swapped " + target.getName() + "'s charm to " + type.displayName() + " Lv.1");
                    target.sendMessage(ChatColor.YELLOW + player.getName() + " swapped your charm to " + type.displayName() + " Lv.1");
                } else {
                    // /charm swap <type> - swap own charm
                    try {
                        type = CharmType.valueOf(args[1].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "Invalid charm type: " + args[1]);
                        return true;
                    }
                    service.fullySwapCharm(player, type);
                    player.sendMessage(ChatColor.GREEN + "Charm fully swapped to " + type.displayName() + " Lv.1");
                }
            }
            case "member" -> {
                if (!player.hasPermission("smpcharms.admin")) {
                    noPerm(player);
                    return true;
                }
                player.getInventory().addItem(service.createMemberBadge(Math.max(1, service.getRerolls(player))));
                player.sendMessage(ChatColor.GREEN + "Member badge granted.");
            }
            case "rerollall" -> {
                if (!player.hasPermission("smpcharms.admin")) {
                    noPerm(player);
                    return true;
                }
                int count = 0;
                for (Player target : plugin.getServer().getOnlinePlayers()) {
                    service.rerollPlayerCharm(target);
                    count++;
                }
                player.sendMessage(ChatColor.GREEN + "Rerolled charms for " + count + " online players.");
            }
            case "storage" -> showStorageInfo(player);
            case "giveupgradeitem" -> {
                if (!player.hasPermission("smpcharms.admin")) {
                    noPerm(player);
                    return true;
                }
                player.getInventory().addItem(service.createUpgradeCatalyst());
                player.sendMessage(ChatColor.GREEN + "Upgrade catalyst given.");
            }
            case "giveswapitem" -> {
                if (!player.hasPermission("smpcharms.admin")) {
                    noPerm(player);
                    return true;
                }
                player.getInventory().addItem(service.createSwapCatalyst());
                player.sendMessage(ChatColor.GREEN + "Swap catalyst given.");
            }
            case "gateway" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /charm gateway <allow|deny> <page>");
                    return true;
                }
                ItemStack gatewayItem = player.getInventory().getItemInMainHand();
                if (!service.isGateway(gatewayItem)) {
                    player.sendMessage(ChatColor.RED + "Hold a gateway item in your main hand.");
                    return true;
                }
                UUID gatewayOwner = service.getGatewayOwner(gatewayItem);
                if (!player.getUniqueId().equals(gatewayOwner)) {
                    player.sendMessage(ChatColor.RED + "You can only configure your own gateways.");
                    return true;
                }
                String action = args[1].toLowerCase();
                int page;
                try {
                    page = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid page number.");
                    return true;
                }
                if (page < 1 || page > 10) {
                    player.sendMessage(ChatColor.RED + "Page must be between 1 and 10.");
                    return true;
                }
                int allowedPages = service.getGatewayAllowedPages(gatewayItem);
                if (allowedPages == -1) {
                    allowedPages = 0; // Start with none if previously all
                }
                int pageBit = 1 << (page - 1);
                if (action.equals("allow")) {
                    allowedPages |= pageBit;
                    player.sendMessage(ChatColor.GREEN + "Allowed access to page " + page);
                } else if (action.equals("deny")) {
                    allowedPages &= ~pageBit;
                    player.sendMessage(ChatColor.GREEN + "Denied access to page " + page);
                } else if (action.equals("all")) {
                    allowedPages = -1;
                    player.sendMessage(ChatColor.GREEN + "Allowed access to all pages");
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /charm gateway <allow|deny|all> <page>");
                    return true;
                }
                service.setGatewayAllowedPages(gatewayItem, allowedPages);
            }
            case "reload" -> {
                if (!player.hasPermission("smpcharms.admin")) {
                    noPerm(player);
                    return true;
                }
                plugin.reloadConfig();
                player.sendMessage(ChatColor.GREEN + "SMP Charms config reloaded.");
            }
            case "menu" -> player.openInventory(new CharmMenu(service).create(service.getActiveCharm(player), service.getRerolls(player)));
            case "recipe" -> showRecipe(player);
            default -> showHelp(player);
        }

        return true;
    }

    private boolean handleTrade(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /trade <player|accept|deny>");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("accept")) {
            UUID requesterId = tradeRequests.remove(player.getUniqueId());
            if (requesterId == null) {
                player.sendMessage(ChatColor.RED + "You have no pending trade requests.");
                return true;
            }
            Player requester = plugin.getServer().getPlayer(requesterId);
            if (requester == null || !requester.isOnline()) {
                player.sendMessage(ChatColor.RED + "The request expired.");
                return true;
            }
            ItemStack requesterCharm = requester.getInventory().getItemInMainHand();
            ItemStack targetCharm = player.getInventory().getItemInMainHand();
            if (!service.isCharm(requesterCharm) || !service.isCharm(targetCharm)) {
                player.sendMessage(ChatColor.RED + "Both players must hold a charm.");
                return true;
            }
            requester.getInventory().setItemInMainHand(targetCharm.clone());
            player.getInventory().setItemInMainHand(requesterCharm.clone());
            requester.sendMessage(ChatColor.GREEN + "Trade completed with " + player.getName() + ".");
            player.sendMessage(ChatColor.GREEN + "Trade completed with " + requester.getName() + ".");
            return true;
        }

        if (sub.equals("deny")) {
            UUID requesterId = tradeRequests.remove(player.getUniqueId());
            if (requesterId == null) {
                player.sendMessage(ChatColor.RED + "You have no pending trade requests.");
                return true;
            }
            Player requester = plugin.getServer().getPlayer(requesterId);
            if (requester != null) {
                requester.sendMessage(ChatColor.RED + player.getName() + " denied your trade request.");
            }
            player.sendMessage(ChatColor.YELLOW + "Trade denied.");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You cannot trade with yourself.");
            return true;
        }

        if (!service.isCharm(player.getInventory().getItemInMainHand())) {
            player.sendMessage(ChatColor.RED + "Hold a charm in your main hand to trade.");
            return true;
        }
        if (!service.isCharm(target.getInventory().getItemInMainHand())) {
            player.sendMessage(ChatColor.RED + target.getName() + " is not holding a charm.");
            return true;
        }

        tradeRequests.put(target.getUniqueId(), player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Trade request sent to " + target.getName() + ".");
        target.sendMessage(ChatColor.YELLOW + player.getName() + " wants to trade charms with you.");
        target.sendMessage(ChatColor.GRAY + "Type /trade accept or /trade deny.");
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "SMP Charms Commands");
        player.sendMessage(ChatColor.GRAY + "/charm help");
        player.sendMessage(ChatColor.GRAY + "/charm list");
        player.sendMessage(ChatColor.GRAY + "/charm info");
        player.sendMessage(ChatColor.GRAY + "/charm reroll");
        player.sendMessage(ChatColor.GRAY + "/charm menu");
        player.sendMessage(ChatColor.GRAY + "/charm recipe");
        player.sendMessage(ChatColor.GRAY + "/charm storage");
        player.sendMessage(ChatColor.GRAY + "/charm gateway <allow|deny|all> <page>");
        player.sendMessage(ChatColor.GRAY + "/trade <player|accept|deny>");
        player.sendMessage(ChatColor.GRAY + "/charm give <type> [level] (admin)");
        player.sendMessage(ChatColor.GRAY + "/charm swap <type> (admin)");
        player.sendMessage(ChatColor.GRAY + "/charm member (admin)");
        player.sendMessage(ChatColor.GRAY + "/charm rerollall (admin)");
        player.sendMessage(ChatColor.GRAY + "/charm giveupgradeitem (admin)");
        player.sendMessage(ChatColor.GRAY + "/charm giveswapitem (admin)");
        player.sendMessage(ChatColor.GRAY + "/charm reload (admin)");
    }

    private void showList(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Available charms:");
        for (String line : service.getCharmCatalog()) {
            player.sendMessage(ChatColor.GRAY + "- " + line);
        }
    }

    private void showRecipe(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Charm Recipes");
        player.sendMessage(ChatColor.GRAY + "Upgrade: 1 base charm + 1 Netherite + 3 Diamond Blocks + 1 Totem");
        player.sendMessage(ChatColor.GRAY + "Swap: 1 base charm + 3 Netherite + 1 Diamond Block + 2 Totems");
    }

    private void showStorageInfo(Player player) {
        CharmItem active = service.getActiveCharm(player);
        if (active == null || active.type() != CharmType.STORAGE) {
            player.sendMessage(ChatColor.RED + "You are not holding a Storage Charm.");
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Storage Charm info:");
        player.sendMessage(ChatColor.GRAY + "Level: " + active.level());
        player.sendMessage(ChatColor.GRAY + "Slots: " + service.getStorageSlotCount(player));
        player.sendMessage(ChatColor.GRAY + "Open it by right-clicking the charm.");
    }

    private void noPerm(Player player) {
        player.sendMessage(ChatColor.RED + "No permission.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("recipe")) {
            return List.of();
        }
        if (cmd.equals("trade")) {
            if (args.length == 1) {
                List<String> names = new ArrayList<>();
                names.add("accept");
                names.add("deny");
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (!p.equals(sender)) {
                        names.add(p.getName());
                    }
                }
                return filter(args[0], names);
            }
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], List.of("help", "list", "info", "reroll", "menu", "recipe", "storage", "gateway", "give", "swap", "member", "rerollall", "giveupgradeitem", "giveswapitem", "reload"));
        }
        if (args.length == 2 && Arrays.asList("give", "swap").contains(args[0].toLowerCase())) {
            List<String> names = Arrays.stream(CharmType.values()).map(Enum::name).toList();
            return filter(args[1], names);
        }
        return List.of();
    }

    private List<String> filter(String prefix, List<String> values) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(p)) {
                out.add(value);
            }
        }
        return out;
    }
}
