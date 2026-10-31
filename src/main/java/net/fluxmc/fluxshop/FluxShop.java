package net.fluxmc.fluxshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class FluxShop extends JavaPlugin {

    private Economy economy;
    private ShopManager shops;
    private ShopGUI gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("shops.yml", false);

        if (!setupEconomy()) {
            getLogger().severe("Vault or an economy plugin (e.g. EssentialsX) was not found. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        shops = new ShopManager(this);
        shops.load();
        gui = new ShopGUI(this);
        getServer().getPluginManager().registerEvents(gui, this);

        ShopCommand cmd = new ShopCommand(this);
        PluginCommand pc = getCommand("shop");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getLogger().info("FluxShop enabled with " + shops.categories().size() + " categories.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public void reloadAll() {
        reloadConfig();
        shops.load();
    }

    public Economy economy() { return economy; }
    public ShopManager shops() { return shops; }
    public ShopGUI gui() { return gui; }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    /** Get a message from config.yml, replacing {key} placeholders. Pairs: key, value, key, value... */
    public String msg(String key, String... pairs) {
        String m = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m = m.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return color(getConfig().getString("messages.prefix", "") + m);
    }
}
