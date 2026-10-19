package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Chuột phải để dùng chiêu + chặn mọi cách vứt / cất / trao đổi vật phẩm kỹ năng. */
public class SkillItemListener implements Listener {

    private final NguyenToPlugin plugin;
    private final SkillItems items;
    private final SkillManager skills;

    public SkillItemListener(NguyenToPlugin plugin, SkillItems items, SkillManager skills) {
        this.plugin = plugin;
        this.items = items;
        this.skills = skills;
    }

    private void deny(Player p) {
        p.sendMessage("§cVật phẩm kỹ năng gắn liền với bạn, không thể bỏ vào đó.");
    }

    private boolean isBundle(ItemStack item) {
        return item != null && item.getType().name().endsWith("BUNDLE");
    }

    // ------------------------------------------------------------------
    //  Chuột phải = dùng chiêu
    // ------------------------------------------------------------------

    private void useSkill(Player p, ItemStack item) {
        if (!items.isOwner(item, p)) {
            items.sync(p); // xóa vật phẩm không thuộc về người này
            p.sendMessage("§cVật phẩm này không thuộc về bạn.");
            return;
        }
        if (!skills.tryCast(p, items.elementOf(item))) {
            items.sync(p); // nguyên tố đã bị gỡ -> thu hồi vật phẩm
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (!items.isBound(item)) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        // đang bấm vào rương / cửa / bàn chế tạo... thì để người chơi tương tác bình thường
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable()) return;
        e.setCancelled(true);
        useSkill(e.getPlayer(), item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getHand() == EquipmentSlot.HAND
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();
        if (!items.isBound(item)) return;
        e.setCancelled(true); // không cho đưa cho Allay / treo lên khung / đặt lên armor stand
        if (e instanceof PlayerInteractAtEntityEvent) return;
        useSkill(p, item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (items.isBound(e.getPlayerItem()) || items.isBound(e.getArmorStandItem())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------
    //  Không vứt được
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (items.isBound(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cKhông thể vứt vật phẩm kỹ năng.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(items::isBound);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> items.sync(p));
    }

    // ------------------------------------------------------------------
    //  Không cất vào rương / bàn chế tạo / túi... được
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        Inventory top = e.getInventory();
        Inventory clicked = e.getClickedInventory();
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        ClickType click = e.getClick();

        boolean cursorBound = items.isBound(cursor);
        boolean currentBound = items.isBound(current);

        // thả ra ngoài cửa sổ
        if (clicked == null) {
            if (cursorBound) {
                e.setCancelled(true);
                deny(p);
            }
            return;
        }

        // đặt vào ô của inventory phía trên (rương, ender chest, bàn chế tạo, lò, ...)
        if (clicked == top) {
            boolean bad = cursorBound;
            if (!bad && click == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
                bad = items.isBound(p.getInventory().getItem(e.getHotbarButton()));
            }
            if (!bad && click == ClickType.SWAP_OFFHAND) {
                bad = items.isBound(p.getInventory().getItemInOffHand());
            }
            if (bad) {
                e.setCancelled(true);
                deny(p);
                return;
            }
        }

        // shift-click / phím Q ngay trên vật phẩm
        if (currentBound && (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP)) {
            e.setCancelled(true);
            deny(p);
            return;
        }

        // nhét vào túi bundle
        if ((isBundle(cursor) && currentBound) || (isBundle(current) && cursorBound)) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!items.isBound(e.getOldCursor())) return;
        int topSize = e.getInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                deny((Player) e.getWhoClicked());
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Người khác không nhặt / phễu không hút được
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent e) {
        ItemStack stack = e.getItem().getItemStack();
        if (!items.isBound(stack)) return;
        if (e.getEntity() instanceof Player && items.isOwner(stack, (Player) e.getEntity())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (items.isBound(e.getItem().getItemStack())) e.setCancelled(true);
    }
}
