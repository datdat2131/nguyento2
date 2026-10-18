package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vật phẩm kỹ năng (bất ly thân): tự được give khi nguyên tố Bóng Tối / Ánh Sáng nằm ở Ô 1,
 * tự biến mất khi gỡ nguyên tố. Chuột phải để dùng chiêu.
 */
public class SkillItems {

    private final NguyenToPlugin plugin;
    private final ElementManager manager;
    private final NamespacedKey itemKey;
    private final NamespacedKey ownerKey;
    private final Set<UUID> warned = new HashSet<>();

    public SkillItems(NguyenToPlugin plugin, ElementManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.itemKey = new NamespacedKey(plugin, "skillitem");
        this.ownerKey = new NamespacedKey(plugin, "skillowner");
    }

    private Material materialFor(Element e) {
        return e == Element.ANH_SANG ? Material.NETHER_STAR : Material.ECHO_SHARD;
    }

    public ItemStack create(Player owner, Element e) {
        ItemStack item = new ItemStack(materialFor(e));
        ItemMeta meta = item.getItemMeta();
        String title = e == Element.ANH_SANG ? "Ấn Thần Phạt" : "Ấn Lãnh Địa Bóng Tối";
        meta.setDisplayName(e.getColor() + "§l✦ " + title + " ✦");
        List<String> lore = new ArrayList<>();
        lore.add("§8Vật phẩm kỹ năng");
        lore.add("");
        lore.add("§7Nhấn §fchuột phải §7để thi triển chiêu");
        lore.add("§7của nguyên tố " + e.colored() + " §7ở Ô số 1.");
        lore.add("");
        lore.add("§cBất ly thân §7- thuộc về §f" + owner.getName());
        lore.add("§8Không thể vứt, cất vào rương hay trao đổi.");
        meta.setLore(lore);
        ElementManager.addGlow(meta);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, e.getId());
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBound(ItemStack item) {
        if (item == null) return false;
        Material t = item.getType();
        if (t != Material.ECHO_SHARD && t != Material.NETHER_STAR) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    public Element elementOf(ItemStack item) {
        if (!isBound(item)) return null;
        return Element.fromId(item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    public boolean isOwner(ItemStack item, Player p) {
        if (!isBound(item)) return false;
        String s = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return p.getUniqueId().toString().equals(s);
    }

    /** Đồng bộ: đảm bảo người chơi có đúng 1 vật phẩm kỹ năng (hoặc không có nếu Ô 1 không có chiêu). */
    public void sync(Player p) {
        List<Element> eq = manager.getEquipped(p);
        Element want = (!eq.isEmpty() && eq.get(0).isSpecial()) ? eq.get(0) : null;
        boolean found = false;

        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!isBound(it)) continue;
            if (want != null && !found && elementOf(it) == want && isOwner(it, p)) {
                found = true;
            } else {
                inv.setItem(i, null);
            }
        }

        ItemStack cursor = p.getItemOnCursor();
        if (isBound(cursor)) {
            if (want != null && !found && elementOf(cursor) == want && isOwner(cursor, p)) found = true;
            else p.setItemOnCursor(null);
        }

        UUID id = p.getUniqueId();
        if (want == null) {
            warned.remove(id);
            return;
        }
        if (!found) {
            Map<Integer, ItemStack> left = inv.addItem(create(p, want));
            if (left.isEmpty()) {
                warned.remove(id);
                p.sendMessage("§aBạn nhận được vật phẩm kỹ năng của " + want.colored()
                        + "§a. §7Nhấn §fchuột phải §7để dùng chiêu.");
            } else if (warned.add(id)) {
                p.sendMessage("§cTúi đồ đầy! Hãy chừa 1 ô trống để nhận vật phẩm kỹ năng.");
            }
        }
    }

    public void removeAll(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isBound(contents[i])) inv.setItem(i, null);
        }
        if (isBound(p.getItemOnCursor())) p.setItemOnCursor(null);
        warned.remove(p.getUniqueId());
    }

    /** Mỗi giây kiểm tra lại (phòng trường hợp mất/trùng vật phẩm, túi đầy lúc trước...). */
    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sync(p);
        }, 20L, 20L);
    }
}
