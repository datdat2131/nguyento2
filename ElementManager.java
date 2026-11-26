package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Quản lý sở hữu / trang bị nguyên tố, chỉ số (buff) và hiệu ứng bay quanh người chơi. */
public class ElementManager {

    /** Số ô trang bị mặc định; mở khóa nhiệm vụ / admin cấp được thêm 1 ô (tối đa MAX_SLOTS). */
    public static final int BASE_SLOTS = 3;
    public static final int MAX_SLOTS = 4;

    public enum EquipResult { OK, NOT_OWNED, ALREADY, FULL }

    public enum FuseResult { OK, NOT_FUSION, ALREADY, MISSING, NO_LEVELS }

    public enum UnfuseResult { OK, NOT_FUSION, NOT_OWNED, NO_SCROLL, NO_LEVELS }

    static class Orbit {
        final Element element;
        final ItemDisplay display;
        Location lastSent;
        /** Đang bay đi chiến đấu / hộ vệ: vòng quay không điều khiển khối này cho tới khi nó về. */
        long busyUntil = 0;
        long nextAttack = 0;
        long nextKey = 0;
        long nextParticle = 0;
        long nextDust = 0;
        long nextRing = 0;

        Orbit(Element element, ItemDisplay display) {
            this.element = element;
            this.display = display;
        }
    }

    /** UUID các modifier mà bản cũ của plugin từng gắn lên người chơi (dùng để dọn buff bị kẹt). */
    private static final Set<UUID> LEGACY_IDS = new HashSet<>();

