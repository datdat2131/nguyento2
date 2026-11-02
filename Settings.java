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

    public double perf(String key) {
        return plugin.getConfig().getDouble("performance." + key);
    }

    public boolean pb(String key) {
        return plugin.getConfig().getBoolean("performance." + key);
    }

    public boolean gb(String key) {
        return plugin.getConfig().getBoolean("general." + key);
    }

    /** % buff chỉ số (máu, sát thương, tốc độ, giáp) mà thành phần giữ lại khi nằm trong hợp thể. */
    public double fusionInherit() {
        return Math.max(0.0, plugin.getConfig().getDouble("fusion.inherit-stats-percent", 100.0));
    }

    /** Số cấp độ (level kinh nghiệm) tốn mỗi lần kết hợp. 0 = miễn phí. */
    public int fusionCostLevels() {
        return Math.max(0, plugin.getConfig().getInt("fusion.cost-levels", 0));
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
                skills.add("Lãnh Địa Bóng Tối: chuột phải vật phẩm kỹ năng (trang bị ở ô nào cũng được)");
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
                skills.add("Thần Phạt: chuột phải vật phẩm kỹ năng (trang bị ở ô nào cũng được)");
                skills.add("Tạo cột hào quang phạm vi " + f(n(e, "skill.radius")) + " block, gây "
                        + f(n(e, "skill.damage")) + " sát thương");
                skills.add("Hồi chiêu " + f(n(e, "skill.cooldown")) + "s");
                break;
            case RONG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "armor-percent", "+{v}% giáp");
                if (n(e, "breath-chance") > 0)
                    passives.add(f(n(e, "breath-chance")) + "% phun Hơi Thở Rồng, gây " + f(n(e, "breath-damage"))
                            + " sát thương lan bán kính " + f(n(e, "breath-radius")) + " block quanh mục tiêu");
                break;
            case PLASMA:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "attack-speed-percent", "+{v}% tốc độ đánh");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                if (n(e, "plasma-chance") > 0)
                    passives.add(f(n(e, "plasma-chance")) + "% gây Bùng Nổ Plasma: " + f(n(e, "plasma-damage"))
                            + " sát thương lan bán kính " + f(n(e, "plasma-radius")) + " block và đốt "
                            + f(n(e, "plasma-ignite-seconds")) + "s");
                break;
            case BANG_THUY:
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                if (n(e, "freeze-chance") > 0)
                    passives.add(f(n(e, "freeze-chance")) + "% Đóng Băng mục tiêu " + f(n(e, "freeze-seconds"))
                            + "s (gần như không thể di chuyển) và gây " + f(n(e, "freeze-damage")) + " sát thương");
                break;
            case THANH_LONG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                if (n(e, "smite-chance") > 0)
                    passives.add(f(n(e, "smite-chance")) + "% giáng Phán Quyết Thánh Long: " + f(n(e, "smite-damage"))
                            + " sát thương thánh và hồi " + f(n(e, "smite-heal")) + " máu cho bản thân");
                break;
            case HAC_LONG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                if (n(e, "curse-chance") > 0)
                    passives.add(f(n(e, "curse-chance")) + "% giáng Lời Nguyền Hắc Long: " + f(n(e, "curse-damage"))
                            + " sát thương bóng tối và héo mòn " + f(n(e, "curse-seconds")) + "s");
                break;
            case DUNG_NHAM:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "armor-percent", "+{v}% giáp");
                if (n(e, "magma-chance") > 0)
                    passives.add(f(n(e, "magma-chance")) + "% Dung Nham Phun Trào: " + f(n(e, "magma-damage"))
                            + " sát thương lan bán kính " + f(n(e, "magma-radius")) + " block và đốt "
                            + f(n(e, "magma-ignite-seconds")) + "s");
                break;
            case BAO_TO:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                stat(buffs, e, "attack-speed-percent", "+{v}% tốc độ đánh");
                if (n(e, "storm-chance") > 0)
                    passives.add(f(n(e, "storm-chance")) + "% Cơn Bão Sét: " + f(n(e, "storm-damage"))
                            + " sát thương lan bán kính " + f(n(e, "storm-radius")) + " block và hất tung kẻ địch");
                break;
            case BAO_TUYET:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                if (n(e, "blizzard-chance") > 0)
                    passives.add(f(n(e, "blizzard-chance")) + "% Bão Tuyết: " + f(n(e, "blizzard-damage"))
                            + " sát thương lan bán kính " + f(n(e, "blizzard-radius")) + " block và làm chậm "
                            + f(n(e, "blizzard-seconds")) + "s");
                break;
            case VIEM_LONG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                if (n(e, "inferno-chance") > 0)
                    passives.add(f(n(e, "inferno-chance")) + "% Long Viêm Thiêu Đốt: " + f(n(e, "inferno-damage"))
                            + " sát thương lan bán kính " + f(n(e, "inferno-radius")) + " block và đốt "
                            + f(n(e, "inferno-ignite-seconds")) + "s");
                break;
            default:
                break;
        }

        List<String> out = new ArrayList<>();
        if (e.isFusion()) {
            List<Element> parts = e.getParts();
            if (parts.size() == 2) {
                out.add("§7Hợp thể: " + parts.get(0).colored() + " §7+ " + parts.get(1).colored());
            }
            out.add("§7Chỉ chiếm §f1 ô §7nhưng nhận buff, nội tại");
            out.add("§7và chiêu của §fcả hai §7nguyên tố");
            if (fusionInherit() < 100) {
                out.add("§8(Chỉ số máu/sát thương/tốc độ/giáp của hai");
                out.add("§8nguyên tố gốc giữ lại " + f(fusionInherit()) + "%)");
            }
            out.add("");
        }
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
