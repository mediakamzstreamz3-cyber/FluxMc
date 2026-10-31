package net.fluxmc.fluxshop;

import net.fluxmc.fluxshop.ShopManager.Category;
import net.fluxmc.fluxshop.ShopManager.ShopItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import static net.fluxmc.fluxshop.FluxShop.color;

public class ShopGUI implements Listener {

    private static final int PAGE_SIZE = 45; // 5 rows of items, bottom row is navigation
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    private final FluxShop plugin;

    public ShopGUI(FluxShop plugin) {
        this.plugin = plugin;
    }

    /** Marks our inventories so we can recognise them in click events. */
    static class Holder implements InventoryHolder {
        final Category category; // null = main menu
        final int page;
        final Map<Integer, Category> menuSlots = new HashMap<>();
        Inventory inventory;

        Holder(Category category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------------------------------------------------------- opening

    public void openMain(Player p) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("main-menu.rows", 3)));
        Holder h = new Holder(null, 0);
        Inventory inv = Bukkit.createInventory(h, rows * 9,
                color(plugin.getConfig().getString("main-menu.title", "&8Shop")));
        h.inventory = inv;

        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler());

        List<Category> auto = new ArrayList<>();
        for (Category c : plugin.shops().categories()) {
            int s = c.slot();
            if (s >= 0 && s < inv.getSize() && !h.menuSlots.containsKey(s)) {
                h.menuSlots.put(s, c);
            } else {
                auto.add(c);
            }
        }
        int next = 0;
        for (Category c : auto) {
            while (next < inv.getSize() && h.menuSlots.containsKey(next)) next++;
            if (next >= inv.getSize()) break;
            h.menuSlots.put(next, c);
            next++;
        }

        for (Map.Entry<Integer, Category> e : h.menuSlots.entrySet()) {
            Category c = e.getValue();
            inv.setItem(e.getKey(), named(c.icon(), c.title(),
                    List.of(color("&7" + c.items().size() + " items"), color("&eClick to open"))));
        }
        p.openInventory(inv);
    }

    public void openCategory(Player p, Category c, int page) {
        int maxPage = Math.max(0, (c.items().size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));

        Holder h = new Holder(c, page);
        Inventory inv = Bukkit.createInventory(h, 54, c.title() + color(" &8(" + (page + 1) + "/" + (maxPage + 1) + ")"));
        h.inventory = inv;

        for (int i = 45; i < 54; i++) inv.setItem(i, filler());

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < c.items().size(); i++) {
            inv.setItem(i, display(c.items().get(start + i)));
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, named(Material.ARROW, color("&e« Previous page"),
                    List.of(color("&7Go to page " + page))));
        }
        inv.setItem(SLOT_BACK, named(Material.BARRIER, color("&cBack"), List.of(color("&7Click to return"))));
        if (page < maxPage) {
            inv.setItem(SLOT_NEXT, named(Material.ARROW, color("&eNext page »"),
                    List.of(color("&7Go to page " + (page + 2)))));
        }
        p.openInventory(inv);
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();

        // Main menu
        if (h.category == null) {
            Category c = h.menuSlots.get(slot);
            if (c != null) openCategory(p, c, 0);
            return;
        }

        // Category page
        Category c = h.category;
        int maxPage = Math.max(0, (c.items().size() - 1) / PAGE_SIZE);

        if (slot == SLOT_PREV && h.page > 0) {
            openCategory(p, c, h.page - 1);
            return;
        }
        if (slot == SLOT_NEXT && h.page < maxPage) {
            openCategory(p, c, h.page + 1);
            return;
        }
        if (slot == SLOT_BACK) {
            openMain(p);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) return;

        int index = h.page * PAGE_SIZE + slot;
        if (index >= c.items().size()) return;
        ShopItem item = c.items().get(index);

        ClickType click = e.getClick();
        switch (click) {
            case LEFT -> buy(p, item, 1);
            case SHIFT_LEFT -> buy(p, item, 64);
            case RIGHT -> sell(p, item, false);
            case SHIFT_RIGHT -> sell(p, item, true);
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- buy / sell

    private void buy(Player p, ShopItem item, int amount) {
        if (item.buy() < 0) {
            p.sendMessage(plugin.msg("cannot-buy"));
            return;
        }
        Economy eco = plugin.economy();
        double cost = item.buy() * amount;
        if (!eco.has(p, cost)) {
            p.sendMessage(plugin.msg("not-enough-money", "price", money(cost)));
            return;
        }
        if (!eco.withdrawPlayer(p, cost).transactionSuccess()) {
            p.sendMessage(plugin.msg("not-enough-money", "price", money(cost)));
            return;
        }

        Map<Integer, ItemStack> leftover = p.getInventory().addItem(new ItemStack(item.material(), amount));
        int notGiven = 0;
        for (ItemStack s : leftover.values()) notGiven += s.getAmount();

        int given = amount - notGiven;
        if (notGiven > 0) eco.depositPlayer(p, item.buy() * notGiven); // refund what didn't fit

        if (given <= 0) {
            p.sendMessage(plugin.msg("inventory-full"));
            return;
        }
        p.sendMessage(plugin.msg("bought", "amount", String.valueOf(given),
                "item", name(item.material()), "price", money(item.buy() * given)));
        if (notGiven > 0) p.sendMessage(plugin.msg("inventory-full"));
    }

    private void sell(Player p, ShopItem item, boolean all) {
        if (item.sell() < 0) {
            p.sendMessage(plugin.msg("cannot-sell"));
            return;
        }

        // Only count plain items (no custom name/lore/enchants) so custom items aren't sold by accident.
        int have = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.getType() == item.material() && !s.hasItemMeta()) have += s.getAmount();
        }
        if (have <= 0) {
            p.sendMessage(plugin.msg("no-items", "item", name(item.material())));
            return;
        }

        int amount = all ? have : 1;
        p.getInventory().removeItem(new ItemStack(item.material(), amount));
        double gain = item.sell() * amount;
        plugin.economy().depositPlayer(p, gain);
        p.sendMessage(plugin.msg("sold", "amount", String.valueOf(amount),
                "item", name(item.material()), "price", money(gain)));
    }

    // ---------------------------------------------------------------- helpers

    private ItemStack display(ShopItem item) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (item.buy() >= 0) lore.add(color("&7Buy: &a$" + money(item.buy()) + " &8(left click, shift = 64)"));
        else lore.add(color("&7Buy: &cnot available"));
        if (item.sell() >= 0) lore.add(color("&7Sell: &6$" + money(item.sell()) + " &8(right click, shift = all)"));
        else lore.add(color("&7Sell: &cnot available"));

        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack filler() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("main-menu.filler", "GRAY_STAINED_GLASS_PANE"));
        if (m == null) m = Material.GRAY_STAINED_GLASS_PANE;
        return named(m, " ", Collections.emptyList());
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack stack = new ItemStack(m);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String money(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private static String name(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
