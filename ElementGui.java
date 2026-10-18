package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Menu trang bị / tắt nguyên tố. */
public class ElementGui implements Listener {

    private static final String TITLE = "§8✦ §5Nguyên Tố §8✦";
    private static final int[] EQUIP_SLOTS = {2, 4, 6};
    private static final int[] ELEMENT_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int INFO_SLOT = 13;
    private static final int CLEAR_SLOT = 22;
    private static final String[] SLOT_NAMES = {"Trên đầu", "Vai trái", "Vai phải"};

    private final ElementManager manager;

    public ElementGui(ElementManager manager) {
        this.manager = manager;
    }

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, TITLE);
        holder.inv = inv;
        render(inv, p);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.2f);
    }

    // ------------------------------------------------------------------
    //  Vẽ giao diện
    // ------------------------------------------------------------------

    private void render(Inventory inv, Player p) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        List<Element> eq = manager.getEquipped(p);
        List<Element> owned = manager.getOwned(p);

        // 3 ô trang bị
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (i < eq.size()) {
                Element e = eq.get(i);
                inv.setItem(EQUIP_SLOTS[i], icon(e.getMaterial(),
                        "§eÔ " + (i + 1) + " • " + SLOT_NAMES[i] + "§7: " + e.colored(),
                        List.of("", "§7Click để §ctắt §7nguyên tố này"), true));
            } else {
                inv.setItem(EQUIP_SLOTS[i], icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "§7Ô " + (i + 1) + " • " + SLOT_NAMES[i] + " §8(trống)",
                        List.of("", "§7Chọn một nguyên tố bên dưới để trang bị"), false));
            }
        }

        // danh sách nguyên tố
        Element[] all = Element.values();
        for (int i = 0; i < ELEMENT_SLOTS.length && i < all.length; i++) {
            Element e = all[i];
            if (!owned.contains(e)) {
                inv.setItem(ELEMENT_SLOTS[i], icon(Material.GRAY_DYE,
                        "§8??? §7Nguyên Tố " + e.getName(),
                        List.of("", "§cChưa sở hữu", "§7Dùng cuộn giấy để lĩnh ngộ"), false));
            } else if (eq.contains(e)) {
                int slot = eq.indexOf(e);
                inv.setItem(ELEMENT_SLOTS[i], icon(e.getMaterial(),
                        e.getColor() + "§l" + e.getName(),
                        List.of("", "§aĐang trang bị §7(Ô " + (slot + 1) + " • " + SLOT_NAMES[slot] + ")",
                                "§7Click để §ctắt"), true));
            } else {
                inv.setItem(ELEMENT_SLOTS[i], icon(e.getMaterial(),
                        e.getColor() + "§l" + e.getName(),
                        List.of("", "§7Đã sở hữu", "§7Click để §atrang bị"), false));
            }
        }

        inv.setItem(INFO_SLOT, icon(Material.BOOK, "§eHướng dẫn", Arrays.asList(
                "",
                "§7Trang bị tối đa §f" + ElementManager.MAX_EQUIPPED + " §7nguyên tố.",
                "§7• Ô 1: bay trên đầu",
                "§7• Ô 2, 3: lơ lửng hai bên vai",
                "§7• Click nguyên tố để bật / tắt",
                "§7• Nhận nguyên tố bằng cuộn giấy"), false));

        inv.setItem(CLEAR_SLOT, icon(Material.BARRIER, "§cTắt tất cả",
                List.of("", "§7Gỡ toàn bộ nguyên tố đang trang bị"), false));
    }

    private ItemStack icon(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) meta.setLore(new ArrayList<>(lore));
        if (glow) ElementManager.addGlow(meta);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------
    //  Xử lý click
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() != top) return;

        int slot = e.getRawSlot();
        List<Element> eq = manager.getEquipped(p);
        boolean changed = false;

        // click vào ô trang bị -> tắt
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (slot == EQUIP_SLOTS[i] && i < eq.size()) {
                Element el = eq.get(i);
                manager.unequip(p, el);
                p.sendMessage("§7Đã tắt nguyên tố " + el.colored() + "§7.");
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);
                changed = true;
            }
        }

        // click vào nguyên tố
        Element[] all = Element.values();
        for (int i = 0; i < ELEMENT_SLOTS.length && i < all.length; i++) {
            if (slot == ELEMENT_SLOTS[i]) {
                handleElement(p, all[i]);
                changed = true;
            }
        }

        if (slot == CLEAR_SLOT) {
            if (!eq.isEmpty()) {
                manager.unequipAll(p);
                p.sendMessage("§7Đã tắt toàn bộ nguyên tố.");
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
            }
            changed = true;
        }

        if (changed) render(top, p);
    }

    private void handleElement(Player p, Element el) {
        if (!manager.owns(p, el)) {
            p.sendMessage("§cBạn chưa sở hữu nguyên tố " + el.colored() + "§c. Hãy dùng cuộn giấy để lĩnh ngộ!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        if (manager.getEquipped(p).contains(el)) {
            manager.unequip(p, el);
            p.sendMessage("§7Đã tắt nguyên tố " + el.colored() + "§7.");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);
            return;
        }
        ElementManager.EquipResult r = manager.equip(p, el);
        if (r == ElementManager.EquipResult.FULL) {
            p.sendMessage("§cBạn đã trang bị tối đa " + ElementManager.MAX_EQUIPPED
                    + " nguyên tố! Hãy tắt bớt một nguyên tố trước.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        } else if (r == ElementManager.EquipResult.OK) {
            int slot = manager.getEquipped(p).indexOf(el);
            p.sendMessage("§aĐã trang bị nguyên tố " + el.colored() + " §7(" + SLOT_NAMES[slot] + ")§a.");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
