package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        Quaternionf last = new Quaternionf();

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
    private SkillItems skillItems;

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
            if (equipped.containsKey(p.getUniqueId())) applyStats(p);
        }
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

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick += 2;
            for (Player p : Bukkit.getOnlinePlayers()) {
                List<Element> eq = equipped.get(p.getUniqueId());
                if (eq == null || eq.isEmpty()) continue;

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
        }, 1L, 2L);
    }

    public void shutdown() {
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
        for (Orbit o : list) {
            if (!o.display.isValid()) return false;
            if (o.display.getWorld() != p.getWorld()) return false;
            if (o.display.getLocation().distanceSquared(p.getLocation()) > 64) return false;
        }
        return true;
    }

    private void rebuild(Player p) {
        despawn(p);
        List<Element> eq = equipped.get(p.getUniqueId());
        if (eq == null || eq.isEmpty() || !shouldShow(p)) return;

        List<Orbit> list = new ArrayList<>();
        for (int i = 0; i < eq.size(); i++) {
            Element el = eq.get(i);
            ItemDisplay d = p.getWorld().spawn(anchor(p, i), ItemDisplay.class);
            d.setItemStack(displayItem(el));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setGravity(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            Orbit o = new Orbit(el, d);
            d.setTransformation(transform(p, i, o));
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
        for (int i = 0; i < list.size(); i++) {
            Orbit o = list.get(i);
            Location loc = anchor(p, i);
            o.display.setInterpolationDelay(0);
            o.display.setTransformation(transform(p, i, o));
            o.display.teleport(loc);

            Element el = o.element;
            if (el.isSpecial()) {
                loc.getWorld().spawnParticle(el.getParticle(), loc, 3, 0.2, 0.2, 0.2, 0.02);
                if (tick % 6 == 0) {
                    Color c = el == Element.ANH_SANG ? Color.fromRGB(255, 215, 0) : Color.fromRGB(110, 0, 170);
                    Fx.dust(loc.getWorld(), loc, 4, 0.25, c, 1.2f);
                }
            } else if (tick % 4 == 0) {
                loc.getWorld().spawnParticle(el.getParticle(), loc, 2, 0.15, 0.15, 0.15, 0.01);
            }
        }
    }

    /** Vị trí của từng ô: 0 = trên đầu, 1 = vai trái, 2 = vai phải. */
    private Location anchor(Player p, int slot) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        double height = p.getHeight();
        double bob = Math.sin((tick + slot * 13) * 0.12) * 0.07;

        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);

        double side, y, back;
        if (slot == 0) {
            side = 0;
            y = height + 0.45 + bob;
            back = 0;
        } else {
            side = (slot == 1 ? -1 : 1) * 0.85;
            y = height * 0.8 + 0.15 + bob;
            back = -0.2;
        }
        return new Location(l.getWorld(),
                l.getX() + rx * side + fx * back,
                l.getY() + y,
                l.getZ() + rz * side + fz * back);
    }

    /** Xoay tròn + nghiêng ra ngoài ở hai bên vai cho ngầu. */
    private Transformation transform(Player p, int slot, Orbit o) {
        float spin = (float) (tick * 0.1 + slot * 1.7);
        float tilt = slot == 0 ? 0f : (slot == 1 ? -0.45f : 0.45f);
        float yaw = (float) Math.toRadians(p.getLocation().getYaw());

        Quaternionf q = new Quaternionf().rotateY(-yaw).rotateZ(tilt).rotateX(slot == 0 ? 0f : 0.3f).rotateY(spin);
        if (q.dot(o.last) < 0) q.set(-q.x, -q.y, -q.z, -q.w);
        o.last = new Quaternionf(q);

        float s = slot == 0 ? 0.9f : 0.75f;
        if (o.element.isSpecial()) s *= 1.25f;
        return new Transformation(new Vector3f(), q, new Vector3f(s, s, s), new Quaternionf());
    }
}
