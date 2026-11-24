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

/** Menu trang bị / tắt nguyên tố (kèm mô tả buff, nội tại, chiêu). Hợp thể có menu riêng (FusionGui). */
public class ElementGui implements Listener {

    private static final String TITLE = "§8✦ §5Nguyên Tố §8✦";
    private static final int SIZE = 45;
    private static final int[] EQUIP_SLOTS = {1, 3, 5, 7};
    private static final int INFO_SLOT = 13;
    private static final int FUSION_SLOT = 22;
    private static final int QUEST_SLOT = 24;
    private static final int CLEAR_SLOT = 40;
    static final String[] SLOT_NAMES = {"Sau lưng", "Sau lưng", "Sau lưng", "Sau lưng"};

    private final ElementManager manager;
    private final Settings settings;
    private FusionGui fusionGui;
    private QuestManager quest;

    public ElementGui(ElementManager manager, Settings settings) {
        this.manager = manager;
        this.settings = settings;
    }

    public void setFusionGui(FusionGui fusionGui) {
        this.fusionGui = fusionGui;
    }

    public void setQuest(QuestManager quest) {
        this.quest = quest;
    }

    /** Gửi tiến độ nhiệm vụ mở ô thứ 4 vào khung chat. */
    private void showQuest(Player p) {
        p.sendMessage("§6✦ Nhiệm vụ mở khóa ô nguyên tố thứ 4:");
        if (quest == null) return;
        for (String line : quest.lines(p)) p.sendMessage("  " + line);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
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

        // các ô trang bị (3 ô, ô thứ 4 cần mở khóa)
        int maxSlots = manager.maxSlots(p);
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (i < eq.size()) {
                Element e = eq.get(i);
                inv.setItem(EQUIP_SLOTS[i], icon(e.getMaterial(),
                        "§eÔ " + (i + 1) + " • " + SLOT_NAMES[i] + "§7: " + e.colored(),
                        List.of("", "§7Click để §ctắt §7nguyên tố này"), true));
            } else if (i >= maxSlots) {
                inv.setItem(EQUIP_SLOTS[i], icon(Material.RED_STAINED_GLASS_PANE,
                        "§cÔ " + (i + 1) + " • Chưa mở khóa",
                        List.of("", "§7Hoàn thành §6nhiệm vụ khó §7hoặc nhờ", "§7admin cấp để mở ô này.", "", "§eClick để xem tiến độ nhiệm vụ"), false));
            } else {
                inv.setItem(EQUIP_SLOTS[i], icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "§7Ô " + (i + 1) + " • " + SLOT_NAMES[i] + " §8(trống)",
                        List.of("", "§7Chọn một nguyên tố bên dưới để trang bị"), false));
            }
        }

        // các nguyên tố gốc (vị trí lấy từ Element.getGuiSlot)
        for (Element e : Element.values()) {
            if (e.isFusion()) continue;
            inv.setItem(e.getGuiSlot(), elementIcon(e, eq, owned));
        }

        List<String> help = new ArrayList<>(Arrays.asList(

                "",
                "§7Trang bị tối đa §f" + maxSlots + " §7nguyên tố §8(ô thứ 4: nhiệm vụ khó / admin cấp)§7.",
                "§7• Nguyên tố nối đuôi nhau bay thành một §fvòng",
                "§7  tròn quay sau lưng bạn (kiểu Cầu Lục Đạo)",
                "§7• Click nguyên tố để bật / tắt",
                "§7• Nhận nguyên tố bằng cuộn giấy",
                "",
                "§7Mọi buff & nội tại hoạt động ở §fmọi ô§7.",
                "",
                "§cChiêu thức §7(Bóng Tối, Ánh Sáng, Rồng): trang bị",
                "§7ở ô nào cũng được, bạn sẽ nhận vật phẩm kỹ năng,",
                "§7§fchuột phải §7vật phẩm đó để dùng chiêu.",
                "§8(Vật phẩm bất ly thân, không vứt/cất được)",
                "",
                "§dHợp thể §7(nút Đe ở giữa): kết hợp 2 nguyên tố,",
                "§7chỉ chiếm §f1 ô §7nhưng mạnh hơn nhiều."));
        List<SetBonus> allSets = settings.sets();
        if (!allSets.isEmpty()) {
            List<SetBonus> active = settings.activeSets(eq);
            help.add("");
            help.add("§6Bộ cộng hưởng §7(trang bị đủ nguyên tố):");
            for (SetBonus sb : allSets) {
                StringBuilder names = new StringBuilder();
                for (Element el : sb.elements) {
                    if (names.length() > 0) names.append("§7 + ");
                    names.append(el.colored());
                }
                help.add((active.contains(sb) ? "§a✔ " : "§7• ") + sb.colored() + "§7: " + names);
                if (active.contains(sb)) help.add("§8   " + String.join(", ", sb.describe()));
            }
        }
        if (settings.counterEnabled() && !settings.counterTable().isEmpty()) {
            help.add("");
            help.add("§bVòng tương khắc §7(khắc: §a+" + (int) settings.counterBonusPercent() + "%§7, bị khắc: §c-"
                    + (int) settings.counterPenaltyPercent() + "%§7 sát thương):");
            for (java.util.Map.Entry<Element, java.util.Set<Element>> en : settings.counterTable().entrySet()) {
                StringBuilder beaten = new StringBuilder();
                for (Element b : en.getValue()) {
                    if (beaten.length() > 0) beaten.append("§7, ");
                    beaten.append(b.colored());
                }
                help.add("§7• " + en.getKey().colored() + " §8▶ " + beaten);
            }
        }
        inv.setItem(INFO_SLOT, icon(Material.BOOK, "§eHướng dẫn", help, false));

        inv.setItem(FUSION_SLOT, fusionButton(owned));
        inv.setItem(QUEST_SLOT, questButton(p));

        inv.setItem(CLEAR_SLOT, icon(Material.BARRIER, "§cTắt tất cả",
                List.of("", "§7Gỡ toàn bộ nguyên tố đang trang bị"), false));
    }

    private ItemStack questButton(Player p) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (quest != null) lore.addAll(quest.lines(p));
        lore.add("");
        lore.add("§eClick để gửi tiến độ vào khung chat");
        boolean done = manager.hasExtraSlot(p);
        return icon(Material.ENCHANTED_BOOK, (done ? "§a" : "§6") + "§lNhiệm vụ Ô Nguyên Tố Thứ 4", lore, done);
    }

    private ItemStack fusionButton(List<Element> owned) {
        int total = 0;
        int have = 0;
        for (Element f : Element.values()) {
            if (!f.isFusion()) continue;
            total++;
            if (owned.contains(f)) have++;
        }
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Kết hợp 2 nguyên tố thành một §dhợp thể§7");
        lore.add("§7mạnh hơn với nội tại riêng.");
        lore.add("§cHai nguyên tố gốc sẽ bị MẤT khi kết hợp.");
        lore.add("");
        lore.add("§7Đã sở hữu: §d" + have + "§7/§d" + total + " §7hợp thể");
        lore.add("");
        lore.add("§eClick để mở menu Hợp Thể");
        return icon(Material.ANVIL, "§d§l✦ Hợp Thể Nguyên Tố ✦", lore, true);
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

        // click vào ô chưa mở khóa / nút nhiệm vụ -> xem tiến độ
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (slot == EQUIP_SLOTS[i] && i >= manager.maxSlots(p)) showQuest(p);
        }
        if (slot == QUEST_SLOT) showQuest(p);

        // click vào nguyên tố gốc
        for (Element el : Element.values()) {
            if (!el.isFusion() && slot == el.getGuiSlot()) {
                toggle(p, el);
                changed = true;
            }
        }

        // mở menu hợp thể
        if (slot == FUSION_SLOT) {
            if (fusionGui != null) {
                fusionGui.open(p);
                return;
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

    /** Bật / tắt một nguyên tố đã sở hữu (dùng chung cho menu nguyên tố và menu hợp thể). */
    public void toggle(Player p, Element el) {
        if (!manager.owns(p, el)) {
            p.sendMessage("§cBạn chưa sở hữu nguyên tố " + el.colored() + "§c.");
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
            p.sendMessage("§cBạn đã trang bị tối đa " + manager.maxSlots(p)
                    + " nguyên tố! Hãy tắt bớt một nguyên tố trước.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        } else if (r == ElementManager.EquipResult.OK) {
            int slot = manager.getEquipped(p).indexOf(el);
            p.sendMessage("§aĐã trang bị nguyên tố " + el.colored() + " §7(" + SLOT_NAMES[slot] + ")§a.");
            if (el.hasSkill()) {
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
