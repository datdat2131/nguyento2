package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Nhiệm vụ khó để mở khóa ô nguyên tố thứ 4 (hoặc admin cấp). Mọi mục tiêu chỉnh trong config.yml, mục extra-slot.quest.
 * Tiến độ lưu trong dữ liệu của từng người chơi nên không mất khi thoát game / tắt server.
 */
public class QuestManager implements Listener {

    private static final int DEF_MOBS = 500;
    private static final int DEF_PLAYERS = 5;
    private static final int DEF_FUSE = 2;
    private static final int DEF_OWNED = 8;

    /** Một mục tiêu của nhiệm vụ. */
    public static final class Objective {
        public final String key;
        public final String label;
        public final int target;

        Objective(String key, String label, int target) {
            this.key = key;
            this.label = label;
            this.target = target;
        }
    }

    private final NguyenToPlugin plugin;
    private final ElementManager manager;
    private final Settings settings;
    private final NamespacedKey questKey;
    /** Chống cày: người giết -> (nạn nhân -> thời điểm lần giết gần nhất). Chỉ lưu trong bộ nhớ. */
    private final Map<UUID, Map<UUID, Long>> lastPlayerKill = new HashMap<>();

    public QuestManager(NguyenToPlugin plugin, ElementManager manager, Settings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.questKey = new NamespacedKey(plugin, "quest");
    }

    // ------------------------------------------------------------------
    //  Cấu hình
    // ------------------------------------------------------------------

    private int cfgInt(String path, int def) {
        return settings.cfg().getInt("extra-slot.quest." + path, def);
    }

    /** Boss -> số lần cần hạ. Không có mục trong config thì dùng mặc định (Ender Dragon, Wither, Warden). */
    private Map<String, Integer> bossTargets() {
        Map<String, Integer> m = new LinkedHashMap<>();
        ConfigurationSection sec = settings.cfg().getConfigurationSection("extra-slot.quest.kill-bosses");
        if (sec == null) {
            m.put("ENDER_DRAGON", 1);
            m.put("WITHER", 1);
            m.put("WARDEN", 1);
            return m;
        }
        for (String k : sec.getKeys(false)) {
            int v = sec.getInt(k, 0);
            if (v > 0) m.put(k.toUpperCase(Locale.ROOT), v);
        }
        return m;
    }

