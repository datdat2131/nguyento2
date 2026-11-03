package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu riêng cho HỢP THỂ: xem công thức, kết hợp 2 nguyên tố thành hợp thể (tiêu hao 2 nguyên tố gốc, có bước xác nhận)
 * và trang bị / tắt hợp thể đã sở hữu.
 */
public class FusionGui implements Listener {

    private static final String TITLE = "§8✦ §dHợp Thể Nguyên Tố §8✦";
    private static final int SIZE = 54;
    private static final int[] EQUIP_SLOTS = {2, 4, 6};
    /** Ô của từng hợp thể, theo thứ tự khai báo trong Element. */
    private static final int[] FUSION_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 23, 24};
    private static final int INFO_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final long CONFIRM_MS = 10_000L;

    private static class Pending {
        final Element element;
        final long until;

        Pending(Element element, long until) {
            this.element = element;
            this.until = until;
        }
    }

    private static class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final ElementManager manager;
    private final Settings settings;
    private final ElementGui elementGui;
    private final List<Element> fusions = new ArrayList<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    public FusionGui(ElementManager manager, Settings settings, ElementGui elementGui) {
        this.manager = manager;
        this.settings = settings;
        this.elementGui = elementGui;
        for (Element e : Element.values()) {
            if (e.isFusion()) fusions.add(e);
        }
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.inv = inv;
        pending.remove(p.getUniqueId());
        render(inv, p);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.7f, 1.2f);
    }

    // ------------------------------------------------------------------
    //  Vẽ giao diện
    // ------------------------------------------------------------------

    private Pending activePending(Player p) {
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null) return null;
        if (pd.until <= System.currentTimeMillis()) {
            pending.remove(p.getUniqueId());
            return null;
        }
        return pd;
    }

    private void render(Inventory inv, Player p) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        List<Element> eq = manager.getEquipped(p);
        List<Element> owned = manager.getOwned(p);
        Pending pd = activePending(p);

        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (i < eq.size()) {
                Element e = eq.get(i);
                inv.setItem(EQUIP_SLOTS[i], icon(e.getMaterial(),
                        "§eÔ " + (i + 1) + " • " + ElementGui.SLOT_NAMES[i] + "§7: " + e.colored(),
                        List.of("", "§7Click để §ctắt §7nguyên tố này"), true));
            } else {
                inv.setItem(EQUIP_SLOTS[i], icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "§7Ô " + (i + 1) + " • " + ElementGui.SLOT_NAMES[i] + " §8(trống)", List.of(), false));
            }
        }

        for (int i = 0; i < fusions.size() && i < FUSION_SLOTS.length; i++) {
            inv.setItem(FUSION_SLOTS[i], fusionIcon(fusions.get(i), eq, owned, pd));
        }

        List<String> info = new ArrayList<>();
        info.add("");
        info.add("§7Sở hữu đủ §f2 nguyên tố gốc §7rồi click vào hợp thể");
        info.add("§7để kết hợp. Cần click §fhai lần §7để xác nhận.");
        info.add("");
        info.add("§c⚠ Kết hợp sẽ làm bạn MẤT cả hai nguyên tố gốc!");
        info.add("§7Muốn có lại phải lĩnh ngộ bằng cuộn giấy.");
        info.add("§7Hợp thể là nguyên tố §fđộc lập§7, chỉ chiếm §f1 ô§7.");
        int cost = settings.fusionCostLevels();
        if (cost > 0) info.add("§7Phí kết hợp: §e" + cost + " §7cấp độ");
        info.add("");
        info.add("§dCông thức:");
        for (Element f : fusions) {
            List<Element> parts = f.getParts();
            if (parts.size() != 2) continue;
            info.add("§7 • " + parts.get(0).colored() + " §7+ " + parts.get(1).colored() + " §7= " + f.colored());
        }
        inv.setItem(INFO_SLOT, icon(Material.BOOK, "§e§lHướng dẫn Hợp Thể", info, false));

        inv.setItem(BACK_SLOT, icon(Material.ARROW, "§7← Quay lại menu Nguyên Tố", List.of(), false));
    }

    private ItemStack fusionIcon(Element f, List<Element> eq, List<Element> owned, Pending pd) {
        List<String> lore = new ArrayList<>();
        lore.add(f.rarityLine());
        lore.add("");
        lore.addAll(settings.describe(f));
        lore.add("");

        if (owned.contains(f)) {
            boolean on = eq.contains(f);
            if (on) {
                int slot = eq.indexOf(f);
                lore.add("§aĐang trang bị §7(Ô " + (slot + 1) + " • " + ElementGui.SLOT_NAMES[slot] + ")");
                lore.add("§7Click để §ctắt");
            } else {
                lore.add("§7Đã sở hữu • Click để §atrang bị");
            }
            return icon(f.getMaterial(), f.getColor() + "§l" + f.getName(), lore, true);
        }

        List<Element> parts = f.getParts();
        boolean can = true;
        StringBuilder need = new StringBuilder();
        for (Element part : parts) {
            boolean ok = owned.contains(part);
            can &= ok;
            if (need.length() > 0) need.append(" §7+ ");
            need.append(ok ? "§a✔ " : "§c✖ ").append(part.colored());
        }
        lore.add("§7Cần sở hữu: " + need);
        if (!can) {
            lore.add("§cChưa đủ nguyên tố gốc để kết hợp");
            return icon(Material.GRAY_DYE, "§8✖ §7Hợp thể " + f.getName(), lore, false);
        }
        int cost = settings.fusionCostLevels();
        if (cost > 0) lore.add("§7Phí: §e" + cost + " §7cấp độ");
        if (pd != null && pd.element == f) {
            lore.add("§e§l⚠ Click lần nữa để XÁC NHẬN");
            lore.add("§cBạn sẽ MẤT " + parts.get(0).colored() + " §cvà " + parts.get(1).colored());
        } else {
            lore.add("§aĐủ điều kiện §7- Click để §dkết hợp");
            lore.add("§8(Sẽ tiêu hao cả hai nguyên tố gốc)");
        }
        return icon(f.getMaterial(), "§d✦ §7Hợp thể " + f.colored() + " §d✦", lore, true);
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
        UUID id = p.getUniqueId();

        if (slot == BACK_SLOT) {
            pending.remove(id);
            elementGui.open(p);
            return;
        }

        boolean changed = false;
        List<Element> eq = manager.getEquipped(p);
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (slot == EQUIP_SLOTS[i] && i < eq.size()) {
                Element el = eq.get(i);
                manager.unequip(p, el);
                p.sendMessage("§7Đã tắt nguyên tố " + el.colored() + "§7.");
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);
                changed = true;
            }
        }

        for (int i = 0; i < fusions.size() && i < FUSION_SLOTS.length; i++) {
            if (slot == FUSION_SLOTS[i]) {
                clickFusion(p, fusions.get(i));
                changed = true;
            }
        }

        if (changed) {
            render(top, p);
            p.updateInventory();
        }
    }

    private void clickFusion(Player p, Element f) {
        UUID id = p.getUniqueId();

        // đã sở hữu -> bật / tắt
        if (manager.owns(p, f)) {
            pending.remove(id);
            elementGui.toggle(p, f);
            return;
        }

        List<Element> parts = f.getParts();
        if (!manager.canFuse(p, f)) {
            pending.remove(id);
            StringBuilder miss = new StringBuilder();
            for (Element part : parts) {
                if (manager.owns(p, part)) continue;
                if (miss.length() > 0) miss.append("§c, ");
                miss.append(part.colored());
            }
            p.sendMessage("§cBạn chưa đủ nguyên tố gốc để kết hợp " + f.colored() + "§c. Còn thiếu: " + miss);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }

        int cost = settings.fusionCostLevels();
        if (cost > 0 && p.getGameMode() != GameMode.CREATIVE && p.getLevel() < cost) {
            pending.remove(id);
            p.sendMessage("§cCần §e" + cost + " §ccấp độ để kết hợp " + f.colored() + "§c (bạn có " + p.getLevel() + ").");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }

        // bước xác nhận: click lần 1 -> hỏi, click lần 2 (trong 10 giây) -> thực hiện
        Pending pd = activePending(p);
        if (pd == null || pd.element != f) {
            pending.put(id, new Pending(f, System.currentTimeMillis() + CONFIRM_MS));
            p.sendMessage("§e⚠ Kết hợp " + parts.get(0).colored() + " §7+ " + parts.get(1).colored() + " §7= " + f.colored()
                    + " §esẽ làm bạn §cMẤT §ecả hai nguyên tố gốc. Click lần nữa trong 10 giây để xác nhận.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
            return;
        }

        pending.remove(id);
        ElementManager.FuseResult r = manager.fuse(p, f);
        switch (r) {
            case OK:
                p.sendTitle("§d§l✦ HỢP THỂ ✦", f.colored() + " §7- đã thành hình!", 5, 50, 15);
                p.sendMessage("§d✦ Kết hợp thành công! §7Bạn đã mất " + parts.get(0).colored() + " §7và "
                        + parts.get(1).colored() + "§7, nhận được hợp thể " + f.colored() + "§7. Click để trang bị.");
                FusionFx.fuseSuccess(p, f);
                break;
            case NO_LEVELS:
                p.sendMessage("§cCần §e" + cost + " §ccấp độ để kết hợp " + f.colored() + "§c.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                break;
            case MISSING:
                p.sendMessage("§cBạn không còn đủ nguyên tố gốc để kết hợp " + f.colored() + "§c.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                break;
            case ALREADY:
                p.sendMessage("§cBạn đã sở hữu hợp thể " + f.colored() + " §crồi.");
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) pending.remove(e.getPlayer().getUniqueId());
    }
}
