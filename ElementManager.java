package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/** Quản lý sở hữu / trang bị nguyên tố, chỉ số (buff) và hiệu ứng bay quanh người chơi. */
public class ElementManager {

    public static final int MAX_EQUIPPED = 3;
    private static final String MOD_PREFIX = "nguyento:";

    public enum EquipResult { OK, NOT_OWNED, ALREADY, FULL }

    private static final Attribute[] STAT_ATTRIBUTES = {
            Attribute.GENERIC_MAX_HEALTH,
            Attribute.GENERIC_ATTACK_DAMAGE,
            Attribute.GENERIC_MOVEMENT_SPEED,
            Attribute.GENERIC_ATTACK_SPEED,
            Attribute.GENERIC_ARMOR
    };

    private static class Orbit {
        final Element element;
        final ItemDisplay display;
        Location lastSent;
        long nextKey = 0;
        long nextParticle = 0;
        long nextDust = 0;

        Orbit(Element element, ItemDisplay display) {
            this.element = element;
            this.display = display;
        }
    }

    private static class Mod {
        final Attribute attr;
        final String name;
        final UUID id;
        final double amount;
        final AttributeModifier.Operation op;
        boolean applied = false;

        Mod(Attribute attr, String name, double amount, AttributeModifier.Operation op) {
            this.attr = attr;
            this.name = name;
            this.id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            this.amount = amount;
            this.op = op;
        }
    }

    private final NguyenToPlugin plugin;
    private final Settings settings;
    private final NamespacedKey ownedKey;
    private final NamespacedKey equippedKey;
    private final NamespacedKey scrollKey;

    private final Map<UUID, List<Element>> equipped = new HashMap<>();
    private final Map<UUID, List<Orbit>> orbits = new HashMap<>();
    private long tick = 0;
    private int statTimer = 0;
    private SkillItems skillItems;
    private final Random rnd = new Random();
    private BukkitTask task;
    private int interval = 2;
    private boolean particlesOn = true;
    private double density = 0.6;
    private float viewRange = 0.5f;

    public ElementManager(NguyenToPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.ownedKey = new NamespacedKey(plugin, "owned");
        this.equippedKey = new NamespacedKey(plugin, "equipped");
        this.scrollKey = new NamespacedKey(plugin, "scroll");
    }

    public void setSkillItems(SkillItems skillItems) {
        this.skillItems = skillItems;
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
        return true;
    }

    public List<Element> getEquipped(Player p) {
        return new ArrayList<>(equipped.getOrDefault(p.getUniqueId(), Collections.emptyList()));
    }

