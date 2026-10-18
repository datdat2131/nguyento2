package vn.nguyento;

import java.util.ArrayList;
import java.util.List;

/** Đọc config.yml + sinh mô tả buff/nội tại/chiêu hiển thị trong GUI. */
public class Settings {

    private final NguyenToPlugin plugin;

    public Settings(NguyenToPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    /** Giá trị số của nguyên tố, ví dụ n(LUA, "damage"). */
    public double n(Element e, String key) {
        return plugin.getConfig().getDouble("elements." + e.getId() + "." + key);
    }

    /** Xác suất 0..1 từ giá trị % trong config. */
    public double chance(Element e, String key) {
        return n(e, key) / 100.0;
    }

    public boolean gb(String key) {
        return plugin.getConfig().getBoolean("general." + key);
    }

    // ------------------------------------------------------------------
    //  Mô tả hiển thị
    // ------------------------------------------------------------------

    public static String f(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private void stat(List<String> out, Element e, String key, String format) {
        double v = n(e, key);
        if (v != 0) out.add(format.replace("{v}", f(v)));
    }

    public List<String> describe(Element e) {
        List<String> buffs = new ArrayList<>();
        List<String> passives = new ArrayList<>();
        List<String> skills = new ArrayList<>();

        switch (e) {
            case LUA:
                buffs.add("Kháng lửa và dung nham");
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                if (n(e, "ignite-chance") > 0)
                    passives.add(f(n(e, "ignite-chance")) + "% gây cháy mục tiêu " + f(n(e, "ignite-seconds")) + "s khi tấn công");
                break;
            case BANG:
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                if (n(e, "slow-chance") > 0)
                    passives.add(f(n(e, "slow-chance")) + "% làm chậm mục tiêu " + f(n(e, "slow-seconds")) + "s khi tấn công");
                break;
            case NUOC:
                stat(buffs, e, "heal-bonus-percent", "+{v}% hiệu quả hồi máu");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                if (n(e, "heal-on-hit-chance") > 0)
                    passives.add(f(n(e, "heal-on-hit-chance")) + "% hồi " + f(n(e, "heal-on-hit-amount")) + " máu khi tấn công");
                break;
            case DAT:
                stat(buffs, e, "armor-percent", "+{v}% giáp");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                break;
            case GIO:
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                stat(buffs, e, "attack-speed-percent", "+{v}% tốc độ đánh");
                stat(buffs, e, "dodge-chance", "+{v}% cơ hội né đòn");
                break;
            case SET:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "attack-speed-percent", "+{v}% tốc độ đánh");
                if (n(e, "lightning-chance") > 0)
                    passives.add(f(n(e, "lightning-chance")) + "% phóng điện khi giao tranh, gây " + f(n(e, "lightning-damage"))
                            + " sát thương lan quanh mục tiêu");
                break;
            case BONG_TOI:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "lifesteal-percent", "{v}% sát thương gây ra hồi thành máu");
                if (n(e, "dark-chance") > 0)
                    passives.add(f(n(e, "dark-chance")) + "% đòn đánh gây thêm " + f(n(e, "dark-bonus-percent")) + "% sát thương bóng tối");
                skills.add("Lãnh Địa Bóng Tối: trang bị ở Ô 1, chuột phải vật phẩm kỹ năng");
                skills.add("Tạo vùng bóng tối " + f(n(e, "skill.duration")) + "s, bán kính " + f(n(e, "skill.radius"))
                        + " block. Bản thân tăng tốc, +" + f(n(e, "skill.owner-bonus-damage")) + " sát thương, +"
                        + f(n(e, "skill.owner-extra-lifesteal-percent")) + "% hút máu. Kẻ địch bên trong bị chậm"
                        + (n(e, "skill.enemy-nausea") > 0 ? " và buồn nôn" : ""));
                skills.add("Hồi chiêu " + f(n(e, "skill.cooldown")) + "s");
                break;
            case ANH_SANG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "damage-reduction-percent", "Giảm {v}% sát thương nhận vào");
                passives.add("Nội tại ẩn: khi máu dưới " + f(n(e, "shield.health-threshold-percent"))
                        + "% tạo lá chắn máu vàng bằng " + f(n(e, "shield.shield-percent"))
                        + "% máu tối đa (hồi " + f(n(e, "shield.cooldown")) + "s)");
                skills.add("Thần Phạt: trang bị ở Ô 1, chuột phải vật phẩm kỹ năng");
                skills.add("Tạo cột hào quang phạm vi " + f(n(e, "skill.radius")) + " block, gây "
                        + f(n(e, "skill.damage")) + " sát thương");
                skills.add("Hồi chiêu " + f(n(e, "skill.cooldown")) + "s");
                break;
            default:
                break;
        }

        List<String> out = new ArrayList<>();
        if (!buffs.isEmpty()) {
            out.add("§6§lBuff:");
            for (String s : buffs) wrap(out, s, "§7 • ", "§7   ");
        }
        if (!passives.isEmpty()) {
            out.add("§d§lNội tại:");
            for (String s : passives) wrap(out, s, "§7 • ", "§7   ");
        }
        if (!skills.isEmpty()) {
            out.add("§c§lChiêu:");
            for (String s : skills) wrap(out, s, "§7 • ", "§7   ");
        }
        return out;
    }

    private static void wrap(List<String> out, String text, String first, String rest) {
        StringBuilder line = new StringBuilder();
        boolean firstLine = true;
        for (String w : text.split(" ")) {
            if (line.length() > 0 && line.length() + w.length() + 1 > 36) {
                out.add((firstLine ? first : rest) + line);
                firstLine = false;
                line = new StringBuilder();
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add((firstLine ? first : rest) + line);
    }
}