    static {
        String[] stats = {"hp", "damage", "speed", "attackspeed", "armor"};
        for (Element e : Element.values()) {
            for (String st : stats) {
                LEGACY_IDS.add(UUID.nameUUIDFromBytes(
                        ("nguyento:" + e.getId() + ":" + st).getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    private final NguyenToPlugin plugin;
    private final Settings settings;
    private final NamespacedKey ownedKey;
    private final NamespacedKey equippedKey;
    private final NamespacedKey scrollKey;
    private final NamespacedKey unfuseKey;
    private final NamespacedKey statFlagKey;
    private final NamespacedKey extraSlotKey;
    private QuestManager quest;
    private boolean warnedStats = false;
    private boolean warnedGeneral = false;

    private final Map<UUID, List<Element>> equipped = new HashMap<>();
    private final Map<UUID, List<Orbit>> orbits = new HashMap<>();

    /** Trạng thái xoay của vòng nguyên tố (pha quay và độ nở), cộng dồn theo thời gian để Giác Ngộ đổi tốc độ mượt. */
    private static final class RingState {
        double phase = 0.0;
        double scale = 1.0;
    }

    private final Map<UUID, RingState> ringStates = new HashMap<>();
    private OrbBattle battle;

    public void setBattle(OrbBattle battle) {
        this.battle = battle;
    }

    private RingState ringState(Player p) {
        return ringStates.computeIfAbsent(p.getUniqueId(), k -> new RingState());
    }

    List<Orbit> orbitsOf(Player p) {
        return orbits.get(p.getUniqueId());
    }

    long tickNow() {
        return tick;
    }

    int teleportInterval() {
        return interval;
    }

    int orbitCount(Player p) {
        List<Orbit> l = orbits.get(p.getUniqueId());
        return l == null ? 0 : l.size();
    }

    int indexOf(Player p, Orbit o) {
        List<Orbit> l = orbits.get(p.getUniqueId());
        return l == null ? -1 : l.indexOf(o);
    }

    /** Vị trí chuẩn hiện tại của khối thứ 'index' trên vòng (để khối bay về). */
    Location homeOf(Player p, int index, int n) {
        return anchor(p.getLocation(), p.getHeight(), index, n, ringState(p));
    }

    /** Hệ số sát thương gây ra từ buff tạm thời (khiên hợp thể) + Giác Ngộ. */
    public double outgoingMultiplier(Player a) {
        double up = 0;
        UUID id = a.getUniqueId();
        for (Element el : getEquipped(a)) up += marks.value(id, Marks.dmgUp(el));
        if (marks.has(id, Marks.AWAKEN)) up += settings.awakenDamagePercent() / 100.0;
        return 1.0 + up;
    }
    private long tick = 0;
    private int statTimer = 0;
    private SkillItems skillItems;
    private final Random rnd = new Random();
    private BukkitTask task;
    private int interval = 2;
    private boolean particlesOn = true;
    private double density = 0.6;
    private float viewRange = 0.5f;
    // kiểu nguyên tố bay quanh người (config: orbit.*)
    private boolean ringStyle = true;   // vòng tròn quay sau lưng (kiểu Cầu Lục Đạo)
    private boolean backStyle = false;  // hình cánh quạt sau lưng
    private double ringRadius = 1.15;
    private double ringSpeed = 1.3 / 20.0; // rad mỗi tick
    private double ringDist = 0.8;
    private double backDist = 0.95;
    private double spreadRad = Math.toRadians(50);
    private double heightFactor = 1.0;
    private double sizeMul = 1.0;
    private boolean ringOn = true;

    private final Marks marks;
    /** Các bộ cộng hưởng đang có hiệu lực của từng người chơi (để chỉ thông báo khi có thay đổi). */
    private final Map<UUID, Set<String>> activeSetIds = new HashMap<>();

    public ElementManager(NguyenToPlugin plugin, Settings settings, Marks marks) {
        this.plugin = plugin;
        this.settings = settings;
        this.marks = marks;
        this.ownedKey = new NamespacedKey(plugin, "owned");
        this.equippedKey = new NamespacedKey(plugin, "equipped");
        this.scrollKey = new NamespacedKey(plugin, "scroll");
        this.unfuseKey = new NamespacedKey(plugin, "unfuse_scroll");
        this.statFlagKey = new NamespacedKey(plugin, "statflag");
        this.extraSlotKey = new NamespacedKey(plugin, "extra_slot");
    }

    /** Chạy một bước mà không để lỗi ở bước đó làm hỏng cả quá trình trang bị. */
    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            if (!warnedGeneral) {
                warnedGeneral = true;
                plugin.getLogger().warning("Loi khi xu ly nguyen to (" + Bukkit.getVersion() + "): " + t);
                t.printStackTrace();
            }
        }
    }

    public void setSkillItems(SkillItems skillItems) {
        this.skillItems = skillItems;
    }

    public void setQuest(QuestManager quest) {
        this.quest = quest;
    }

    /** Người chơi đã mở khóa ô nguyên tố thứ 4 chưa (nhiệm vụ hoặc admin cấp). */
    public boolean hasExtraSlot(Player p) {
        return p.getPersistentDataContainer().has(extraSlotKey, PersistentDataType.INTEGER);
    }

    /** Số ô trang bị hiện có của người chơi: 3, hoặc 4 nếu đã mở khóa (và tính năng được bật trong config). */
    public int maxSlots(Player p) {
        return BASE_SLOTS + (settings.extraSlotEnabled() && hasExtraSlot(p) ? 1 : 0);
    }

    /** Cấp / thu hồi ô thứ 4. Thu hồi thì nguyên tố ở ô cuối bị gỡ ra nếu đang vượt số ô. */
    public void setExtraSlot(Player p, boolean on) {
        if (on) {
            p.getPersistentDataContainer().set(extraSlotKey, PersistentDataType.INTEGER, 1);
        } else {
            p.getPersistentDataContainer().remove(extraSlotKey);
        }
        List<Element> list = equipped.get(p.getUniqueId());
        if (list == null) return;
        boolean trimmed = false;
        while (list.size() > maxSlots(p)) {
            list.remove(list.size() - 1);
            trimmed = true;
        }
        if (trimmed) refreshAfterChange(p);
    }

    private void syncSkillItem(Player p) {
        if (skillItems != null) skillItems.sync(p);
    }

    // ------------------------------------------------------------------
    //  Dữ liệu (lưu trong PersistentDataContainer của người chơi)
    // ------------------------------------------------------------------

    private List<Element> parse(String s) {
        List<Element> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) {
            Element e = Element.fromId(part);
            if (e != null && !out.contains(e)) out.add(e);
        }
        return out;
    }

    private String join(List<Element> list) {
        return list.stream().map(Element::getId).collect(Collectors.joining(","));
    }

    public List<Element> getOwned(Player p) {
        return parse(p.getPersistentDataContainer().get(ownedKey, PersistentDataType.STRING));
    }

    public boolean owns(Player p, Element e) {
        return getOwned(p).contains(e);
    }

    /** @return true nếu là nguyên tố mới, false nếu đã sở hữu từ trước */
    public boolean grant(Player p, Element e) {
        List<Element> owned = getOwned(p);
        if (owned.contains(e)) return false;
        owned.add(e);
        p.getPersistentDataContainer().set(ownedKey, PersistentDataType.STRING, join(owned));
        if (quest != null) quest.refreshOwned(p);
        return true;
    }

    public List<Element> getEquipped(Player p) {
        return new ArrayList<>(equipped.getOrDefault(p.getUniqueId(), Collections.emptyList()));
    }

    /** Người chơi có đang trang bị nguyên tố có chiêu (Bóng Tối / Ánh Sáng / Rồng) không. */
    public boolean hasSpecial(Player p) {
        for (Element e : getEquipped(p)) {
            if (e.hasSkill()) return true;
        }
        return false;
    }

    /** Có đang trang bị nguyên tố e không. */
    public boolean has(Player p, Element e) {
        List<Element> l = equipped.get(p.getUniqueId());
        return l != null && l.contains(e);
    }

    private void saveEquipped(Player p) {
        List<Element> list = equipped.getOrDefault(p.getUniqueId(), new ArrayList<>());
        p.getPersistentDataContainer().set(equippedKey, PersistentDataType.STRING, join(list));
    }

    public EquipResult equip(Player p, Element e) {
        if (!owns(p, e)) return EquipResult.NOT_OWNED;
        List<Element> list = equipped.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
        if (list.contains(e)) return EquipResult.ALREADY;
        if (list.size() >= maxSlots(p)) return EquipResult.FULL;
        list.add(e);
        refreshAfterChange(p);
        return EquipResult.OK;
    }

    public void unequip(Player p, Element e) {
        List<Element> list = equipped.get(p.getUniqueId());
        if (list == null || !list.remove(e)) return;
        refreshAfterChange(p);
    }

    public void unequipAll(Player p) {
        List<Element> list = equipped.get(p.getUniqueId());
        if (list == null || list.isEmpty()) return;
        list.clear();
        refreshAfterChange(p);
    }

    /** Sau mỗi lần đổi trang bị: áp lại buff, dựng lại nguyên tố bay, đồng bộ vật phẩm kỹ năng. Mỗi bước độc lập. */
    private void refreshAfterChange(Player p) {
        if (settings.keepEquipped()) safe(() -> saveEquipped(p));
        safe(() -> notifySets(p));
        safe(() -> applyStats(p));
        safe(() -> rebuild(p));
        safe(() -> syncSkillItem(p));
    }

    /**
     * Vào game: khôi phục nguyên tố đang trang bị từ lần trước (nếu general.keep-equipped-on-rejoin bật),
     * chỉ giữ những nguyên tố người chơi vẫn còn sở hữu. Dọn sạch buff còn sót từ bản plugin cũ.
     */
    public void load(Player p) {
        safe(() -> purgeLegacyModifiers(p));
        List<Element> restored = new ArrayList<>();
        if (settings.keepEquipped()) {
            List<Element> owned = getOwned(p);
            for (Element e : parse(p.getPersistentDataContainer().get(equippedKey, PersistentDataType.STRING))) {
                if (owned.contains(e) && restored.size() < maxSlots(p)) restored.add(e);
            }
        } else {
            p.getPersistentDataContainer().remove(equippedKey);
        }
        equipped.put(p.getUniqueId(), restored);
        // đặt sẵn bộ cộng hưởng hiện có để lần khôi phục này không bị thông báo lại
        Set<String> ids = new LinkedHashSet<>();
        for (SetBonus sb : settings.activeSets(restored)) ids.add(sb.id);
        activeSetIds.put(p.getUniqueId(), ids);

        safe(() -> despawn(p));
        if (restored.isEmpty()) {
            safe(() -> syncStats(p, Collections.emptyList()));
            safe(() -> syncSkillItem(p));
            return;
        }
        refreshAfterChange(p);
        // làm lại lần nữa sau một lúc: lúc mới vào, thế giới / kho đồ có thể chưa sẵn sàng hẳn
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            List<Element> cur = equipped.get(p.getUniqueId());
            if (cur == null || cur.isEmpty()) return;
            refreshAfterChange(p);
        }, 20L);
    }