    /** Người chơi có đang trang bị nguyên tố có chiêu (Bóng Tối / Ánh Sáng) không. */
    public boolean hasSpecial(Player p) {
        List<Element> l = equipped.get(p.getUniqueId());
        if (l == null) return false;
        for (Element e : l) {
            if (e.isSpecial()) return true;
        }
        return false;
    }

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
        if (list.size() >= MAX_EQUIPPED) return EquipResult.FULL;
        list.add(e);
        saveEquipped(p);
        applyStats(p);
        rebuild(p);
        syncSkillItem(p);
        return EquipResult.OK;
    }

    public void unequip(Player p, Element e) {
        List<Element> list = equipped.get(p.getUniqueId());
        if (list == null || !list.remove(e)) return;
        saveEquipped(p);
        applyStats(p);
        rebuild(p);
        syncSkillItem(p);
    }

    public void unequipAll(Player p) {
        List<Element> list = equipped.get(p.getUniqueId());
        if (list == null || list.isEmpty()) return;
        list.clear();
        saveEquipped(p);
        applyStats(p);
        rebuild(p);
        syncSkillItem(p);
    }

    public void load(Player p) {
        List<Element> owned = getOwned(p);
        List<Element> eq = parse(p.getPersistentDataContainer().get(equippedKey, PersistentDataType.STRING));
        eq.removeIf(e -> !owned.contains(e));
        while (eq.size() > MAX_EQUIPPED) eq.remove(eq.size() - 1);
        equipped.put(p.getUniqueId(), eq);
        applyStats(p);
        rebuild(p);
        syncSkillItem(p);
    }

    public void unload(Player p) {
        if (skillItems != null) skillItems.removeAll(p);
        despawn(p);
        syncStats(p, Collections.emptyList());
        equipped.remove(p.getUniqueId());
    }

    /** Gọi sau khi /nguyento reload để áp lại chỉ số mới. */
    public void reloadStats() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (equipped.containsKey(p.getUniqueId())) {
                applyStats(p);
                rebuild(p);
            }
        }
    }

    /** Gọi sau khi hồi sinh / đổi thế giới: Minecraft có thể đã xóa buff, áp lại + dựng lại hiển thị. */
    public void refreshLater(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || !equipped.containsKey(p.getUniqueId())) return;
            applyStats(p);
            rebuild(p);
            syncSkillItem(p);
        });
    }

    private double val(Player p, Attribute a) {
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
        out.add("§7Máu tối đa: §f" + Settings.f(val(p, Attribute.GENERIC_MAX_HEALTH)));
        out.add("§7Sát thương cận chiến: §f" + Settings.f(val(p, Attribute.GENERIC_ATTACK_DAMAGE)));
        out.add("§7Tốc độ di chuyển: §f" + Settings.f(val(p, Attribute.GENERIC_MOVEMENT_SPEED) * 1000) + "%");
        out.add("§7Tốc độ đánh: §f" + Settings.f(val(p, Attribute.GENERIC_ATTACK_SPEED)));
        out.add("§7Giáp: §f" + Settings.f(val(p, Attribute.GENERIC_ARMOR)) + " §8(buff % giáp chỉ có tác dụng khi bạn đang mặc giáp)");
        return out;
    }

    public double maxHealth(LivingEntity le) {
        AttributeInstance a = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    // ------------------------------------------------------------------
    //  Chỉ số (buff) qua Attribute
    // ------------------------------------------------------------------

    public void applyStats(Player p) {
        syncStats(p, equipped.getOrDefault(p.getUniqueId(), Collections.emptyList()));
    }

    private void syncStats(Player p, List<Element> eq) {
        List<Mod> desired = new ArrayList<>();
        for (Element e : eq) {
            addDesired(desired, Attribute.GENERIC_MAX_HEALTH, e, "hp", settings.n(e, "max-health"),
                    AttributeModifier.Operation.ADD_NUMBER);
            addDesired(desired, Attribute.GENERIC_ATTACK_DAMAGE, e, "damage", settings.n(e, "damage"),
                    AttributeModifier.Operation.ADD_NUMBER);
            addDesired(desired, Attribute.GENERIC_MOVEMENT_SPEED, e, "speed", settings.n(e, "speed-percent") / 100.0,
                    AttributeModifier.Operation.ADD_SCALAR);
            addDesired(desired, Attribute.GENERIC_ATTACK_SPEED, e, "attackspeed", settings.n(e, "attack-speed-percent") / 100.0,
                    AttributeModifier.Operation.ADD_SCALAR);
            addDesired(desired, Attribute.GENERIC_ARMOR, e, "armor", settings.n(e, "armor-percent") / 100.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }

        // gỡ modifier cũ không còn đúng, giữ cái còn đúng (tránh mất máu khi đổi trang bị)
        for (Attribute a : STAT_ATTRIBUTES) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (!m.getName().startsWith(MOD_PREFIX)) continue;
                Mod keep = null;
                for (Mod d : desired) {
                    if (d.attr == a && d.name.equals(m.getName()) && d.op == m.getOperation()
                            && Math.abs(d.amount - m.getAmount()) < 1e-9) {
                        keep = d;
                        break;
                    }
                }
                if (keep != null) keep.applied = true;
                else inst.removeModifier(m);
            }
        }
        for (Mod d : desired) {
            if (d.applied) continue;
            AttributeInstance inst = p.getAttribute(d.attr);
            if (inst == null) continue;
            inst.addModifier(new AttributeModifier(d.id, d.name, d.amount, d.op));
        }
    }

    private void addDesired(List<Mod> list, Attribute attr, Element e, String stat, double amount,
                            AttributeModifier.Operation op) {
        if (amount == 0) return;
        list.add(new Mod(attr, MOD_PREFIX + e.getId() + ":" + stat, amount, op));
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
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, interval);
    }

    private void tickAll() {
        tick += interval;
        statTimer += interval;
        boolean refreshStats = statTimer >= 20;
        if (refreshStats) statTimer = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<Element> eq = equipped.get(p.getUniqueId());
            if (eq == null || eq.isEmpty()) continue;

            // mỗi ~1 giây kiểm tra lại buff (rất nhẹ: chỉ so sánh, không đổi nếu đã đúng)
            if (refreshStats && !p.isDead()) applyStats(p);

            List<Orbit> list = orbits.get(p.getUniqueId());
            if (!shouldShow(p)) {
                if (list != null) despawn(p);
                continue;
            }
            if (list == null || !isValid(p, list, eq.size())) {
                rebuild(p);
                continue;
            }
            update(p, list);
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (List<Orbit> list : orbits.values()) {
            for (Orbit o : list) o.display.remove();
        }
        orbits.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            syncStats(p, Collections.emptyList());
            if (skillItems != null) skillItems.removeAll(p);
        }
        equipped.clear();
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
            Location at = anchor(pl, height, i);
            ItemDisplay d = pl.getWorld().spawn(at, ItemDisplay.class);
            d.setItemStack(displayItem(el));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setGravity(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(viewRange);
            d.setTeleportDuration(interval);
            d.setTransformation(transform(i, el, tick));
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
        for (int i = 0; i < list.size(); i++) {
            Orbit o = list.get(i);
            Location target = anchor(pl, height, i);

            // mốc xoay + nhấp nhô kế tiếp (client tự nội suy giữa các mốc)
            if (tick + interval >= o.nextKey) {
                long when = tick + KEY_STEP;
                o.display.setInterpolationDuration(KEY_STEP);
                o.display.setInterpolationDelay(0);
                o.display.setTransformation(transform(i, o.element, when));
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
        }
        if (el.isSpecial() && tick >= o.nextDust) {
            o.nextDust = tick + 12;
            int n = scaled(3);
            if (n > 0) {
                Color c = el == Element.ANH_SANG ? Color.fromRGB(255, 215, 0) : Color.fromRGB(110, 0, 170);
                Fx.dust(w, loc, n, 0.25, c, 1.2f);
            }
        }
    }

    /** Vị trí của từng ô: 0 = trên đầu, 1 = vai trái, 2 = vai phải (yaw = hướng người chơi). */
    private Location anchor(Location pl, double height, int slot) {
        double yaw = Math.toRadians(pl.getYaw());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);

        double side, y, back;
        if (slot == 0) {
            side = 0;
            y = height + 0.5;
            back = 0;
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

    /** Xoay tròn + nghiêng ra ngoài ở hai bên vai + nhấp nhô nhẹ. 'when' = tick của mốc đích. */
    private Transformation transform(int slot, Element el, long when) {
        float spin = (float) (when * 0.1 + slot * 1.7);
        float tilt = slot == 0 ? 0f : (slot == 1 ? -0.45f : 0.45f);
        Quaternionf q = new Quaternionf().rotateZ(tilt).rotateX(slot == 0 ? 0f : 0.3f).rotateY(spin);
        float bob = (float) (Math.sin((when + slot * 13) * 0.12) * 0.07);
        float s = slot == 0 ? 0.9f : 0.75f;
        if (el.isSpecial()) s *= 1.25f;
        return new Transformation(new Vector3f(0f, bob, 0f), q, new Vector3f(s, s, s), new Quaternionf());
    }
}
