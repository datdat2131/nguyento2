package vn.nguyento;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Bộ cộng hưởng: trang bị đủ 2-3 nguyên tố định sẵn thì nhận thêm buff.
 * Định nghĩa hoàn toàn trong config.yml, mục "sets".
 */
public final class SetBonus {

    public final String id;
    public final String name;
    public final String color;
    public final Set<Element> elements;
    public final double damage;
    public final double maxHealth;
    public final double speedPercent;
    public final double attackSpeedPercent;
    public final double armorPercent;
    public final double damageReductionPercent;

    private SetBonus(String id, String name, String color, Set<Element> elements, ConfigurationSection s) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.elements = elements;
        this.damage = s.getDouble("damage", 0);
        this.maxHealth = s.getDouble("max-health", 0);
        this.speedPercent = s.getDouble("speed-percent", 0);
        this.attackSpeedPercent = s.getDouble("attack-speed-percent", 0);
        this.armorPercent = s.getDouble("armor-percent", 0);
        this.damageReductionPercent = s.getDouble("damage-reduction-percent", 0);
    }

    /** Đã trang bị đủ mọi nguyên tố của bộ chưa. */
    public boolean matches(Collection<Element> equipped) {
        return equipped.containsAll(elements);
    }

    public String colored() {
        return color + name;
    }

    /** Danh sách dòng mô tả buff của bộ. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        if (damage != 0) out.add("+" + fmt(damage) + " sát thương");
        if (maxHealth != 0) out.add("+" + fmt(maxHealth) + " máu tối đa");
        if (speedPercent != 0) out.add("+" + fmt(speedPercent) + "% tốc độ di chuyển");
        if (attackSpeedPercent != 0) out.add("+" + fmt(attackSpeedPercent) + "% tốc độ đánh");
        if (armorPercent != 0) out.add("+" + fmt(armorPercent) + "% giáp");
        if (damageReductionPercent != 0) out.add("Giảm " + fmt(damageReductionPercent) + "% sát thương nhận vào");
        return out;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Đọc toàn bộ bộ cộng hưởng từ config (bỏ qua mục sai: thiếu nguyên tố, id lạ, ít hơn 2 hoặc nhiều hơn 3 nguyên tố). */
    public static List<SetBonus> loadAll(FileConfiguration cfg) {
        List<SetBonus> out = new ArrayList<>();
        ConfigurationSection root = cfg.getConfigurationSection("sets");
        if (root == null) return out;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue; // ví dụ khóa "enabled"
            Set<Element> els = EnumSet.noneOf(Element.class);
            boolean bad = false;
            for (String id : s.getStringList("elements")) {
                Element e = Element.fromId(id);
                if (e == null) {
                    bad = true;
                    break;
                }
                els.add(e);
            }
            if (bad || els.size() < 2 || els.size() > 3) continue;
            out.add(new SetBonus(key, s.getString("name", key), s.getString("color", "§6"), els, s));
        }
        return out;
    }
}
