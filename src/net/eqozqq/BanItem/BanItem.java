package net.eqozqq.BanItem;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerItemHeldEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class BanItem extends PluginBase implements Listener {

    private Config config;
    private Set<Integer> bannedItems;
    private boolean autoRemove;
    private Map<String, List<Item>> playerBannedItems = new HashMap<>();
    private boolean loggingEnabled;
    private File logFile;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();

        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);

            config = new Config(configFile, Config.YAML);
            config.set("loggingEnabled", false);
            config.save();
        } else {
            config = new Config(configFile, Config.YAML);
        }

        bannedItems = new HashSet<>(config.getList("bannedItems", new ArrayList<>()));
        loggingEnabled = config.getBoolean("loggingEnabled", true);

        if (!config.exists("autoRemove")) {
            config.set("autoRemove", false);
            config.save();
        }
        autoRemove = config.getBoolean("autoRemove", false);

        if (loggingEnabled) {
            logFile = new File(getDataFolder(), "logging.log");
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    getLogger().error("Failed to create logging.log file: " + e.getMessage());
                }
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    private void log(String message) {
        if (loggingEnabled) {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            String timestamp = sdf.format(new Date());

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println("[" + timestamp + "] " + message);
            } catch (IOException e) {
                getLogger().error("Failed to write to logging.log file: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("item")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                sender.sendMessage("Blocked items: " + bannedItems);
                return true;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("ban")) {
                try {
                    int itemId = Integer.parseInt(args[1]);
                    if (!bannedItems.contains(itemId)) {
                        bannedItems.add(itemId);
                        config.set("bannedItems", new ArrayList<>(bannedItems));
                        config.save();
                        sender.sendMessage("Item " + Item.get(itemId).getName() + " (ID: " + itemId + ") blocked.");
                    } else {
                    	sender.sendMessage("Item " + Item.get(itemId).getName() + " (ID: " + itemId + ") already blocked.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid item ID format.");
                }
                return true;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("unban")) {
                try {
                    int itemId = Integer.parseInt(args[1]);
                    if (bannedItems.contains(itemId)) {
                        bannedItems.remove(itemId);
                        config.set("bannedItems", new ArrayList<>(bannedItems));
                        config.save();
                        sender.sendMessage("Item " + Item.get(itemId).getName() + " (ID: " + itemId + ") unblocked.");
                    } else {
                    	sender.sendMessage("Item " + Item.get(itemId).getName() + " (ID: " + itemId + ") was not blocked.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid item ID format.");
                }
                return true;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("autoremove")) {
                autoRemove = Boolean.parseBoolean(args[1]);
                config.set("autoRemove", autoRemove);
                config.save();
                sender.sendMessage("The 'Remove item from inventory' option is set to: " + autoRemove);
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        if (item != null && bannedItems.contains(item.getId())) {
            player.sendMessage(TextFormat.RED + "You cannot use this item because it is blocked.");
            event.setCancelled();
            log("Player " + player.getName() + " trying to use a blocked item " + Item.get(item.getId()).getName() + " (ID: " + item.getId() + ").");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (autoRemove) {
            List<Item> bannedItemsToRemove = new ArrayList<>();
            for (Item item : player.getInventory().getContents().values()) {
                if (item != null && bannedItems.contains(item.getId())) {
                    bannedItemsToRemove.add(item);
                }
            }
            for (Item item : bannedItemsToRemove) {
                player.getInventory().remove(item);
                player.sendMessage(TextFormat.RED + "Blocked item removed " + Item.get(item.getId()).getName() + " (ID: " + item.getId() + ") from your inventory.");
                log("Player " + player.getName() + " has a blocked item " + Item.get(item.getId()).getName() + " (ID: " + item.getId() + ").");
            }
            playerBannedItems.put(player.getName(), bannedItemsToRemove);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (autoRemove) {
            playerBannedItems.remove(player.getName());
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        if (item != null && bannedItems.contains(item.getId())) {
            player.sendMessage(TextFormat.RED + "You cannot throw away this item because it is blocked.");
            event.setCancelled();
            log("Player " + player.getName() + " tries to throw away a blocked item " + Item.get(item.getId()).getName() + " (ID: " + item.getId() + ").");
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Item item = player.getInventory().getItem(event.getInventorySlot());

        if (autoRemove && item != null && bannedItems.contains(item.getId())) {
            player.getInventory().setItem(event.getInventorySlot(), Item.get(0));
            player.sendMessage(TextFormat.RED + "Blocked item removed " + Item.get(item.getId()).getName() + " (ID: " + item.getId() + ") from your inventory.");
            log("Player " + player.getName() + " trying to use a blocked item " + Item.get(item.getId()).getName() + " (ID: " + item.getId() + ").");
        }
    }
}