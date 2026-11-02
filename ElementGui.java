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
    private static final int SIZE = 54;
    private static final int[] EQUIP_SLOTS = {2, 4, 6};
    private static final int INFO_SLOT = 13;
    private static final int CLEAR_SLOT = 40;
    private static final int FUSION_LABEL_SLOT = 37;
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
                "§7Mọi buff & nội tại hoạt động ở §fmọi ô§7.",
                "§dHợp thể §7(hàng dưới): kết hợp 2 nguyên tố,",
                "§7chỉ chiếm §f1 ô §7nhưng nhận sức mạnh cả hai.",
                "",
                "§cChiêu thức §7(Bóng Tối, Ánh Sáng): trang bị",
                "§7ở ô nào cũng được, bạn sẽ nhận vật phẩm kỹ năng,",
                "§7§fchuột phải §7vật phẩm đó để dùng chiêu.",
                "§8(Vật phẩm bất ly thân, không vứt/cất được)"), false));

        inv.setItem(FUSION_LABEL_SLOT, fusionLabel(owned));

        inv.setItem(CLEAR_SLOT, icon(Material.BARRIER, "§cTắt tất cả",
                List.of("", "§7Gỡ toàn bộ nguyên tố đang trang bị"), false));
    }

    /** Biển chỉ dẫn hàng hợp thể: liệt kê công thức. */
    private ItemStack fusionLabel(List<Element> owned) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Sở hữu đủ 2 nguyên tố gốc rồi §fclick");
        lore.add("§7vào hợp thể bên phải để kết hợp.");
        lore.add("§7Nguyên tố gốc §fkhông bị mất§7.");
        int cost = settings.fusionCostLevels();
        if (cost > 0) lore.add("§7Phí kết hợp: §e" + cost + " cấp độ");
        lore.add("");
        lore.add("§dCông thức:");
        for (Element f : Element.values()) {
            if (!f.isFusion()) continue;
            List<Element> parts = f.getParts();
            if (parts.size() != 2) continue;
            lore.add("§7 • " + parts.get(0).colored() + " §7+ " + parts.get(1).colored() + " §7= " + f.colored());
        }
        return icon(Material.ANVIL, "§d§l✦ Hợp Thể ✦", lore, false);
    }

    private ItemStack elementIcon(Element e, List<Element> eq, List<Element> owned) {
        boolean has = owned.contains(e);
        boolean on = eq.contains(e);

        List<String> lore = new ArrayList<>();
        lore.add(e.rarityLine());
        lore.add("");
        lore.addAll(settings.describe(e));
        lore.add("");
        if (!has && e.isFusion()) {
            List<Element> parts = e.getParts();
            boolean can = true;
            StringBuilder need = new StringBuilder();
            for (Element part : parts) {
                boolean ok = owned.contains(part);
                can &= ok;
                if (need.length() > 0) need.append(" §7+ ");
                need.append(ok ? "§a✔ " : "§c✖ ").append(part.colored());
            }
            lore.add("§7Cần sở hữu: " + need);
            if (can) {
                int cost = settings.fusionCostLevels();
                lore.add("§aĐủ điều kiện §7- Click để §dkết hợp" + (cost > 0 ? " §7(tốn §e" + cost + " §7cấp)" : ""));
                return icon(e.getMaterial(), "§d✦ §7Hợp thể " + e.colored() + " §d✦", lore, true);
            }
            lore.add("§cChưa đủ nguyên tố gốc để kết hợp");
            return icon(Material.GRAY_DYE, "§8✖ §7Hợp thể " + e.getName(), lore, false);
        }
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

        if (changed) {
            render(top, p);
            p.updateInventory(); // ép client vẽ lại ngay, không cần thoát ra vào lại
        }
    }

    private void handleFuse(Player p, Element el) {
        ElementManager.FuseResult r = manager.fuse(p, el);
        switch (r) {
            case OK:
                p.sendMessage("§d✦ Kết hợp thành công! Bạn sở hữu hợp thể " + el.colored()
                        + "§d. §7Click lần nữa để trang bị.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 0.8f);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
                p.getWorld().spawnParticle(el.getParticle(), p.getLocation().add(0, 1, 0), 60, 0.5, 0.9, 0.5, 0.1);
                break;
            case MISSING:
                p.sendMessage("§cBạn cần sở hữu cả hai nguyên tố gốc của " + el.colored() + "§c.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                break;
            case NO_LEVELS:
                p.sendMessage("§cCần §e" + settings.fusionCostLevels() + " §ccấp độ để kết hợp " + el.colored() + "§c.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                break;
            default:
                break;
        }
    }

    private void handleElement(Player p, Element el) {
        if (!manager.owns(p, el) && el.isFusion()) {
            handleFuse(p, el);
            return;
        }
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
        } else if (r == ElementManager.EquipResult.CONFLICT) {
            Element other = manager.conflictWith(p, el);
            p.sendMessage("§cKhông thể trang bị " + el.colored() + "§c cùng " + (other == null ? "nguyên tố trùng" : other.colored())
                    + "§c vì chúng chứa cùng một nguyên tố. Hãy tắt một trong hai.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        } else if (r == ElementManager.EquipResult.OK) {
            int slot = manager.getEquipped(p).indexOf(el);
            p.sendMessage("§aĐã trang bị nguyên tố " + el.colored() + " §7(" + SLOT_NAMES[slot] + ")§a.");
            boolean skill = el.hasSkill();
            for (Element part : el.getParts()) skill |= part.hasSkill();
            if (skill) {
                p.sendMessage("§7Bạn nhận vật phẩm kỹ năng, §fchuột phải §7để dùng chiêu.");
            }
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