    private static String bossName(String type) {
        switch (type) {
            case "ENDER_DRAGON": return "Rồng Ender";
            case "WITHER": return "Wither";
            case "WARDEN": return "Warden";
            case "ELDER_GUARDIAN": return "Elder Guardian";
            default: return type.toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    public List<Objective> objectives() {
        List<Objective> out = new ArrayList<>();
        int mobs = cfgInt("kill-mobs", DEF_MOBS);
        if (mobs > 0) out.add(new Objective("mobs", "Hạ quái vật", mobs));
        int players = cfgInt("kill-players", DEF_PLAYERS);
        if (players > 0) out.add(new Objective("players", "Hạ người chơi", players));
        for (Map.Entry<String, Integer> b : bossTargets().entrySet()) {
            out.add(new Objective("boss:" + b.getKey(), "Hạ " + bossName(b.getKey()), b.getValue()));
        }
        int fuse = cfgInt("fuse-count", DEF_FUSE);
        if (fuse > 0) out.add(new Objective("fuse", "Kết hợp hợp thể", fuse));
        int owned = cfgInt("own-elements", DEF_OWNED);
        if (owned > 0) out.add(new Objective("owned", "Sở hữu nguyên tố khác nhau", owned));
        return out;
    }

    /** Nhiệm vụ còn đang chạy với người chơi này không. */
    public boolean active(Player p) {
        return settings.extraSlotEnabled() && settings.questEnabled() && !manager.hasExtraSlot(p);
    }

    // ------------------------------------------------------------------
    //  Tiến độ (lưu dạng "khóa=số;khóa=số" trong dữ liệu người chơi)
    // ------------------------------------------------------------------

    private Map<String, Integer> read(Player p) {
        Map<String, Integer> m = new HashMap<>();
        String s = p.getPersistentDataContainer().get(questKey, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) return m;
        for (String part : s.split(";")) {
            int i = part.lastIndexOf('=');
            if (i <= 0) continue;
            try {
                m.put(part.substring(0, i), Integer.parseInt(part.substring(i + 1)));
            } catch (NumberFormatException ignored) {
                // bỏ qua mục hỏng
            }
        }
        return m;
    }

    private void write(Player p, Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        p.getPersistentDataContainer().set(questKey, PersistentDataType.STRING, sb.toString());
    }

    public int progress(Player p, String key) {
        return read(p).getOrDefault(key, 0);
    }

    /** Xóa tiến độ nhiệm vụ (admin). Không đụng tới ô thứ 4 đã mở. */
    public void reset(Player p) {
        p.getPersistentDataContainer().remove(questKey);
    }

    private Objective find(String key) {
        for (Objective o : objectives()) {
            if (o.key.equals(key)) return o;
        }
        return null;
    }

    private void set(Player p, String key, int value) {
        Map<String, Integer> m = read(p);
        int before = m.getOrDefault(key, 0);
        if (value == before) return;
        m.put(key, value);
        write(p, m);
        Objective o = find(key);
        if (o != null && before < o.target && value >= o.target) {
            p.sendMessage("§6✦ Nhiệm vụ ô thứ 4: §a✔ Hoàn thành mục tiêu §f" + o.label + "§a!");
            Fx.actionBar(p, "§6✦ Hoàn thành: §f" + o.label);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        }
        check(p);
    }

    private void inc(Player p, String key) {
        set(p, key, progress(p, key) + 1);
    }

    /** Đủ mọi mục tiêu thì mở khóa. Không có mục tiêu nào thì không tự mở (chỉ admin cấp). */
    public void check(Player p) {
        if (!active(p)) return;
        List<Objective> obj = objectives();
        if (obj.isEmpty()) return;
        Map<String, Integer> m = read(p);
        for (Objective o : obj) {
            if (m.getOrDefault(o.key, 0) < o.target) return;
        }
        manager.setExtraSlot(p, true);
        announceUnlock(p, true);
    }

    /** Gọi khi người chơi kết hợp hợp thể thành công. */
    public void onFuse(Player p) {
        if (active(p)) inc(p, "fuse");
    }

    /** Số nguyên tố khác nhau từng sở hữu (lấy mức cao nhất, vì kết hợp làm giảm số đang sở hữu). */
    public void refreshOwned(Player p) {
        if (!active(p)) return;
        int now = manager.getOwned(p).size();
        if (now > progress(p, "owned")) set(p, "owned", now);
    }

    // ------------------------------------------------------------------
    //  Hiển thị
    // ------------------------------------------------------------------

    public List<String> lines(Player p) {
        List<String> out = new ArrayList<>();
        if (!settings.extraSlotEnabled()) {
            out.add("§7Ô nguyên tố thứ 4 đang bị tắt trong config.");
            return out;
        }
        if (manager.hasExtraSlot(p)) {
            out.add("§a✔ Bạn đã mở khóa ô nguyên tố thứ 4!");
            return out;
        }
        if (!settings.questEnabled()) {
            out.add("§7Ô nguyên tố thứ 4 chỉ do admin cấp.");
            return out;
        }
        Map<String, Integer> m = read(p);
        for (Objective o : objectives()) {
            int cur = Math.min(o.target, m.getOrDefault(o.key, 0));
            boolean done = cur >= o.target;
            out.add((done ? "§a✔ " : "§7• ") + o.label + "§7: " + (done ? "§a" : "§f") + cur + "§7/§f" + o.target);
        }
        if (out.isEmpty()) out.add("§7Ô nguyên tố thứ 4 chỉ do admin cấp.");
        return out;
    }

    public void announceUnlock(Player p, boolean broadcast) {
        p.sendTitle("§6§l✦ MỞ KHÓA ✦", "§eÔ nguyên tố thứ 4 đã sẵn sàng!", 10, 70, 20);
        p.sendMessage("§6✦ Chúc mừng! Bạn đã mở khóa §eô nguyên tố thứ 4§6. Gõ §f/nguyento §6để trang bị thêm.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        if (Vfx.on()) {
            Location l = p.getLocation();
            Vfx.helix(l.getWorld(), l, 1.4, 2.4, 0, 3, Color.fromRGB(255, 215, 0), Color.fromRGB(255, 255, 235),
                    org.bukkit.Particle.END_ROD);
            Vfx.flash(l.getWorld(), l.clone().add(0, 1, 0));
        }
        if (broadcast && settings.cfg().getBoolean("extra-slot.broadcast", true)) {
            Bukkit.broadcastMessage("§6✦ §e" + p.getName() + " §7đã hoàn thành nhiệm vụ khó và mở khóa §6ô nguyên tố thứ 4§7!");
        }
    }

    // ------------------------------------------------------------------
    //  Sự kiện
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead) || !active(killer)) return;
        if (settings.cfg().getBoolean("extra-slot.quest.require-equipped", true)
                && manager.getEquipped(killer).isEmpty()) return;

        if (dead instanceof Player) {
            if (cfgInt("kill-players", DEF_PLAYERS) <= 0) return;
            long cooldown = (long) (settings.cfg().getDouble("extra-slot.quest.kill-players-cooldown-minutes", 30.0) * 60000.0);
            Map<UUID, Long> mine = lastPlayerKill.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
            long now = System.currentTimeMillis();
            Long last = mine.get(dead.getUniqueId());
            if (last != null && now - last < cooldown) return; // giết lại cùng một người trong thời gian ngắn không tính
            mine.put(dead.getUniqueId(), now);
            inc(killer, "players");
            return;
        }

        String type = dead.getType().name();
        if (bossTargets().containsKey(type)) {
            inc(killer, "boss:" + type);
        } else if (dead instanceof Enemy && cfgInt("kill-mobs", DEF_MOBS) > 0) {
            inc(killer, "mobs");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) refreshOwned(p);
        }, 40L);
    }
}
