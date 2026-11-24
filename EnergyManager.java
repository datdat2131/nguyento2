package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thanh năng lượng nguyên tố (tùy chọn, config energy.enabled): thay cho hồi chiêu cố định của Bóng Tối / Ánh Sáng / Rồng.
 * Đánh trúng, bị đánh và thời gian trôi qua sẽ nạp năng lượng; dùng chiêu tiêu hao energy.cost.
 */
public class EnergyManager implements Listener {

    private final ElementManager manager;
    private final Settings settings;
    private final Map<UUID, Double> energy = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public EnergyManager(ElementManager manager, Settings settings) {
        this.manager = manager;
        this.settings = settings;
    }

    private double cfg(String key, double def) {
        return settings.cfg().getDouble("energy." + key, def);
    }

    public boolean enabled() {
        return settings.cfg().getBoolean("energy.enabled", false);
    }

    public double max() {
        double v = cfg("max", 100.0);
        return v > 0 ? v : 100.0;
    }

    /** Năng lượng tiêu hao mỗi lần dùng chiêu (không vượt quá max). */
    public double cost() {
        return Math.max(1.0, Math.min(max(), cfg("cost", 100.0)));
    }

    public double get(Player p) {
        return energy.getOrDefault(p.getUniqueId(), 0.0);
    }

    public boolean ready(Player p) {
        return get(p) + 1e-9 >= cost();
    }

    public void add(Player p, double amount) {
        if (!enabled() || amount <= 0) return;
        energy.put(p.getUniqueId(), Math.min(max(), get(p) + amount));
    }

    /** Đánh trúng một đòn. */
    public void onHit(Player p) {
        if (enabled() && manager.hasSpecial(p)) add(p, cfg("per-hit", 5.0));
    }

    /** Bị đánh một đòn. */
    public void onDamaged(Player p) {
        if (enabled() && manager.hasSpecial(p)) add(p, cfg("on-damaged", 3.0));
    }

    /** Dùng chiêu: trừ năng lượng. */
    public void consume(Player p) {
        energy.put(p.getUniqueId(), Math.max(0.0, get(p) - cost()));
        refreshBar(p);
    }

    /** Gọi mỗi 4 tick cho từng người chơi: nạp theo thời gian, cập nhật thanh khoảng mỗi giây một lần. */
    public void tick(Player p, boolean refresh) {
        if (!enabled() || !manager.hasSpecial(p)) {
            hideBar(p);
            return;
        }
        add(p, cfg("regen-per-second", 0.5) * 0.2);
        if (refresh) refreshBar(p);
    }

    private void refreshBar(Player p) {
        if (!settings.cfg().getBoolean("energy.bar", true)) {
            hideBar(p);
            return;
        }
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_10);
            bars.put(p.getUniqueId(), bar);
        }
        if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        boolean full = ready(p);
        bar.setProgress(Math.max(0.0, Math.min(1.0, get(p) / max())));
        bar.setColor(full ? BarColor.GREEN : BarColor.PURPLE);
        bar.setTitle(full ? "§a✦ Năng lượng nguyên tố đầy - dùng chiêu được rồi! ✦"
                : "§d✦ Năng lượng nguyên tố §f" + (int) get(p) + "§7/§f" + (int) max());
    }

    private void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hideBar(e.getPlayer());
        energy.remove(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
        energy.clear();
    }
}
