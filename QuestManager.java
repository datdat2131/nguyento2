package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Nhiệm vụ khó để mở khóa ô nguyên tố thứ 4 (hoặc admin cấp). Cần hoàn thành TẤT CẢ mục tiêu đang bật:
 * hạ N người chơi KHÁC NHAU, hạ N quái, hoàn thành N cổng dungeon từ một rank trở lên, online đủ N giờ.
 * Mọi con số chỉnh trong config.yml (extra-slot.quest). Tiến độ lưu theo người chơi, không mất khi thoát game.
 */
public class QuestManager implements Listener {

    private static final int DEF_PLAYERS = 100;
    private static final int DEF_MOBS = 5000;
    private static final int DEF_DUNGEON = 10;
    private static final int DEF_ONLINE = 48;

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
    private final NamespacedKey victimsKey;

    public QuestManager(NguyenToPlugin plugin, ElementManager manager, Settings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.questKey = new NamespacedKey(plugin, "quest");
        this.victimsKey = new NamespacedKey(plugin, "quest_victims");
    }

    /** Bắt đầu vòng kiểm tra mỗi phút (thời gian online không có sự kiện riêng). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // đếm phút online của chính plugin (dự phòng khi server không có thống kê thời gian chơi)
                Map<String, Integer> m = read(p);
                m.put("playmin", m.getOrDefault("playmin", 0) + 1);
                write(p, m);
                if (active(p)) checkOnline(p);
            }
        }, 1200L, 1200L);
    }

    // ------------------------------------------------------------------
    //  Cấu hình
    // ------------------------------------------------------------------

    private int cfgInt(String path, int def) {
        return settings.cfg().getInt("extra-slot.quest." + path, def);
    }

    private String minRank() {
        return settings.cfg().getString("extra-slot.quest.dungeon-min-rank", "legendary");
    }

    private List<String> rankOrder() {
        List<String> l = settings.cfg().getStringList("extra-slot.quest.dungeon-ranks");
        if (l.isEmpty()) {
            l = java.util.Arrays.asList("common", "uncommon", "rare", "epic", "legendary", "mythic");
        }
        return l;
    }

    /** Vị trí của rank trong danh sách (khớp chính xác, hoặc viết tắt từ 3 chữ như "leg" = legendary). -1 nếu không có. */
    private int rankIndex(String rank) {
        if (rank == null) return -1;
        String r = rank.toLowerCase(Locale.ROOT).trim();
        List<String> order = rankOrder();
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).toLowerCase(Locale.ROOT).equals(r)) return i;
        }
        if (r.length() >= 3) {
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).toLowerCase(Locale.ROOT).startsWith(r)) return i;
            }
        }
        return -1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    public List<Objective> objectives() {
        List<Objective> out = new ArrayList<>();
        int players = cfgInt("kill-players", DEF_PLAYERS);
        if (players > 0) out.add(new Objective("players", "Hạ người chơi khác nhau", players));
        int mobs = cfgInt("kill-mobs", DEF_MOBS);
        if (mobs > 0) out.add(new Objective("mobs", "Tiêu diệt quái vật", mobs));
        int dungeon = cfgInt("dungeon-gates", DEF_DUNGEON);
        if (dungeon > 0) {
            out.add(new Objective("dungeon", "Hoàn thành cổng dungeon " + capitalize(minRank()) + " trở lên", dungeon));
        }
        int online = cfgInt("online-hours", DEF_ONLINE);
        if (online > 0) out.add(new Objective("online", "Online đủ (giờ)", online));
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

    /** Những người chơi (UUID) đã hạ gục - mỗi người chỉ tính MỘT lần mãi mãi. */
    private Set<String> victims(Player p) {
        Set<String> out = new LinkedHashSet<>();
        String s = p.getPersistentDataContainer().get(victimsKey, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    /** Số giờ đã online: dùng thống kê thời gian chơi của server, không có thì dùng số phút plugin tự đếm. */
    public double playHours(Player p) {
        try {
            return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
        } catch (Throwable t) {
            return read(p).getOrDefault("playmin", 0) / 60.0;
        }
    }

    /** Tiến độ hiện tại của một mục tiêu. */
    public int progress(Player p, String key) {
        if (key.equals("players")) return victims(p).size();
        if (key.equals("online")) return (int) playHours(p);
        return read(p).getOrDefault(key, 0);
    }

    /** Xóa tiến độ nhiệm vụ (admin). Không đụng tới ô thứ 4 đã mở. */
    public void reset(Player p) {
        p.getPersistentDataContainer().remove(questKey);
        p.getPersistentDataContainer().remove(victimsKey);
    }

    private Objective find(String key) {
        for (Objective o : objectives()) {
            if (o.key.equals(key)) return o;
        }
        return null;
    }

    /** Gọi sau khi một mục tiêu tăng: báo hoàn thành mục tiêu và kiểm tra mở khóa. */
    private void afterChange(Player p, String key, int before, int after) {
        Objective o = find(key);
        if (o != null && before < o.target && after >= o.target) {
            p.sendMessage("§6✦ Nhiệm vụ ô thứ 4: §a✔ Hoàn thành mục tiêu §f" + o.label + "§a!");
            Fx.actionBar(p, "§6✦ Hoàn thành: §f" + o.label);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        }
        check(p);
    }

    private void inc(Player p, String key) {
        Map<String, Integer> m = read(p);
        int before = m.getOrDefault(key, 0);
        m.put(key, before + 1);
        write(p, m);
        afterChange(p, key, before, before + 1);
    }

    /** Đủ mọi mục tiêu thì mở khóa. Không có mục tiêu nào thì không tự mở (chỉ admin cấp). */
    public void check(Player p) {
        if (!active(p)) return;
        List<Objective> obj = objectives();
        if (obj.isEmpty()) return;
        for (Objective o : obj) {
            if (progress(p, o.key) < o.target) return;
        }
        manager.setExtraSlot(p, true);
        announceUnlock(p, true);
    }

    /** Mục tiêu online: báo khi vừa đủ giờ, rồi kiểm tra mở khóa. */
    private void checkOnline(Player p) {
        Objective o = find("online");
        if (o == null) return;
        if (progress(p, "online") >= o.target) {
            Map<String, Integer> m = read(p);
            if (m.getOrDefault("online_done", 0) == 0) {
                m.put("online_done", 1);
                write(p, m);
                afterChange(p, "online", o.target - 1, o.target);
                return;
            }
        }
        check(p);
    }

    /** Giữ lại cho tương thích: nhiệm vụ mới không còn đếm số lần kết hợp / sưu tầm nguyên tố. */
    public void onFuse(Player p) {
        // không dùng nữa
    }

    public void refreshOwned(Player p) {
        // không dùng nữa
    }

    /**
     * Ghi nhận một cổng dungeon đã hoàn thành (dùng cho lệnh /nguyento quest dungeon, plugin dungeon gọi bằng lệnh khi người chơi
     * thắng cổng). 'rank' có thể null (khi đó coi như đạt). Trả về null nếu thành công, ngược lại là lý do không tính.
     */
    public String addDungeon(Player p, String rank) {
        if (!active(p)) return "Người chơi không còn nhiệm vụ này (đã mở khóa ô thứ 4 hoặc nhiệm vụ đang tắt).";
        if (cfgInt("dungeon-gates", DEF_DUNGEON) <= 0) return "Mục tiêu cổng dungeon đang tắt trong config.";
        if (rank != null) {
            int got = rankIndex(rank);
            if (got < 0) return "Rank '" + rank + "' không có trong danh sách dungeon-ranks.";
            int need = rankIndex(minRank());
            if (need >= 0 && got < need) {
                return "Cổng rank " + rank + " thấp hơn rank tối thiểu (" + minRank() + ") nên không được tính.";
            }
        }
        inc(p, "dungeon");
        Fx.actionBar(p, "§6✦ Nhiệm vụ ô thứ 4: cổng dungeon §f" + progress(p, "dungeon") + "§7/§f" + cfgInt("dungeon-gates", DEF_DUNGEON));
        return null;
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
        for (Objective o : objectives()) {
            int cur = Math.min(o.target, progress(p, o.key));
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
            int need = cfgInt("kill-players", DEF_PLAYERS);
            if (need <= 0) return;
            Set<String> seen = victims(killer);
            String victim = dead.getUniqueId().toString();
            if (seen.contains(victim)) return; // giết lại cùng một người không tính
            int before = seen.size();
            seen.add(victim);
            killer.getPersistentDataContainer().set(victimsKey, PersistentDataType.STRING, String.join(",", seen));
            Fx.actionBar(killer, "§6✦ Nhiệm vụ ô thứ 4: người chơi khác nhau §f" + seen.size() + "§7/§f" + need);
            afterChange(killer, "players", before, seen.size());
            return;
        }

        if (dead instanceof Enemy && cfgInt("kill-mobs", DEF_MOBS) > 0) {
            inc(killer, "mobs");
        }
    }
}
