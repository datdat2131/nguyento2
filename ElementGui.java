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

/** Menu trang bị / tắt nguyên tố (kèm mô tả buff, nội tại, chiêu). */
public class ElementGui implements Listener {

    private static final String TITLE = "§8✦ §5Nguyên Tố §8✦";
    private static final int SIZE = 45;
    private static final int[] EQUIP_SLOTS = {2, 4, 6};
    private static final int INFO_SLOT = 13;
    private static final int CLEAR_SLOT = 40;
    private static final String[] SLOT_NAMES = {"Trên đầu", "Vai trái", "Vai phải"};

    private final ElementManager manager;
    private final Settings settings;

    public ElementGui(ElementManager manager, Settings settings) {
        this.manager = manager;
        this.settings = settings;
    }

    private static class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE);
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

        // các nguyên tố (vị trí lấy từ Element.getGuiSlot)
        for (Element e : Element.values()) {
            inv.setItem(e.getGuiSlot(), elementIcon(e, eq, owned));
        }

        inv.setItem(INFO_SLOT, icon(Material.BOOK, "§eHướng dẫn", Arrays.asList(
                "",
                "§7Trang bị tối đa §f" + ElementManager.MAX_EQUIPPED + " §7nguyên tố.",
                "§7• Ô 1: bay trên đầu",
                "§7• Ô 2, 3: lơ lửng hai bên vai",
                "§7• Click nguyên tố để bật / tắt",
                "§7• Nhận nguyên tố bằng cuộn giấy",
                "",
                "§cChiêu thức §7chỉ dùng được khi nguyên tố",
                "§7nằm ở §fÔ 1§7. Bạn sẽ nhận một vật phẩm kỹ năng,",
                "§7§fchuột phải §7vật phẩm đó để dùng chiêu.",
                "§8(Vật phẩm bất ly thân, không vứt/cất được)"), false));

        inv.setItem(CLEAR_SLOT, icon(Material.BARRIER, "§cTắt tất cả",
                List.of("", "§7Gỡ toàn bộ nguyên tố đang trang bị"), false));
    }

    private ItemStack elementIcon(Element e, List<Element> eq, List<Element> owned) {
        boolean has = owned.contains(e);
        boolean on = eq.contains(e);

        List<String> lore = new ArrayList<>();
        lore.add(e.rarityLine());
        lore.add("");
        lore.addAll(settings.describe(e));
        lore.add("");
        if (!has) {
            lore.add("§cChưa sở hữu §7- dùng cuộn giấy để lĩnh ngộ");
        } else if (on) {
            int slot = eq.indexOf(e);
            lore.add("§aĐang trang bị §7(Ô " + (slot + 1) + " • " + SLOT_NAMES[slot] + ")");
            lore.add("§7Click để §ctắt");
        } else {
            lore.add("§7Đã sở hữu • Click để §atrang bị");
        }

        if (!has) {
            return icon(Material.GRAY_DYE, "§8✖ §7Nguyên Tố " + e.getName(), lore, false);
        }
        String name = e.getColor() + "§l" + e.getName();
        return icon(e.getMaterial(), name, lore, on || e.isSpecial());
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
        for (Element el : Element.values()) {
            if (slot == el.getGuiSlot()) {
                handleElement(p, el);
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
            if (slot == 0 && el.isSpecial()) {
                p.sendMessage("§7Ở Ô 1: bạn nhận vật phẩm kỹ năng, §fchuột phải §7để dùng chiêu.");
            }
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
