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
                skills.add("Tạo lãnh địa bóng tối bán kính " + f(n(e, "skill.radius")) + " block trong " + f(n(e, "skill.duration"))
                        + "s. Kẻ địch bên trong bị KHÓA CHÂN, không thể chạy ra ngoài, bị chậm"
                        + (n(e, "skill.enemy-nausea") > 0 ? " và buồn nôn" : "")
                        + ". Bản thân tăng tốc, +" + f(n(e, "skill.owner-bonus-damage")) + " sát thương, +"
                        + f(n(e, "skill.owner-extra-lifesteal-percent")) + "% hút máu");
                skills.add("Hồi chiêu " + f(n(e, "skill.cooldown")) + "s");
                break;
            case ANH_SANG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "damage-reduction-percent", "Giảm {v}% sát thương nhận vào");
                passives.add("Nội tại ẩn: khi máu dưới " + f(n(e, "shield.health-threshold-percent"))
                        + "% tạo lá chắn máu vàng bằng " + f(n(e, "shield.shield-percent"))
                        + "% máu tối đa (hồi " + f(n(e, "shield.cooldown")) + "s)");
                skills.add("Thần Phạt: chuột phải vật phẩm kỹ năng (trang bị ở ô nào cũng được)");
                skills.add("Triệu hồi " + f(n(e, "skill.pillars")) + " cột ánh sáng giáng xuống kẻ địch gần nhất, mỗi cột gây "
                        + f(n(e, "skill.damage")) + " sát thương trong bán kính " + f(n(e, "skill.radius")) + " block");
                skills.add("Hồi chiêu " + f(n(e, "skill.cooldown")) + "s");
                break;
            case RONG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "armor-percent", "+{v}% giáp");
                skills.add("Hơi Thở Rồng: chuột phải vật phẩm kỹ năng (trang bị ở ô nào cũng được)");
                skills.add("Tạo vùng hơi thở rồng bán kính " + f(n(e, "skill.radius")) + " block, gây "
                        + f(n(e, "skill.damage-per-second")) + " sát thương mỗi giây trong " + f(n(e, "skill.duration")) + "s");
                skills.add("Hồi chiêu " + f(n(e, "skill.cooldown")) + "s");
                break;

            // ---------------- HỢP THỂ ----------------
            case FIRE_WATER:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "attack-speed-percent", "+{v}% tốc độ đánh");
                if (n(e, "steam-chance") > 0) {
                    passives.add(f(n(e, "steam-chance")) + "% tạo vụ nổ hơi nước nhỏ: " + f(n(e, "steam-damage"))
                            + " sát thương phép bán kính " + f(n(e, "steam-radius")) + " block, Giảm Tốc I "
                            + f(n(e, "steam-slow-seconds")) + "s");
                    passives.add("Mục tiêu đang Thiêu Đốt: vụ nổ +" + f(n(e, "steam-burn-bonus-percent")) + "% sát thương");
                    passives.add("Mục tiêu đang Ướt: " + f(n(e, "steam-stun-chance")) + "% Choáng "
                            + f(n(e, "steam-stun-seconds")) + "s");
                }
                guardLine(passives, e, "tạo màn sương");
                break;
            case BAO_CAT:
                stat(buffs, e, "armor-percent", "+{v}% giáp");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "attack-speed-percent", "+{v}% tốc độ đánh");
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                if (n(e, "sand-chance") > 0) {
                    passives.add(f(n(e, "sand-chance")) + "% tạo cơn bão cát: " + f(n(e, "sand-damage"))
                            + " sát thương diện rộng " + f(n(e, "sand-radius")) + " block, Giảm tốc "
                            + f(n(e, "sand-slow-percent")) + "% trong " + f(n(e, "sand-slow-seconds")) + "s");
                    passives.add(f(n(e, "sand-knockup-chance")) + "% hất tung nhẹ mỗi kẻ địch trúng");
                    passives.add("Trúng từ " + f(n(e, "sand-multi-count")) + " mục tiêu: +" + f(n(e, "sand-speed-bonus-percent"))
                            + "% tốc độ di chuyển " + f(n(e, "sand-speed-seconds")) + "s");
                }
                guardLine(passives, e, "tạo Khiên Cát");
                break;
            case HAN_LOI:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "attack-speed-percent", "+{v}% tốc độ đánh");
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                if (n(e, "bolt-chance") > 0) {
                    passives.add(f(n(e, "bolt-chance")) + "% triệu hồi tia sét băng: " + f(n(e, "bolt-damage"))
                            + " sát thương sét + " + f(n(e, "frost-damage")) + " sát thương băng");
                    passives.add("Mục tiêu bị Chậm " + f(n(e, "slow-percent")) + "% trong " + f(n(e, "slow-seconds")) + "s");
                    passives.add("Mục tiêu đang bị Chậm: " + f(n(e, "freeze-chance")) + "% Đóng Băng "
                            + f(n(e, "freeze-seconds")) + "s");
                    passives.add("Đánh mục tiêu đang Đóng Băng: phá băng, +" + f(n(e, "shatter-bonus-percent")) + "% sát thương");
                }
                guardLine(passives, e, "tạo Lôi Băng Hộ Thể");
                break;
            case LOI_VIEM:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                if (n(e, "strike-chance") > 0) {
                    passives.add("Thiên Hỏa Lôi Bạo: " + f(n(e, "strike-chance")) + "% tạo Lôi Viêm lên mục tiêu, gây "
                            + f(n(e, "fire-damage")) + " sát thương lửa + " + f(n(e, "lightning-damage")) + " sát thương sét");
                    passives.add("Thiêu Đốt " + f(n(e, "burn-seconds")) + "s và Nhiễm Điện " + f(n(e, "static-seconds")) + "s");
                    passives.add("Mục tiêu có cả 2 hiệu ứng: Lôi Viêm Bộc Phát +" + f(n(e, "burst-bonus-percent"))
                            + "% sát thương, lan sang tối đa " + f(n(e, "burst-targets")) + " kẻ địch trong "
                            + f(n(e, "burst-radius")) + " block (hồi " + f(n(e, "burst-cooldown")) + "s)");
                }
                break;
            case BANG_THUY:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "speed-percent", "+{v}% tốc độ di chuyển");
                if (n(e, "freeze-chance") > 0)
                    passives.add(f(n(e, "freeze-chance")) + "% Đóng Băng mục tiêu " + f(n(e, "freeze-seconds"))
                            + "s (gần như không thể di chuyển) và gây " + f(n(e, "freeze-damage")) + " sát thương");
                break;
            case THANH_LONG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "armor-percent", "+{v}% giáp");
                stat(buffs, e, "damage-reduction-percent", "Giảm {v}% sát thương nhận vào");
                if (n(e, "soul-chance") > 0) {
                    passives.add("Thánh Long Giáng Thế: " + f(n(e, "soul-chance")) + "% triệu hồi Long Hồn Ánh Sáng, gây "
                            + f(n(e, "soul-damage")) + " sát thương Ánh Sáng + Rồng");
                    passives.add("Mục tiêu bị Thánh Ấn " + f(n(e, "mark-seconds")) + "s: đánh trúng gây thêm "
                            + f(n(e, "holy-bonus-percent")) + "% sát thương");
                }
                guardLine(passives, e, "kích hoạt Long Hộ");
                break;
            case HAC_LONG:
                stat(buffs, e, "damage", "+{v} sát thương");
                stat(buffs, e, "max-health", "+{v} máu tối đa");
                stat(buffs, e, "lifesteal-percent", "{v}% sát thương gây ra hồi thành máu");
                if (n(e, "soul-chance") > 0) {
                    passives.add("Hắc Long Thôn Phệ: " + f(n(e, "soul-chance")) + "% triệu hồi Hắc Long Hồn, gây "
                            + f(n(e, "soul-damage")) + " sát thương Rồng + Bóng Tối");
                    passives.add("Mục tiêu bị Hắc Ấn " + f(n(e, "mark-seconds")) + "s: đánh trúng gây Thôn Phệ +"
                            + f(n(e, "devour-bonus-percent")) + "% sát thương và hồi " + f(n(e, "devour-heal-percent"))
                            + "% máu tối đa");
                }
                guardLine(passives, e, "kích hoạt Hắc Long Hộ Thể");
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
                stat(buffs, e, "max-health", "+{v} máu tối đa");
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
            out.add("§7Nguyên tố §fđộc lập§7, chỉ chiếm §f1 ô§7.");
            out.add("§cKết hợp sẽ MẤT cả hai nguyên tố gốc.");
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

    /** Dòng mô tả nội tại "khi máu thấp" của hợp thể (đọc từ mục guard trong config). */
    private void guardLine(List<String> passives, Element e, String what) {
        double thr = n(e, "guard.health-threshold-percent");
        if (thr <= 0) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Khi máu dưới ").append(f(thr)).append("%: ").append(what).append(", giảm ")
                .append(f(n(e, "guard.reduction-percent"))).append("% sát thương nhận vào");
        if (n(e, "guard.speed-percent") > 0) sb.append(", +").append(f(n(e, "guard.speed-percent"))).append("% tốc độ di chuyển");
        if (n(e, "guard.damage-percent") > 0) sb.append(", +").append(f(n(e, "guard.damage-percent"))).append("% sát thương");
        sb.append(" trong ").append(f(n(e, "guard.seconds"))).append("s (hồi ").append(f(n(e, "guard.cooldown"))).append("s)");
        passives.add(sb.toString());
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
