package vn.nguyento;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public class ElementListener implements Listener {

    private final ElementManager manager;

    public ElementListener(ElementManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.load(e.getPlayer());
        if (!manager.getOwned(e.getPlayer()).isEmpty()) {
            e.getPlayer().sendMessage("§7Nguyên tố đã được đặt lại khi bạn thoát game. Gõ §f/nguyento §7để trang bị lại.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.unload(e.getPlayer());
    }

    /** Hồi sinh: Minecraft xóa attribute modifier -> áp lại buff. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        manager.refreshLater(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        manager.refreshLater(e.getPlayer());
    }

    /** Chuột phải cuộn giấy để lĩnh ngộ nguyên tố. */
    @EventHandler
    public void onUseScroll(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        Element el = manager.readScroll(item);
        if (el == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();

        if (!manager.grant(p, el)) {
            p.sendMessage("§cBạn đã sở hữu nguyên tố " + el.colored() + " §crồi! Cuộn giấy không bị mất.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }

        item.setAmount(item.getAmount() - 1);
        p.sendMessage("§a✦ Bạn đã lĩnh ngộ nguyên tố " + el.colored() + "§a! §7Gõ §f/nguyento §7để trang bị.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        p.getWorld().spawnParticle(el.getParticle(), p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);
    }
}