    /** Thoát game: lưu nguyên tố đang trang bị (nếu bật), rồi gỡ buff / nguyên tố bay / vật phẩm kỹ năng. */
    public void unload(Player p) {
        if (settings.keepEquipped()) {
            saveEquipped(p);
        } else {
            p.getPersistentDataContainer().remove(equippedKey);
        }
        safe(() -> {
            if (skillItems != null) skillItems.removeAll(p);
        });
        safe(() -> despawn(p));
        List<Element> list = equipped.get(p.getUniqueId());
        if (list != null) list.clear();
        safe(() -> syncStats(p, Collections.emptyList()));
        equipped.remove(p.getUniqueId());
        activeSetIds.remove(p.getUniqueId());
        ringStates.remove(p.getUniqueId());
    }

    /** Đặt lại hoàn toàn một người chơi: bỏ trang bị, xóa buff kẹt, chỉ số về mặc định. */
    public void resetPlayer(Player p) {
        safe(() -> purgeLegacyModifiers(p));
        List<Element> list = equipped.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
        list.clear();
        activeSetIds.remove(p.getUniqueId());
        p.getPersistentDataContainer().remove(equippedKey);
        safe(() -> {
            setBase(p, Attrs.MAX_HEALTH, DEF_HEALTH);
            setBase(p, Attrs.ATTACK_DAMAGE, DEF_DAMAGE);
            setBase(p, Attrs.MOVEMENT_SPEED, DEF_SPEED);
            setBase(p, Attrs.ATTACK_SPEED, DEF_ATTACK_SPEED);
            setBase(p, Attrs.ARMOR, DEF_ARMOR);
            p.getPersistentDataContainer().remove(statFlagKey);
        });
        safe(() -> rebuild(p));
        safe(() -> syncSkillItem(p));
    }

