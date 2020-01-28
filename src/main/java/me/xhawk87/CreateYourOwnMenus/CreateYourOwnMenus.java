/*
 * Copyright (C) 2013-2019 XHawk87
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.xhawk87.CreateYourOwnMenus;

import me.xhawk87.CreateYourOwnMenus.commands.MenuCommand;
import me.xhawk87.CreateYourOwnMenus.script.SudoCommand;
import me.xhawk87.CreateYourOwnMenus.i18n.LanguageWrapper;
import me.xhawk87.CreateYourOwnMenus.listeners.MenuListener;
import me.xhawk87.CreateYourOwnMenus.script.CloseCommand;
import me.xhawk87.CreateYourOwnMenus.script.ConsumeCommand;
import me.xhawk87.CreateYourOwnMenus.script.DelayCommand;
import me.xhawk87.CreateYourOwnMenus.script.ReloadCommand;
import me.xhawk87.CreateYourOwnMenus.script.RequireCurrencyCommand;
import me.xhawk87.CreateYourOwnMenus.script.RequireLevelCommand;
import me.xhawk87.CreateYourOwnMenus.script.RequirePermissionCommand;
import me.xhawk87.CreateYourOwnMenus.script.ScriptCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A plugin to allow server owners to design and create their own menus
 * in-game
 *
 * @author XHawk87
 */
public class CreateYourOwnMenus extends JavaPlugin {

    // Store menus by their ID
    private Map<String, Menu> menus = new HashMap<>();
    private Set<String> commandBlacklist = new HashSet<>();
    private Set<String> commandWhitelist = new HashSet<>();
    private EconomyWrapper economy = null;
    // Names are in lower case
    private Map<String, ScriptCommand> scriptCommands = new HashMap<>();
    private LanguageWrapper language;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        new BukkitRunnable() {
            @Override
            public void run() {
                setupEconomy();
            }
        }.runTaskLater(this, 20);

        // Load menus
        reloadMenus();

        // Register commands
        Objects.requireNonNull(getCommand("menu")).setExecutor(new MenuCommand(this));

        // Register listeners
        new MenuListener().registerEvents(this);

        // Register script commands
        scriptCommands.put("close", new CloseCommand());
        scriptCommands.put("consume", new ConsumeCommand());
        scriptCommands.put("delay", new DelayCommand(this));
        scriptCommands.put("reload", new ReloadCommand());
        scriptCommands.put("requirecurrency", new RequireCurrencyCommand(this));
        scriptCommands.put("requirelevel", new RequireLevelCommand(this));
        scriptCommands.put("requirepermission", new RequirePermissionCommand());
        scriptCommands.put("sudo", new SudoCommand(this));

        // Register permissions
        PluginManager mgr = getServer().getPluginManager();
        for (int i = 0; i < 40; i++) {
            mgr.addPermission(new Permission("cyom.slot.lock." + i, "Treats the " + i + " slot of the player's inventory as locked menu", PermissionDefault.FALSE));
        }

