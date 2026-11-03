package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Trạng thái có thời hạn gắn lên thực thể (người chơi / quái): Ướt, Nhiễm Điện, Đóng Băng, Thánh Ấn, Hắc Ấn,
 * khiên của hợp thể, buff tạm thời, hồi chiêu nội tại... Mỗi trạng thái có thời điểm hết hạn và một giá trị số.
 */
public final class Marks {

    public static final String WET = "wet";
    public static final String STATIC = "static";
    public static final String FROZEN = "frozen";
    public static final String HOLY = "holy";
    public static final String DARK = "dark";

    private static final class Entry {
        final long until;
        final double value;

        Entry(long until, double value) {
            this.until = until;
            this.value = value;
        }
    }

    private final Map<UUID, Map<String, Entry>> data = new HashMap<>();
    private BukkitTask purgeTask;

    /** Khiên giảm sát thương đang chạy của một hợp thể (giá trị = tỉ lệ giảm 0..1). */
    public static String guard(Element e) { return "guard:" + e.getId(); }

    /** Buff tốc độ di chuyển tạm thời (giá trị = tỉ lệ cộng thêm 0..1). */
    public static String speed(Element e) { return "speed:" + e.getId(); }

    /** Buff sát thương gây ra tạm thời (giá trị = tỉ lệ cộng thêm 0..1). */
    public static String dmgUp(Element e) { return "dmgup:" + e.getId(); }

    /** Khóa hồi chiêu của một nội tại (còn trạng thái = đang hồi). */
    public static String cooldown(Element e, String what) { return "cd:" + e.getId() + ":" + what; }

    public void set(UUID id, String key, double seconds, double value) {
        if (seconds <= 0) return;
        long until = System.currentTimeMillis() + (long) (seconds * 1000.0);
        data.computeIfAbsent(id, k -> new HashMap<>()).put(key, new Entry(until, value));
    }

    public void set(Entity e, String key, double seconds) {
        set(e.getUniqueId(), key, seconds, 1.0);
    }

    private Entry get(UUID id, String key) {
        Map<String, Entry> m = data.get(id);
        if (m == null) return null;
        Entry en = m.get(key);
        if (en == null) return null;
        if (en.until <= System.currentTimeMillis()) {
            m.remove(key);
            if (m.isEmpty()) data.remove(id);
            return null;
        }
        return en;
    }

    public boolean has(UUID id, String key) {
        return get(id, key) != null;
    }

    public boolean has(Entity e, String key) {
        return has(e.getUniqueId(), key);
    }

    /** Giá trị của trạng thái, 0 nếu không có / đã hết hạn. */
    public double value(UUID id, String key) {
        Entry en = get(id, key);
        return en == null ? 0.0 : en.value;
    }

    public long remainingMs(UUID id, String key) {
        Entry en = get(id, key);
        return en == null ? 0L : Math.max(0L, en.until - System.currentTimeMillis());
    }

    public void clear(UUID id, String key) {
        Map<String, Entry> m = data.get(id);
        if (m == null) return;
        m.remove(key);
        if (m.isEmpty()) data.remove(id);
    }

    public void clear(Entity e, String key) {
        clear(e.getUniqueId(), key);
    }

    public void clearAll(UUID id) {
        data.remove(id);
    }

    /** Dọn các trạng thái đã hết hạn (chạy định kỳ để không rò rỉ bộ nhớ với quái đã chết / người chơi đã thoát). */
    public void purge() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Map<String, Entry>>> it = data.entrySet().iterator();
        while (it.hasNext()) {
            Map<String, Entry> m = it.next().getValue();
            m.values().removeIf(en -> en.until <= now);
            if (m.isEmpty()) it.remove();
        }
    }

    public void start(NguyenToPlugin plugin) {
        if (purgeTask != null) purgeTask.cancel();
        purgeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::purge, 1200L, 1200L);
    }

    public void shutdown() {
        if (purgeTask != null) purgeTask.cancel();
        purgeTask = null;
        data.clear();
    }
}