    /** Gỡ các AttributeModifier do bản plugin cũ để lại (nguyên nhân buff "vĩnh viễn không xóa"). */
    private void purgeLegacyModifiers(Player p) {
        Attribute[] attrs = {Attrs.MAX_HEALTH, Attrs.ATTACK_DAMAGE, Attrs.MOVEMENT_SPEED,
                Attrs.ATTACK_SPEED, Attrs.ARMOR};
        for (Attribute a : attrs) {
            if (a == null) continue;
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                boolean ours = false;
                try {
                    String n = m.getName();
                    ours = n != null && n.startsWith("nguyento");
                } catch (Throwable ignored) {
                    // bỏ qua
                }
                if (!ours) {
                    try {
                        ours = LEGACY_IDS.contains(m.getUniqueId());
                    } catch (Throwable ignored) {
                        // bỏ qua
                    }
                }
                if (ours) inst.removeModifier(m);
            }
        }
    }

    /** Thông báo khi bộ cộng hưởng được kích hoạt / mất đi (chỉ khi có thay đổi). */
    private void notifySets(Player p) {
        UUID id = p.getUniqueId();
        List<SetBonus> now = settings.activeSets(getEquipped(p));
        Set<String> before = activeSetIds.getOrDefault(id, Collections.emptySet());
        Set<String> after = new LinkedHashSet<>();
        for (SetBonus sb : now) {
            after.add(sb.id);
            if (before.contains(sb.id)) continue;
            p.sendMessage(sb.colored() + " §7- cộng hưởng kích hoạt! §8(" + String.join(", ", sb.describe()) + ")");
            Fx.actionBar(p, sb.color + "✦ Cộng hưởng " + sb.name + " ✦");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
            if (Vfx.on()) {
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.1);
            }
        }
        for (String old : before) {
            if (after.contains(old)) continue;
            for (SetBonus sb : settings.sets()) {
                if (sb.id.equals(old)) {
                    p.sendMessage("§7Mất cộng hưởng " + sb.colored() + "§7.");
                    break;
                }
            }
        }
        activeSetIds.put(id, after);
    }

    /** Gọi sau khi /nguyento reload để áp lại chỉ số mới. */
    public void reloadStats() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (equipped.containsKey(p.getUniqueId())) {
                safe(() -> applyStats(p));
                safe(() -> rebuild(p));
            }
        }
    }

    /** Gọi sau khi hồi sinh / đổi thế giới: Minecraft có thể đã xóa buff, áp lại + dựng lại hiển thị. */
    public void refreshLater(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || !equipped.containsKey(p.getUniqueId())) return;
            refreshAfterChange(p);
        });
    }

    private double val(Player p, Attribute a) {
        if (a == null) return 0;
        AttributeInstance inst = p.getAttribute(a);
        return inst == null ? 0 : inst.getValue();
    }

    /** Các dòng hiển thị chỉ số thật đang có (dùng cho /nguyento stats). */
    public List<String> statLines(Player p) {
        List<String> out = new ArrayList<>();
        out.add("§6§l✦ Chỉ số hiện tại ✦");
        List<Element> eq = getEquipped(p);
        StringBuilder sb = new StringBuilder();
        for (Element e : eq) {
            if (sb.length() > 0) sb.append("§7, ");
            sb.append(e.colored());
        }
        out.add("§7Đang trang bị: " + (eq.isEmpty() ? "§8(chưa có)" : sb.toString()));
        out.add("§7Máu tối đa: §f" + Settings.f(val(p, Attrs.MAX_HEALTH)));
        out.add("§7Sát thương cận chiến: §f" + Settings.f(val(p, Attrs.ATTACK_DAMAGE)));
        out.add("§7Tốc độ di chuyển: §f" + Settings.f(val(p, Attrs.MOVEMENT_SPEED) * 1000) + "%");
        out.add("§7Tốc độ đánh: §f" + Settings.f(val(p, Attrs.ATTACK_SPEED)));
        out.add("§7Giáp: §f" + Settings.f(val(p, Attrs.ARMOR)) + " §8(buff % giáp chỉ có tác dụng khi bạn đang mặc giáp)");
        return out;
    }

    public double maxHealth(LivingEntity le) {
        if (Attrs.MAX_HEALTH == null) return 20.0;
        AttributeInstance a = le.getAttribute(Attrs.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    // ------------------------------------------------------------------
    //  Chỉ số (buff) qua Attribute
    // ------------------------------------------------------------------

    public void applyStats(Player p) {
        syncStats(p, equipped.getOrDefault(p.getUniqueId(), Collections.emptyList()));
    }

    // Giá trị gốc của người chơi trong Minecraft
    private static final double DEF_HEALTH = 20.0;
    private static final double DEF_DAMAGE = 1.0;
    private static final double DEF_SPEED = 0.1;
    private static final double DEF_ATTACK_SPEED = 4.0;
    private static final double DEF_ARMOR = 0.0;

    /**
     * Áp chỉ số bằng cách đặt giá trị gốc (base) của attribute = giá trị mặc định + tổng buff.
     * Cách này không dùng AttributeModifier nên chạy ổn trên mọi phiên bản Paper/Spigot.
     * Gọi nhiều lần vẫn an toàn (chỉ ghi khi giá trị thay đổi).
     */
    private void syncStats(Player p, List<Element> eq) {
        try {
            boolean any = !eq.isEmpty();
            boolean flagged = p.getPersistentDataContainer().has(statFlagKey, PersistentDataType.BYTE);
            if (!any && !flagged) return; // chưa từng đụng tới chỉ số của người này

            double hp = 0, dmg = 0, spd = 0, aspd = 0, armor = 0;
            for (Element e : eq) {
                hp += settings.n(e, "max-health");
                dmg += settings.n(e, "damage");
                spd += settings.n(e, "speed-percent") / 100.0;
                aspd += settings.n(e, "attack-speed-percent") / 100.0;
                armor += settings.n(e, "armor-percent") / 100.0;
            }
            // buff tốc độ di chuyển tạm thời (khiên / nội tại của hợp thể) - chỉ khi còn trang bị nguyên tố
            if (any) {
                UUID id = p.getUniqueId();
                for (Element e : eq) spd += marks.value(id, Marks.speed(e));
                if (marks.has(id, Marks.AWAKEN)) spd += settings.awakenSpeedPercent() / 100.0;
                // bộ cộng hưởng (config: sets)
                for (SetBonus sb : settings.activeSets(eq)) {
                    hp += sb.maxHealth;
                    dmg += sb.damage;
                    spd += sb.speedPercent / 100.0;
                    aspd += sb.attackSpeedPercent / 100.0;
                    armor += sb.armorPercent / 100.0;
                }
            }

            setBase(p, Attrs.MAX_HEALTH, Math.max(1.0, DEF_HEALTH + hp));
            setBase(p, Attrs.ATTACK_DAMAGE, Math.max(0.0, DEF_DAMAGE + dmg));
            setBase(p, Attrs.MOVEMENT_SPEED, Math.max(0.0, DEF_SPEED * (1 + spd)));
            setBase(p, Attrs.ATTACK_SPEED, Math.max(0.1, DEF_ATTACK_SPEED * (1 + aspd)));

            // giáp: +% của giáp đang mặc (giáp từ đồ = tổng - base hiện tại)
            if (Attrs.ARMOR != null) {
                AttributeInstance ai = p.getAttribute(Attrs.ARMOR);
                if (ai != null) {
                    double fromGear = Math.max(0.0, ai.getValue() - ai.getBaseValue());
                    setBase(p, Attrs.ARMOR, DEF_ARMOR + fromGear * armor);
                }
            }

            if (any && !flagged) {
                p.getPersistentDataContainer().set(statFlagKey, PersistentDataType.BYTE, (byte) 1);
            } else if (!any) {
                p.getPersistentDataContainer().remove(statFlagKey);
            }
        } catch (Throwable t) {
            if (!warnedStats) {
                warnedStats = true;
                plugin.getLogger().warning("Khong ap dung duoc buff chi so (" + Bukkit.getVersion() + "): " + t);
                t.printStackTrace();
            }
        }
    }

    private void setBase(Player p, Attribute attr, double value) {
        if (attr == null) return;
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        if (Math.abs(inst.getBaseValue() - value) > 1e-9) inst.setBaseValue(value);
    }

    // ------------------------------------------------------------------
    //  Fusion: kết hợp 2 nguyên tố thành hợp thể
    // ------------------------------------------------------------------

    /**
     * Kết hợp: cần sở hữu cả 2 nguyên tố thành phần (+ tốn cấp độ nếu config có đặt).
     * Khi thành công người chơi MẤT hai nguyên tố gốc (và nếu đang trang bị thì bị gỡ ra) và nhận hợp thể.
     */
    public FuseResult fuse(Player p, Element f) {
        if (!f.isFusion()) return FuseResult.NOT_FUSION;
        List<Element> parts = f.getParts();
        if (parts.size() != 2) return FuseResult.NOT_FUSION;
        List<Element> owned = getOwned(p);
        if (owned.contains(f)) return FuseResult.ALREADY;
        for (Element part : parts) {
            if (!owned.contains(part)) return FuseResult.MISSING;
        }
        int cost = settings.fusionCostLevels();
        boolean pay = cost > 0 && p.getGameMode() != GameMode.CREATIVE;
        if (pay && p.getLevel() < cost) return FuseResult.NO_LEVELS;
        if (pay) p.setLevel(p.getLevel() - cost);

        owned.removeAll(parts);
        owned.add(f);
        p.getPersistentDataContainer().set(ownedKey, PersistentDataType.STRING, join(owned));

        List<Element> eq = equipped.get(p.getUniqueId());
        if (eq != null && eq.removeAll(parts)) refreshAfterChange(p);
        if (quest != null) quest.onFuse(p);
        return FuseResult.OK;
    }

    /** Người chơi đủ điều kiện (sở hữu cả 2 thành phần) để kết hợp f chưa. */
    public boolean canFuse(Player p, Element f) {
        if (!f.isFusion()) return false;
        for (Element part : f.getParts()) {
            if (!owns(p, part)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Cuộn giấy
    // ------------------------------------------------------------------

    public ItemStack createScroll(Element e, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(e.getColor() + "✦ Cuộn Giấy Nguyên Tố " + e.getName() + " ✦");
        List<String> lore = new ArrayList<>();
        lore.add(e.rarityLine());
        lore.add("");
        lore.addAll(settings.describe(e));
        lore.add("");
        lore.add("§7Nhấn §fchuột phải §7để lĩnh ngộ nguyên tố");
        lore.add("§8Dùng một lần • Ai cũng có thể sở hữu");
        meta.setLore(lore);
        addGlow(meta);
        meta.getPersistentDataContainer().set(scrollKey, PersistentDataType.STRING, e.getId());
        item.setItemMeta(meta);
        return item;
    }

    /** Cuộn Tách Hợp Thể: trả một hợp thể về 2 nguyên tố gốc (dùng trong menu tách). */
    public ItemStack createUnfuseScroll(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d✦ Cuộn Tách Hợp Thể ✦");
        List<String> lore = new ArrayList<>();
        lore.add("§d§l✦ VẬT PHẨM ĐẶC BIỆT ✦");
        lore.add("");
        lore.add("§7Nhấn §fchuột phải §7để mở menu tách.");
        lore.add("§7Một hợp thể sẽ được §ftrả về hai nguyên tố gốc§7.");
        lore.add("§8Mỗi lần tách tiêu hao 1 cuộn");
        meta.setLore(lore);
        addGlow(meta);
        meta.getPersistentDataContainer().set(unfuseKey, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isUnfuseScroll(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(unfuseKey, PersistentDataType.INTEGER);
    }

    public int countUnfuseScrolls(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isUnfuseScroll(it)) n += it.getAmount();
        }
        return n;
    }

    private boolean consumeUnfuseScroll(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!isUnfuseScroll(it)) continue;
            if (it.getAmount() <= 1) {
                p.getInventory().setItem(i, null);
            } else {
                it.setAmount(it.getAmount() - 1);
                p.getInventory().setItem(i, it);
            }
            return true;
        }
        return false;
    }

    /**
     * Tách hợp thể: tiêu hao 1 Cuộn Tách Hợp Thể (+ cấp độ nếu config có đặt), mất hợp thể, nhận lại 2 nguyên tố gốc
     * (nguyên tố gốc nào đã có sẵn thì không nhận thêm bản thứ hai).
     */
    public UnfuseResult unfuse(Player p, Element f) {
        if (!f.isFusion()) return UnfuseResult.NOT_FUSION;
        List<Element> owned = getOwned(p);
        if (!owned.contains(f)) return UnfuseResult.NOT_OWNED;
        if (countUnfuseScrolls(p) <= 0) return UnfuseResult.NO_SCROLL;
        int cost = settings.unfuseCostLevels();
        boolean pay = cost > 0 && p.getGameMode() != GameMode.CREATIVE;
        if (pay && p.getLevel() < cost) return UnfuseResult.NO_LEVELS;
        if (!consumeUnfuseScroll(p)) return UnfuseResult.NO_SCROLL;
        if (pay) p.setLevel(p.getLevel() - cost);

        owned.remove(f);
        for (Element part : f.getParts()) {
            if (!owned.contains(part)) owned.add(part);
        }
        p.getPersistentDataContainer().set(ownedKey, PersistentDataType.STRING, join(owned));

        List<Element> eq = equipped.get(p.getUniqueId());
        if (eq != null && eq.remove(f)) refreshAfterChange(p);
        return UnfuseResult.OK;
    }

    // ------------------------------------------------------------------
    //  Vòng tương khắc
    // ------------------------------------------------------------------

    /** Các loại nguyên tố gốc đang mang (hợp thể bung ra thành 2 thành phần). */
    private static Set<Element> typesOf(List<Element> list) {
        Set<Element> out = java.util.EnumSet.noneOf(Element.class);
        for (Element e : list) {
            if (e.isFusion()) {
                out.addAll(e.getParts());
            } else {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Hệ số sát thương theo tương khắc khi 'attacker' đánh 'target': có nguyên tố khắc mục tiêu thì cộng thêm
     * counter.bonus-percent, bị mục tiêu khắc ngược thì trừ counter.penalty-percent. Mục tiêu là người chơi dùng
     * nguyên tố đang mang, là quái dùng nguyên tố khai báo ở counter.mobs. Không có thông tin thì trả 1.0.
     */
    public double counterMultiplier(Player attacker, LivingEntity target) {
        if (!settings.counterEnabled()) return 1.0;
        Set<Element> mine = typesOf(getEquipped(attacker));
        if (mine.isEmpty()) return 1.0;
        Set<Element> theirs;
        if (target instanceof Player) {
            theirs = typesOf(getEquipped((Player) target));
        } else {
            Element natural = settings.mobElement(target.getType().name());
            if (natural == null) return 1.0;
            theirs = java.util.EnumSet.of(natural);
        }
        if (theirs.isEmpty()) return 1.0;
        boolean advantage = false;
        boolean disadvantage = false;
        for (Element a : mine) {
            for (Element b : theirs) {
                if (settings.counters(a, b)) advantage = true;
                if (settings.counters(b, a)) disadvantage = true;
            }
        }
        double m = 1.0;
        if (advantage) m *= 1.0 + settings.counterBonusPercent() / 100.0;
        if (disadvantage) m *= Math.max(0.0, 1.0 - settings.counterPenaltyPercent() / 100.0);
        return m;
    }

    /** Có đang giữ nguyên trang bị khi thoát vào lại không (để thông báo cho đúng). */
    public boolean keepsEquipped() {
        return settings.keepEquipped();
    }

    public Element readScroll(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(scrollKey, PersistentDataType.STRING);
        return Element.fromId(id);
    }

    /** Làm item phát sáng (enchant glow) mà không hiện dòng enchant. */
    public static void addGlow(ItemMeta meta) {
        Enchantment glow = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (glow != null) meta.addEnchant(glow, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    private ItemStack displayItem(Element e) {
        ItemStack it = new ItemStack(e.getMaterial());
        if (e.isSpecial()) {
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                addGlow(m);
                it.setItemMeta(m);
            }
        }
        return it;
    }

    // ------------------------------------------------------------------
    //  Hiển thị nguyên tố bay quanh người
    // ------------------------------------------------------------------

    /**
     * Số tick giữa 2 mốc xoay / nhấp nhô. Client tự nội suy mượt giữa 2 mốc nên
     * không cần gửi gói tin mỗi tick (giảm lag rõ rệt so với cập nhật liên tục).
     */
    private static final int KEY_STEP = 10;

    public void startTask() {
        if (task != null) task.cancel();
        interval = (int) Math.max(1, Math.min(10, settings.perf("orbit-update-ticks")));
        particlesOn = settings.pb("particles");
        density = Math.max(0.0, Math.min(2.0, settings.perf("particle-density")));
        viewRange = (float) (Math.max(8.0, settings.perf("view-range-blocks")) / 64.0);
        String style = plugin.getConfig().getString("orbit.style", "ring").toLowerCase(java.util.Locale.ROOT);
        backStyle = style.equals("back");
        ringStyle = !style.equals("back") && !style.equals("classic");
        ringRadius = Math.max(0.5, Math.min(3.0, plugin.getConfig().getDouble("orbit.ring-radius", 1.15)));
        ringSpeed = Math.max(-6.0, Math.min(6.0, plugin.getConfig().getDouble("orbit.ring-speed", 1.3))) / 20.0;
        ringDist = Math.max(0.2, Math.min(3.0, plugin.getConfig().getDouble("orbit.ring-distance", 0.8)));
        backDist = Math.max(0.3, Math.min(3.0, plugin.getConfig().getDouble("orbit.back-distance", 0.95)));
        spreadRad = Math.toRadians(Math.max(0.0, Math.min(90.0, plugin.getConfig().getDouble("orbit.spread-degrees", 50.0))));
        heightFactor = Math.max(0.3, Math.min(1.6, plugin.getConfig().getDouble("orbit.height", 1.0)));
        sizeMul = Math.max(0.2, Math.min(3.0, plugin.getConfig().getDouble("orbit.size", 1.0)));
        ringOn = plugin.getConfig().getBoolean("orbit.ring", true);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, interval);
    }

    private void tickAll() {
        tick += interval;
        statTimer += interval;
        boolean refreshStats = statTimer >= 20;
        if (refreshStats) statTimer = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                tickPlayer(p, refreshStats);
            } catch (Throwable t) {
                if (!warnedGeneral) {
                    warnedGeneral = true;
                    plugin.getLogger().warning("Loi khi cap nhat nguyen to (" + Bukkit.getVersion() + "): " + t);
                    t.printStackTrace();
                }
            }
        }
    }

    private void tickPlayer(Player p, boolean refreshStats) {
        List<Element> eq = equipped.get(p.getUniqueId());
        if (eq == null || eq.isEmpty()) return;

        // mỗi ~1 giây kiểm tra lại buff (rất nhẹ: chỉ so sánh, không đổi nếu đã đúng)
        if (refreshStats && !p.isDead()) applyStats(p);

        List<Orbit> list = orbits.get(p.getUniqueId());
        if (!shouldShow(p)) {
            if (list != null) despawn(p);
            return;
        }
        if (list == null || !isValid(p, list, eq.size())) {
            rebuild(p);
            return;
        }
        update(p, list);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (List<Orbit> list : orbits.values()) {
            for (Orbit o : list) o.display.remove();
        }
        orbits.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (skillItems != null) safe(() -> skillItems.removeAll(p));
            if (settings.keepEquipped()) {
                saveEquipped(p); // tắt server / reload plugin: vẫn giữ trang bị để lần sau tự khôi phục
            } else {
                p.getPersistentDataContainer().remove(equippedKey);
            }
            safe(() -> syncStats(p, Collections.emptyList()));
        }
        equipped.clear();
        activeSetIds.clear();
    }

    private boolean shouldShow(Player p) {
        return p.isOnline() && !p.isDead()
                && p.getGameMode() != GameMode.SPECTATOR
                && !p.isInvisible();
    }

    private boolean isValid(Player p, List<Orbit> list, int expected) {
        if (list.size() != expected) return false;
        Location pl = p.getLocation();
        for (Orbit o : list) {
            if (o.busyUntil > tick) continue; // đang bay đi chiến đấu / hộ vệ
            if (!o.display.isValid()) return false;
            Location last = o.lastSent;
            if (last == null || last.getWorld() != pl.getWorld() || last.distanceSquared(pl) > 144) return false;
        }
        return true;
    }

    private void rebuild(Player p) {
        despawn(p);
        List<Element> eq = equipped.get(p.getUniqueId());
        if (eq == null || eq.isEmpty() || !shouldShow(p)) return;

        Location pl = p.getLocation();
        double height = p.getHeight();
        List<Orbit> list = new ArrayList<>();
        for (int i = 0; i < eq.size(); i++) {
            Element el = eq.get(i);
            Location at = anchor(pl, height, i, eq.size(), ringState(p));
            ItemDisplay d = pl.getWorld().spawn(at, ItemDisplay.class);
            d.setItemStack(displayItem(el));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setGravity(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(viewRange);
            d.setTeleportDuration(interval);
            d.setTransformation(transform(i, eq.size(), el, tick));
            Orbit o = new Orbit(el, d);
            o.lastSent = at;
            o.nextKey = tick;
            list.add(o);
        }
        orbits.put(p.getUniqueId(), list);
    }

    private void despawn(Player p) {
        List<Orbit> list = orbits.remove(p.getUniqueId());
        if (list == null) return;
        for (Orbit o : list) o.display.remove();
    }

    private void update(Player p, List<Orbit> list) {
        Location pl = p.getLocation();
        double height = p.getHeight();

        // vòng quay: cộng dồn pha; Giác Ngộ làm vòng quay nhanh hơn và nở to dần
        RingState rs = ringState(p);
        boolean awake = marks.has(p.getUniqueId(), Marks.AWAKEN);
        rs.phase += ringSpeed * interval * (awake ? settings.awakenRingSpeedMultiplier() : 1.0);
        double wantScale = awake ? settings.awakenRingRadiusMultiplier() : 1.0;
        rs.scale += (wantScale - rs.scale) * 0.2;

        for (int i = 0; i < list.size(); i++) {
            Orbit o = list.get(i);
            if (o.busyUntil > tick) continue; // khối này đang bay đi chiến đấu / hộ vệ
            Location target = anchor(pl, height, i, list.size(), rs);

            // mốc xoay + nhấp nhô kế tiếp (client tự nội suy giữa các mốc)
            if (tick + interval >= o.nextKey) {
                long when = tick + KEY_STEP;
                o.display.setInterpolationDuration(KEY_STEP);
                o.display.setInterpolationDelay(0);
                o.display.setTransformation(transform(i, list.size(), o.element, when));
                o.nextKey = when;
            }

            // chỉ gửi vị trí khi người chơi thực sự di chuyển / quay đầu
            Location last = o.lastSent;
            if (last == null || last.getWorld() != target.getWorld()
                    || last.distanceSquared(target) > 0.0004
                    || Math.abs(yawDiff(target.getYaw(), last.getYaw())) > 1.5f) {
                o.display.teleport(target);
                o.lastSent = target;
            }

            particles(o, target);
        }
        backRing(pl, height, list, rs);
        if (battle != null) battle.tick(p, list, tick);
    }

    private static float yawDiff(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private int scaled(double base) {
        double v = base * density;
        int n = (int) v;
        if (rnd.nextDouble() < v - n) n++;
        return n;
    }

    private void particles(Orbit o, Location loc) {
        if (!particlesOn || density <= 0) return;
        World w = loc.getWorld();
        Element el = o.element;

        if (tick >= o.nextParticle) {
            o.nextParticle = tick + (el.isSpecial() ? 4 : 6);
            int n = scaled(el.isSpecial() ? 2 : 1);
            if (n > 0) w.spawnParticle(el.getParticle(), loc, n, 0.15, 0.15, 0.15, 0.01);
            if (el.isFusion()) {
                // hợp thể: thêm hạt của cả 2 nguyên tố thành phần
                for (Element part : el.getParts()) {
                    int m = scaled(1);
                    if (m > 0) w.spawnParticle(part.getParticle(), loc, m, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
        if (el.isSpecial() && tick >= o.nextDust) {
            o.nextDust = tick + 12;
            int n = scaled(3);
            if (n > 0) {
                Color c = Vfx.colorOf(el);
                Fx.dust(w, loc, n, 0.25, c, 1.2f);
            }
        }
    }

    /** Làn của nguyên tố trong hình quạt sau lưng: -1 = trái, 0 = giữa, 1 = phải (xếp đối xứng theo số lượng). */
    private static double lane(int slot, int n) {
        if (n <= 1) return 0;
        if (n == 2) return slot == 0 ? -1 : 1;
        if (n == 3) return slot - 1;
        return slot - 1.5; // 4 nguyên tố: -1.5, -0.5, 0.5, 1.5
    }

    /**
     * Vị trí của từng nguyên tố (yaw = hướng người chơi).
     * Kiểu "back": xếp hình cánh quạt PHÍA SAU LƯNG (1 cái ở giữa; 2 cái hai bên; 3 cái trái - giữa - phải, cái giữa cao hơn).
     * Kiểu "classic": ô 0 trên đầu, ô 1-2 lơ lửng hai bên vai.
     */
    private Location anchor(Location pl, double height, int slot, int n, RingState rs) {
        double yaw = Math.toRadians(pl.getYaw());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);

        if (ringStyle) {
            // các nguyên tố nối đuôi nhau bay thành một vòng tròn thẳng đứng quay sau lưng (kiểu Cầu Lục Đạo)
            double ang = rs.phase + slot * 2 * Math.PI / Math.max(1, n);
            double rad = ringRadius * rs.scale;
            double ox = Math.cos(ang) * rad;
            double oy = Math.sin(ang) * rad;
            double lift = ringRadius * (rs.scale - 1.0) * 0.9; // vòng nở to thì nâng lên để không chạm đất
            return new Location(pl.getWorld(),
                    pl.getX() + rx * ox + fx * (-ringDist),
                    pl.getY() + height * 0.9 * heightFactor + lift + oy,
                    pl.getZ() + rz * ox + fz * (-ringDist),
                    pl.getYaw(), 0f);
        }

        double side, y, back;
        if (backStyle) {
            double lane = lane(slot, n);
            double ang = lane * spreadRad;
            side = Math.sin(ang) * backDist;
            back = -Math.cos(ang) * backDist;           // âm = phía sau lưng
            double edge = Math.min(1.0, Math.abs(lane)); // 0 = giữa (cao nhất), 1 = ngoài rìa (thấp hơn)
            y = (height * (0.98 - 0.2 * edge) + (0.2 - 0.1 * edge)) * heightFactor;
        } else if (slot == 0) {
            side = 0;
            y = height + 0.5;
            back = 0;
        } else if (slot == 3) {
            side = 0;
            y = height * 0.9;
            back = -0.7;
        } else {
            side = (slot == 1 ? -1 : 1) * 0.85;
            y = height * 0.8 + 0.15;
            back = -0.2;
        }
        return new Location(pl.getWorld(),
                pl.getX() + rx * side + fx * back,
                pl.getY() + y,
                pl.getZ() + rz * side + fz * back,
                pl.getYaw(), 0f);
    }

    /** Xoay tròn + trôi lơ lửng nhẹ (kiểu back) hoặc xoay + nhấp nhô (kiểu classic). 'when' = tick của mốc đích. */
    private Transformation transform(int slot, int n, Element el, long when) {
        float s;
        Vector3f translation;
        Quaternionf q;
        if (ringStyle) {
            float spin = (float) (when * 0.1 + slot * 1.7);
            q = new Quaternionf().rotateY(spin);
            translation = new Vector3f(0f, 0f, 0f);
            s = 0.9f;
        } else if (backStyle) {
            double lane = lane(slot, n);
            float spin = (float) (when * 0.08 + slot * 1.7);
            q = new Quaternionf().rotateZ((float) (-lane * 0.3)).rotateY(spin);
            double ph = (when + slot * 17) * 0.09;
            translation = new Vector3f((float) (Math.cos(ph) * 0.06), (float) (Math.sin(ph * 1.3) * 0.09),
                    (float) (Math.sin(ph) * 0.06));
            s = 0.9f - 0.15f * (float) Math.min(1.0, Math.abs(lane));
        } else {
            float spin = (float) (when * 0.1 + slot * 1.7);
            float tilt = slot == 0 ? 0f : (slot == 1 ? -0.45f : 0.45f);
            q = new Quaternionf().rotateZ(tilt).rotateX(slot == 0 ? 0f : 0.3f).rotateY(spin);
            float bob = (float) (Math.sin((when + slot * 13) * 0.12) * 0.07);
            translation = new Vector3f(0f, bob, 0f);
            s = slot == 0 ? 0.9f : 0.75f;
        }
        if (el.isSpecial()) s *= 1.25f;
        if (el.isFusion()) s *= 1.1f;
        float custom = (float) settings.n(el, "orbit-scale"); // config: elements.<id>.orbit-scale
        if (custom > 0f) s *= custom;
        s *= (float) sizeMul;                                  // config: orbit.size
        return new Transformation(translation, q, new Vector3f(s, s, s), new Quaternionf());
    }

    /** Vòng ma pháp phát sáng xoay chậm sau lưng, màu theo các nguyên tố đang trang bị (chỉ kiểu back, config orbit.ring). */
    private void backRing(Location pl, double height, List<Orbit> list, RingState rs) {
        if (!(ringStyle || backStyle) || !ringOn || !particlesOn || density <= 0 || list.isEmpty()) return;
        Orbit first = list.get(0);
        if (tick < first.nextRing) return;
        first.nextRing = tick + 6;

        double yaw = Math.toRadians(pl.getYaw());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double radius;
        double cx;
        double cy;
        double cz;
        if (ringStyle) {
            // vòng bụi mờ đúng quỹ đạo của các nguyên tố
            radius = ringRadius * rs.scale;
            cx = pl.getX() + fx * (-ringDist);
            cy = pl.getY() + height * 0.9 * heightFactor + ringRadius * (rs.scale - 1.0) * 0.9;
            cz = pl.getZ() + fz * (-ringDist);
        } else {
            radius = Math.max(0.5, height * 0.52);
            cx = pl.getX() + fx * (-backDist * 0.55);
            cy = pl.getY() + height * 0.62 * heightFactor;
            cz = pl.getZ() + fz * (-backDist * 0.55);
        }
        World w = pl.getWorld();
        int n = Math.max(6, scaled(18));
        double rot = tick * 0.05;
        for (int k = 0; k < n; k++) {
            double a = rot + 2 * Math.PI * k / n;
            double x = cx + rx * Math.cos(a) * radius;
            double y = cy + Math.sin(a) * radius;
            double z = cz + rz * Math.cos(a) * radius;
            Color c = Vfx.colorOf(list.get(k % list.size()).element);
            Fx.dust(w, new Location(w, x, y, z), 1, 0.0, c, 0.9f);
        }
    }
}