        // Register LanguageAPI
        language = new LanguageWrapper(this, "eng");
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }
        economy = new EconomyWrapper(rsp.getProvider());
    }

    public EconomyWrapper getEconomy() {
        return economy;
    }

    /**
     * Create a new menu with the given id, display title and number of rows
     * for the inventory.
     *
     * @param id    A unique identifier for the menu
     * @param title The display title, may contain colour codes and spaces
     * @param rows  The number of rows for the inventory
     * @return The menu created
     */
    public Menu createMenu(String id, String title, int rows) {
        id = id.toLowerCase();
        if (menus.containsKey(id)) {
            throw new IllegalArgumentException("Menu IDs must be unique");
        }
        if (title.length() > 32) {
            throw new IllegalArgumentException("Titles are limited to 32 characters (including colours)");
        }
        Menu menu = new Menu(this, id, title, rows);
        menus.put(id, menu);

        // Register the specific-opening permission for the new menu
        String permissionNode = "cyom.menu." + id;
        PluginManager mgr = getServer().getPluginManager();
        if (mgr.getPermission(permissionNode) == null) {
            mgr.addPermission(
                    new Permission(permissionNode,
                            "Allows the given player to use the /menu open command for the "
                                    + id + " menu", PermissionDefault.FALSE));
        }
        return menu;
    }

    /**
     * Gets a menu by its id
     *
     * @param id The id of the menu
     * @return The menu, or null if no menu by this id exists
     */
    public Menu getMenu(String id) {
        return menus.get(id.toLowerCase());
    }

    /**
     * Deletes a menu from the list of menus. Menu.delete() should be used to
     * properly delete a menu. This method should only be called from inside
     * Menu.delete()
     *
     * @param menu The menu to delete
     */
    void deleteMenu(Menu menu) {
        menus.remove(menu.getId());
        // De-register the specific-opening permission for the deleted menu
        getServer().getPluginManager().removePermission("cyom.menus." + menu.getId());
    }

    /**
     * Load or reload all menus from their .yml files
     */
    public void reloadMenus() {
        // Load in blacklisted and whitelisted commands from config
        commandBlacklist.clear();
        commandWhitelist.clear();
        for (String commandName : getConfig().getStringList("blacklist-commands")) {
            if (commandName.startsWith("/")) {
                commandName = commandName.substring(1);
            }
            commandBlacklist.add(commandName.toLowerCase());
        }
        for (String commandName : getConfig().getStringList("whitelist-commands")) {
            if (commandName.startsWith("/")) {
                commandName = commandName.substring(1);
            }
            commandWhitelist.add(commandName.toLowerCase());
        }

        // De-register the specific-opening permission for all menus
        PluginManager mgr = getServer().getPluginManager();
        for (Menu menu : menus.values()) {
            mgr.removePermission("cyom.menus." + menu.getId());
        }

        // Delete all menus
        menus.clear();

        // Ensure that the plugin folder has been created
        File menusFolder = new File(getDataFolder(), "menus");
        if (!menusFolder.exists()) {
            if (!menusFolder.mkdirs()) {
                throw new RuntimeException("Failed to create directory " + menusFolder.getPath());
            }
        }

        // Load all .yml files in the plugin folder as menus
        // This may need to change if a config.yml is added
        for (File file : Objects.requireNonNull(menusFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(".yml");
            }
        }))) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length()).toLowerCase();
            Menu menu = new Menu(this, id);
            menu.load();
            menus.put(id, menu);

            // Register the specific-opening permission for the loaded menu
            if (mgr.getPermission("cyom.menu." + id) == null) {
                mgr.addPermission(
                        new Permission("cyom.menu." + id,
                                "Allows the given player to use the /menu open command for the "
                                        + id + " menu", PermissionDefault.FALSE));
            }
        }
    }

    public boolean reloadMenu(String menuId) {
        // Check if menu and/or menu file exist
        File menusFolder = new File(getDataFolder(), "menus");
        File file = new File(menusFolder, menuId + ".yml");
        if (file.exists()) {
            Menu menu = getMenu(menuId);
            if (menu == null) {
                // Create this menu from file
                menu = new Menu(this, menuId);
                menu.load();
                menus.put(menuId, menu);

                // Register the specific-opening permission for the loaded menu
                PluginManager mgr = getServer().getPluginManager();
                if (mgr.getPermission("cyom.menu." + menuId) == null) {
                    mgr.addPermission(
                            new Permission("cyom.menu." + menuId,
                                    "Allows the given player to use the /menu open command for the "
                                            + menuId + " menu", PermissionDefault.FALSE));
                }

                return true;
            } else {
                // Update this menu from file
                menu.reload();
                return true;
            }
        } else {
            // We should delete this menu if it exists
            Menu menu = getMenu(menuId);
            if (menu == null) {
                return false;
            }
            // De-register the specific-opening permission for this menu
            getServer().getPluginManager().removePermission("cyom.menus." + menu.getId());

            // Delete this menu
            menus.remove(menu.getId());
            return true;
        }
    }

    /**
     * Show a formatted list of all menus to the command sender
     *
     * @param sender The command sender
     */
    public void displayMenuList(CommandSender sender) {
        if (menus.isEmpty()) {
            sender.sendMessage(language.get(sender, "no-menus", "There are currently no menus. Use /menu create to create one"));
            return;
        }
        sender.sendMessage(language.get(sender, "menu-count", "There are {0} menus:", menus.size()));
        for (Map.Entry<String, Menu> entry : menus.entrySet()) {
            String id = entry.getKey();
            Menu menu = entry.getValue();
            sender.sendMessage(language.get(sender, "menu-list-item", "    {0}: {1}", id, menu.getTitle()));
        }
    }

    /**
     * Checks the command blacklist and whitelist to see if this command can
     * be used in a menu script.
     *
     * @param commandName The command name
     * @return True, if the command is on the whitelist or there is no
     * whitelist and it is not on the blacklist. False, if it is on the
     * blacklist or if there is a whitelist and this command is not on it
     */
    public boolean isValidMenuScriptCommand(String commandName) {
        if (commandName.startsWith("/")) {
            commandName = commandName.substring(1);
        }
        if (commandWhitelist.isEmpty()) {
            return !commandBlacklist.contains(commandName.toLowerCase());
        } else {
            return commandWhitelist.contains(commandName.toLowerCase());
        }
    }

    /**
     * Gets a script-only command by its name
     *
     * @param commandString The command name
     * @return The script command
     */
    ScriptCommand getScriptCommand(String commandString) {
        if (commandString.startsWith("/")) {
            commandString = commandString.substring(1);
        }
        return scriptCommands.get(commandString.toLowerCase());
    }

    /**
     * Translate this message into the specified language of the sender using
     * the language files for this plugin.
     *
     * @param forWhom  The player/console to translate for
     * @param key      The language key
     * @param template The message template
     * @param params   The dynamic parameters
     * @return The translated message
     */
    public String translate(CommandSender forWhom, String key, String template, Object... params) {
        return language.get(forWhom, key, template, params);
    }
}
