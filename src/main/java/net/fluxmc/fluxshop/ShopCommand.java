package net.fluxmc.fluxshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShopCommand implements TabExecutor {

    private final FluxShop plugin;

    public ShopCommand(FluxShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("fluxshop.reload")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(plugin.msg("reloaded", "count", String.valueOf(plugin.shops().categories().size())));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("fluxshop.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length > 0) {
            ShopManager.Category c = plugin.shops().get(args[0]);
            if (c == null) {
                player.sendMessage(plugin.msg("unknown-category"));
            } else {
                plugin.gui().openCategory(player, c, 0);
            }
        } else {
            plugin.gui().openMain(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            for (String id : plugin.shops().ids()) {
                if (id.startsWith(start)) out.add(id);
            }
            if (sender.hasPermission("fluxshop.reload") && "reload".startsWith(start)) out.add("reload");
        }
        return out;
    }
}
