package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Chiêu thức: Lãnh Địa Bóng Tối, Thần Phạt (3 cột ánh sáng), Hơi Thở Rồng + lá chắn ánh sáng + hồi chiêu. */
public class SkillManager implements Listener {

    private static final String CD_DOMAIN = "domain";
    private static final String CD_DIVINE = "divine";
    private static final String CD_SHIELD = "shield";
    private static final String CD_BREATH = "breath";

    /** Một mảnh tinh thể bóng tối mọc lên ở rìa lãnh địa. */
    private static class Spire {
        final BlockDisplay display;
        final float width;
        final Quaternionf tilt;

        Spire(BlockDisplay display, float width, Quaternionf tilt) {
            this.display = display;
            this.width = width;
            this.tilt = tilt;
        }
    }

    private static class Domain {
        final Location center;
        final double radius;
        final double height;
        BukkitTask task;
        /** Những kẻ đã bị khóa chân trong lãnh địa. */
        final Set<UUID> locked = new HashSet<>();
        final Map<UUID, Location> lastSafe = new HashMap<>();
        final Map<UUID, Integer> lastPulse = new HashMap<>();
        final List<Spire> spires = new ArrayList<>();
        BlockDisplay coreA;
        BlockDisplay coreB;
        /** Những chiếc sọ ma bay vòng trong lãnh địa. */
        final List<ItemDisplay> skulls = new ArrayList<>();

        Domain(Location center, double radius, double height) {
            this.center = center;
            this.radius = radius;
            this.height = height;
        }
    }

    private static class Breath {
        BukkitTask task;
        /** Các đầu rồng bay quanh vùng hơi thở. */
        final List<ItemDisplay> heads = new ArrayList<>();
    }

    private static final double[] HEAD_ORBIT = {0.78, 0.55, 0.38, 0.68, 0.28, 0.9, 0.46, 0.6};
    private static final double[] HEAD_HEIGHT = {12, 9, 7, 10.5, 6, 14, 8, 11};

    private final NguyenToPlugin plugin;
    private final ElementManager manager;
    private final Settings settings;
    private final Random rnd = new Random();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Domain> domains = new HashMap<>();
    private final Map<UUID, Breath> breaths = new HashMap<>();
    private boolean busy = false;
    private int hudCounter = 0;
    private BukkitTask mainTask;

    public SkillManager(NguyenToPlugin plugin, ElementManager manager, Settings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
    }

    // ------------------------------------------------------------------
    //  Tiện ích chung
    // ------------------------------------------------------------------

    public boolean isBusy() { return busy; }

    /** Chạy đoạn code gây sát thương của plugin mà không kích hoạt lại nội tại (tránh đệ quy). */
    public void runBusy(Runnable r) {
        boolean old = busy;
        busy = true;
        try {
            r.run();
        } finally {
            busy = old;
        }
    }

    public boolean validTarget(Player owner, Entity ent) {
        if (!(ent instanceof LivingEntity) || ent instanceof ArmorStand) return false;
        if (ent.equals(owner)) return false;
        if (ent instanceof Player) {
            Player t = (Player) ent;
            if (!settings.gb("skills-hit-players")) return false;
            GameMode gm = t.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
        } else if (!settings.gb("skills-hit-mobs")) {
            return false;
        }
        if (ent instanceof Tameable) {
            AnimalTamer o = ((Tameable) ent).getOwner();
            if (o != null && o.getUniqueId().equals(owner.getUniqueId())) return false;
        }
        return true;
    }

    private long remainingMs(Player p, String key) {
        Map<String, Long> m = cooldowns.get(p.getUniqueId());
        if (m == null) return 0;
        Long end = m.get(key);
        if (end == null) return 0;
        return Math.max(0, end - System.currentTimeMillis());
    }

    private void startCooldown(Player p, String key, double seconds) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(key, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    private boolean checkReady(Player p, String key) {
        long r = remainingMs(p, key);
        if (r > 0) {
            Fx.actionBar(p, "§cChiêu đang hồi: §f" + (r / 1000 + 1) + "s");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            return false;
        }
        return true;
    }

    /** Giá trị cấu hình, dùng 'def' nếu thiếu hoặc không dương. */
    private double cfg(Element el, String key, double def) {
        double v = settings.n(el, key);
        return v > 0 ? v : def;
    }

    // ------------------------------------------------------------------
    //  Vòng lặp: lá chắn + HUD
    // ------------------------------------------------------------------

    public void startTask() {
        if (mainTask != null) mainTask.cancel();
        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            hudCounter += 4;
            boolean hud = settings.gb("show-skill-hud") && hudCounter % 20 == 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (manager.has(p, Element.ANH_SANG)) tickShield(p);
                if (hud) hud(p);
            }
        }, 4L, 4L);
    }

    public void shutdown() {
        if (mainTask != null) mainTask.cancel();
        mainTask = null;
        for (Domain d : new ArrayList<>(domains.values())) {
            if (d.task != null) d.task.cancel();
            for (Spire s : d.spires) Vfx.remove(s.display);
            Vfx.remove(d.coreA);
            Vfx.remove(d.coreB);
            for (ItemDisplay sk : d.skulls) Vfx.remove(sk);
        }
        domains.clear();
        for (Breath b : new ArrayList<>(breaths.values())) {
            if (b.task != null) b.task.cancel();
            for (ItemDisplay h : b.heads) Vfx.remove(h);
        }
        breaths.clear();
    }

    private void hud(Player p) {
        if (!manager.hasSpecial(p)) return;
        StringBuilder sb = new StringBuilder();
        for (Element e : manager.getEquipped(p)) {
            String part = null;
            if (e == Element.BONG_TOI) part = hudPart(p, "§5✦ Lãnh Địa", CD_DOMAIN);
            else if (e == Element.ANH_SANG) part = hudPart(p, "§6✦ Thần Phạt", CD_DIVINE);
            else if (e == Element.RONG) part = hudPart(p, "§4✦ Hơi Thở Rồng", CD_BREATH);
            if (part == null) continue;
            if (sb.length() > 0) sb.append("  §8|  ");
            sb.append(part);
        }
        if (sb.length() > 0) Fx.actionBar(p, sb.toString());
    }

    private String hudPart(Player p, String name, String key) {
        long r = remainingMs(p, key);
        String state = r > 0 ? "§c" + (r / 1000 + 1) + "s" : "§aSẵn sàng";
        return name + " §8» " + state;
    }

    // ------------------------------------------------------------------
    //  Dùng chiêu (chuột phải vật phẩm kỹ năng, nguyên tố ở ô nào cũng được)
    // ------------------------------------------------------------------

    /** @return true nếu người chơi đang trang bị nguyên tố đó và nó có chiêu (kể cả khi đang hồi chiêu). */
    public boolean tryCast(Player p, Element e) {
        if (e == null || p.isDead() || !manager.has(p, e)) return false;
        if (e == Element.BONG_TOI) {
            castDomain(p);
            return true;
        }
        if (e == Element.ANH_SANG) {
            castDivine(p);
            return true;
        }
        if (e == Element.RONG) {
            castBreath(p);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  BÓNG TỐI: Lãnh Địa Bóng Tối (khóa chân)
    // ------------------------------------------------------------------

    public boolean isInDomain(Player p) {
        Domain d = domains.get(p.getUniqueId());
        if (d == null || d.center.getWorld() != p.getWorld()) return false;
        double dx = p.getLocation().getX() - d.center.getX();
        double dz = p.getLocation().getZ() - d.center.getZ();
        return dx * dx + dz * dz <= d.radius * d.radius;
    }

    private void castDomain(Player p) {
        if (!checkReady(p, CD_DOMAIN)) return;
        Element el = Element.BONG_TOI;
        startCooldown(p, CD_DOMAIN, settings.n(el, "skill.cooldown"));
        endDomain(p, false);

        final int maxTicks = (int) (cfg(el, "skill.duration", 10) * 20);
        final Domain d = new Domain(p.getLocation().clone(), cfg(el, "skill.radius", 7), cfg(el, "skill.height", 12));
        domains.put(p.getUniqueId(), d);

        World w = d.center.getWorld();
        p.sendTitle("§5§l✦ LÃNH ĐỊA BÓNG TỐI ✦", "§7Không ai thoát được bóng đêm...", 5, 35, 10);
        w.playSound(d.center, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.5f);
        w.playSound(d.center, Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.6f);
        w.playSound(d.center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
        buildDomainVisuals(d);

        d.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || ticks >= maxTicks) {
                    endDomain(p, true);
                    return;
                }
                tickDomain(p, d, ticks);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private static Transformation cube(float s, Quaternionf q) {
        Vector3f t = new Vector3f(s / 2f, s / 2f, s / 2f);
        q.transform(t);
        t.negate();
        return new Transformation(t, new Quaternionf(q), new Vector3f(s, s, s), new Quaternionf());
    }

    /** Tinh thể bóng tối trồi lên quanh rìa + lõi hắc ám xoay trên cao (Display, không phải hạt). */
    private void buildDomainVisuals(Domain d) {
        if (!Vfx.on()) return;
        final World w = d.center.getWorld();
        final double cx = d.center.getX(), cy = d.center.getY(), cz = d.center.getZ();
        final int n = 14;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n + rnd.nextDouble() * 0.2;
            double x = cx + Math.cos(a) * d.radius;
            double z = cz + Math.sin(a) * d.radius;
            double y = Vfx.groundY(w, x, z, cy);
            final float width = 0.6f + rnd.nextFloat() * 0.5f;
            final float height = 4.0f + rnd.nextFloat() * 5.0f;
            final Quaternionf tilt = new Quaternionf().rotateY((float) (rnd.nextDouble() * Math.PI))
                    .rotateX((rnd.nextFloat() - 0.5f) * 0.35f).rotateZ((rnd.nextFloat() - 0.5f) * 0.35f);
            Material m = i % 2 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN;
            final BlockDisplay bd = Vfx.block(w, new Location(w, x, y, z), m,
                    new Transformation(new Vector3f(-width / 2f, 0f, -width / 2f), tilt,
                            new Vector3f(width, 0.05f, width), new Quaternionf()));
            d.spires.add(new Spire(bd, width, tilt));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!bd.isValid()) return;
                bd.setInterpolationDelay(0);
                bd.setInterpolationDuration(14);
                bd.setTransformation(new Transformation(new Vector3f(-width / 2f, 0f, -width / 2f), tilt,
                        new Vector3f(width, height, width), new Quaternionf()));
            }, 2L + i);
        }
        Location top = new Location(w, cx, cy + 9.0, cz);
        d.coreA = Vfx.block(w, top, Material.BLACK_CONCRETE, cube(0.01f, new Quaternionf()));
        d.coreB = Vfx.block(w, top, Material.PURPLE_STAINED_GLASS, cube(0.01f, new Quaternionf()));
        for (int i = 0; i < 6; i++) {
            d.skulls.add(Vfx.item(w, new Location(w, cx, cy + 3.0, cz), Material.WITHER_SKELETON_SKULL, 2.4f));
        }
        final BlockDisplay a = d.coreA;
        final BlockDisplay b = d.coreB;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (a.isValid()) {
                a.setInterpolationDelay(0);
                a.setInterpolationDuration(20);
                a.setTransformation(cube(1.7f, new Quaternionf().rotateX(0.6155f).rotateZ(0.7854f)));
            }
            if (b.isValid()) {
                b.setInterpolationDelay(0);
                b.setInterpolationDuration(20);
                b.setTransformation(cube(2.6f, new Quaternionf().rotateX(0.6155f).rotateZ(0.7854f)));
            }
        }, 4L);
    }

    private void animateSkulls(Domain d, int ticks) {
        int n = d.skulls.size();
        if (n == 0) return;
        World w = d.center.getWorld();
        double cx = d.center.getX(), cy = d.center.getY(), cz = d.center.getZ();
        double orbit = d.radius * 0.72;
        for (int i = 0; i < n; i++) {
            ItemDisplay sk = d.skulls.get(i);
            if (!sk.isValid()) continue;
            double ang = i * 2 * Math.PI / n + ticks * 0.05;
            double y = cy + 2.2 + Math.sin(ticks * 0.12 + i * 1.3) * 0.8;
            Location pos = new Location(w, cx + Math.cos(ang) * orbit, y, cz + Math.sin(ang) * orbit);
            Location facing = pos.clone();
            facing.setDirection(new Vector(-Math.sin(ang), 0, Math.cos(ang)));
            sk.teleport(facing);
            if (ticks % 6 == 0) {
                w.spawnParticle(Particle.SQUID_INK, pos, 2, 0.2, 0.2, 0.2, 0.01);
                w.spawnParticle(Particle.SOUL, pos, 1, 0.2, 0.2, 0.2, 0.01);
                for (int k = 1; k <= 6; k++) {
                    double a2 = ang - k * 0.09;
                    Vfx.dustAt(w, cx + Math.cos(a2) * orbit, y, cz + Math.sin(a2) * orbit,
                            Color.fromRGB(90, 0, 140), Math.max(1.0f, 2.2f - k * 0.2f));
                }
            }
        }
        if (ticks % 40 == 0) w.playSound(d.center, Sound.ENTITY_VEX_CHARGE, 1.0f, 0.5f);
    }

    /** Hố đen trên cao, sét bóng tối, xích + gông chân + gai bóng tối trồi lên dưới chân kẻ bị khóa. */
    private void darkExtras(Player owner, Domain d, int ticks) {
        if (!Vfx.on()) return;
        World w = d.center.getWorld();
        double cx = d.center.getX(), cy = d.center.getY(), cz = d.center.getZ();
        double r = d.radius;
        Color purple = Color.fromRGB(120, 0, 190);
        Color black = Color.fromRGB(15, 0, 25);
        Location core = new Location(w, cx, cy + 9.0, cz);
        double rot = ticks * 0.18;

        // hố đen: dòng vật chất xoáy tụ vào lõi
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < 14; k++) {
                double t01 = k / 14.0;
                double rr = 7.5 * (1.0 - t01) + 0.6;
                double a = rot + arm * 2 * Math.PI / 3 + t01 * 5.0;
                Vfx.dustAt(w, cx + Math.cos(a) * rr, core.getY() + Math.sin(a * 2) * 0.3, cz + Math.sin(a) * rr,
                        k % 2 == 0 ? purple : black, 1.5f);
            }
        }
        w.spawnParticle(Particle.PORTAL, core, 20, 0.5, 0.5, 0.5, 1.0);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, core, 4, 0.5, 0.5, 0.5, 0.02);
        Vfx.dustRing(w, new Location(w, cx, cy + 14.0, cz), r * 0.9, 0, Vfx.pts((int) (r * 4), 16), -ticks * 0.09, purple, 1.4f);

        // sét bóng tối từ lõi giáng xuống các tinh thể
        if (ticks % 18 == 0 && !d.spires.isEmpty()) {
            Spire s = d.spires.get(rnd.nextInt(d.spires.size()));
            if (s.display.isValid()) {
                Location top = s.display.getLocation().clone().add(0, 4, 0);
                Vfx.bolt(w, core, top, purple, black, Particle.SOUL_FIRE_FLAME);
                w.playSound(top, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 0.6f);
            }
        }

        // xích bóng tối nối lõi với từng kẻ bị khóa + vòng gông chân + gai bóng tối
        int shown = 0;
        for (UUID id : new ArrayList<>(d.locked)) {
            if (shown >= 8) break;
            Entity ent = Bukkit.getEntity(id);
            if (!(ent instanceof LivingEntity) || !ent.isValid()) continue;
            Location feet = ent.getLocation();
            Vfx.line(w, core, feet.clone().add(0, 1.0, 0), 0.6, purple, 1.2f, Particle.SOUL_FIRE_FLAME);
            Vfx.dustRing(w, feet, 0.75, 0.15, 10, ticks * 0.2, black, 1.3f);
            Vfx.dustRing(w, feet, 0.75, 0.35, 10, -ticks * 0.2, purple, 1.1f);
            if (ticks % 30 == 0 && shown < 4) spawnSpikes(w, feet);
            shown++;
        }
    }

    /** Vòng gai tinh thể bóng tối đâm lên dưới chân mục tiêu rồi thụt xuống (chỉ là hiệu ứng, không gây sát thương). */
    private void spawnSpikes(World w, Location base) {
        w.playSound(base, Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 0.5f);
        Vfx.blockBurst(w, base.clone().add(0, 0.2, 0), Material.OBSIDIAN, Vfx.pts(14, 6), 0.5);
        final int n = 6;
        for (int i = 0; i < n; i++) {
            final double a = 2 * Math.PI * i / n + rnd.nextDouble() * 0.4;
            final double x = base.getX() + Math.cos(a) * 1.1;
            final double z = base.getZ() + Math.sin(a) * 1.1;
            final float wd = 0.3f;
            final float h = 1.6f + rnd.nextFloat() * 1.2f;
            final Quaternionf tilt = new Quaternionf().rotateY((float) -a).rotateZ(-0.35f);
            final BlockDisplay bd = Vfx.block(w, new Location(w, x, base.getY() - 0.2, z), Material.CRYING_OBSIDIAN,
                    new Transformation(new Vector3f(0f, 0f, 0f), tilt, new Vector3f(wd, 0.05f, wd), new Quaternionf()));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!bd.isValid()) return;
                bd.setInterpolationDelay(0);
                bd.setInterpolationDuration(3);
                bd.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), tilt, new Vector3f(wd, h, wd), new Quaternionf()));
            }, 1L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!bd.isValid()) return;
                bd.setInterpolationDelay(0);
                bd.setInterpolationDuration(6);
                bd.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), tilt, new Vector3f(wd, 0.02f, wd), new Quaternionf()));
            }, 24L);
            Vfx.removeLater(bd, 32L);
        }
    }

    private void animateCore(Domain d, int ticks) {
        if (d.coreA == null || !d.coreA.isValid() || d.coreB == null || !d.coreB.isValid()) return;
        float a = ticks * 0.06f;
        d.coreA.setInterpolationDelay(0);
        d.coreA.setInterpolationDuration(10);
        d.coreA.setTransformation(cube(1.7f, new Quaternionf().rotateY(a).rotateX(0.6155f).rotateZ(0.7854f)));
        d.coreB.setInterpolationDelay(0);
        d.coreB.setInterpolationDuration(10);
        d.coreB.setTransformation(cube(2.6f, new Quaternionf().rotateY(-a * 0.7f).rotateX(0.6155f).rotateZ(0.7854f)));
    }

    private void endDomain(Player p, boolean announce) {
        Domain d = domains.remove(p.getUniqueId());
        if (d == null) return;
        if (d.task != null) d.task.cancel();
        d.locked.clear();
        d.lastSafe.clear();
        // tinh thể thu nhỏ rồi biến mất
        for (Spire s : d.spires) {
            final BlockDisplay bd = s.display;
            if (bd.isValid()) {
                bd.setInterpolationDelay(0);
                bd.setInterpolationDuration(10);
                bd.setTransformation(new Transformation(new Vector3f(-s.width / 2f, 0f, -s.width / 2f), s.tilt,
                        new Vector3f(s.width, 0.02f, s.width), new Quaternionf()));
            }
            Vfx.removeLater(bd, 12L);
        }
        for (BlockDisplay core : new BlockDisplay[]{d.coreA, d.coreB}) {
            if (core != null && core.isValid()) {
                core.setInterpolationDelay(0);
                core.setInterpolationDuration(10);
                core.setTransformation(cube(0.01f, new Quaternionf()));
            }
            Vfx.removeLater(core, 12L);
        }
        for (ItemDisplay sk : d.skulls) Vfx.remove(sk);
        d.skulls.clear();
        if (announce) {
            if (p.isOnline()) Fx.actionBar(p, "§5Lãnh địa bóng tối đã tan biến.");
            d.center.getWorld().playSound(d.center, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f);
            if (Vfx.on()) {
                d.center.getWorld().spawnParticle(Particle.SQUID_INK, d.center.clone().add(0, 1, 0), 60, d.radius / 3, 1, d.radius / 3, 0.1);
            }
        }
    }

    private boolean isFree(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private void tickDomain(Player owner, Domain d, int ticks) {
        Element el = Element.BONG_TOI;
        World w = d.center.getWorld();
        if (w == null) {
            endDomain(owner, false);
            return;
        }
        double cx = d.center.getX(), cy = d.center.getY(), cz = d.center.getZ();
        double r = d.radius;

        // --- khóa chân (mỗi 2 tick) ---
        for (Entity ent : w.getNearbyEntities(d.center, r + 1, d.height + 6, r + 1)) {
            if (!validTarget(owner, ent)) continue;
            double dx = ent.getLocation().getX() - cx;
            double dz = ent.getLocation().getZ() - cz;
            if (dx * dx + dz * dz <= r * r && d.locked.add(ent.getUniqueId()) && ent instanceof Player) {
                Player t = (Player) ent;
                Fx.actionBar(t, "§5✦ Bạn bị khóa chân trong Lãnh Địa Bóng Tối!");
                t.playSound(t.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.5f);
            }
        }
        enforceLock(owner, d, ticks);

        // --- lõi hắc ám xoay + sọ ma bay vòng ---
        if (ticks % 10 == 0) animateCore(d, ticks);
        animateSkulls(d, ticks);

        // --- hiệu ứng lên người + hình ảnh (mỗi 6 tick) ---
        if (ticks % 6 != 0) return;

        if (Vfx.on()) {
            int pts = Vfx.pts((int) (r * 4), 16);
            double rot = ticks * 0.03;
            Color purple = Color.fromRGB(110, 0, 170);
            Color black = Color.fromRGB(20, 0, 30);
            // bức tường bóng tối: 3 tầng vòng + vệt cổng không gian
            for (int layer = 0; layer < 3; layer++) {
                double yy = cy + 0.2 + layer * 1.3;
                Vfx.dustRing(w, d.center, r, yy - cy, pts, rot + layer * 0.4, layer % 2 == 0 ? purple : black, 1.6f);
            }
            for (int i = 0; i < pts; i += 6) {
                double a = 2 * Math.PI * i / pts + rot;
                double x = cx + Math.cos(a) * r;
                double z = cz + Math.sin(a) * r;
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, x, cy + 0.15, z, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.PORTAL, x, cy + 1.2, z, 4, 0.1, 1.0, 0.1, 0.1);
            }
            // vòng ma pháp dưới chân
            Vfx.dustRing(w, d.center, r * 0.55, 0.12, Vfx.pts(36, 14), -rot * 2, purple, 1.4f);
            Vfx.star(w, d.center, r * 0.85, 0.12, rot * 1.5, 6, purple, 1.1f);
            int fill = Vfx.pts(8, 4);
            for (int i = 0; i < fill; i++) {
                double a = rnd.nextDouble() * 2 * Math.PI;
                double rr = Math.sqrt(rnd.nextDouble()) * r;
                w.spawnParticle(Particle.SQUID_INK, cx + Math.cos(a) * rr, cy + 0.1, cz + Math.sin(a) * rr,
                        1, 0, 0.02, 0, 0.01);
            }
        }

        darkExtras(owner, d, ticks);

        int ownerSpeed = (int) settings.n(el, "skill.owner-speed-amplifier");
        int enemySlow = (int) settings.n(el, "skill.enemy-slow-amplifier");
        boolean nausea = settings.n(el, "skill.enemy-nausea") > 0;
        boolean darkness = settings.n(el, "skill.enemy-darkness") > 0;

        for (Entity ent : w.getNearbyEntities(d.center, r, 6, r)) {
            double dx = ent.getLocation().getX() - cx;
            double dz = ent.getLocation().getZ() - cz;
            if (dx * dx + dz * dz > r * r) continue;

            if (ent.equals(owner)) {
                Fx.addEffect(owner, 30, ownerSpeed, false, "SPEED");
            } else if (validTarget(owner, ent)) {
                LivingEntity le = (LivingEntity) ent;
                Fx.addEffect(le, 40, enemySlow, true, "SLOW", "SLOWNESS");
                if (nausea) Fx.addEffect(le, 100, 0, true, "CONFUSION", "NAUSEA");
                if (darkness && le instanceof Player) Fx.addEffect(le, 60, 0, false, "DARKNESS");
            }
        }
    }

    /** Giữ những kẻ bị khóa ở trong lãnh địa: sát tường thì bị đẩy lại, vượt tường thì kéo về chỗ an toàn gần nhất. */
    private void enforceLock(Player owner, Domain d, int ticks) {
        if (d.locked.isEmpty()) return;
        double cx = d.center.getX(), cy = d.center.getY(), cz = d.center.getZ();
        double r = d.radius;
        World w = d.center.getWorld();

        for (UUID id : new ArrayList<>(d.locked)) {
            Entity ent = Bukkit.getEntity(id);
            if (!(ent instanceof LivingEntity) || !ent.isValid() || ent.isDead()
                    || ent.getWorld() != w || !validTarget(owner, ent)) {
                d.locked.remove(id);
                d.lastSafe.remove(id);
                d.lastPulse.remove(id);
                continue;
            }
            LivingEntity le = (LivingEntity) ent;
            Location l = le.getLocation();
            double dx = l.getX() - cx;
            double dz = l.getZ() - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            boolean tooHigh = l.getY() > cy + d.height;

            if (dist <= r - 1.0 && !tooHigh) {
                if (isFree(l)) d.lastSafe.put(id, l.clone());
                continue;
            }

            // chạm tường: hiệu ứng (giới hạn tần suất)
            Integer last = d.lastPulse.get(id);
            if (last == null || ticks - last >= 10) {
                d.lastPulse.put(id, ticks);
                wallPulse(w, l, dx, dz, dist);
                if (le instanceof Player) Fx.actionBar((Player) le, "§5✦ Không thể thoát khỏi Lãnh Địa Bóng Tối!");
            }

            if (dist > r + 0.15 || tooHigh) {
                Location back = d.lastSafe.get(id);
                if (back == null || back.getWorld() != w) {
                    double len = Math.max(0.0001, dist);
                    Location c = new Location(w, cx + dx / len * (r - 1.5), Math.min(l.getY(), cy + d.height - 1), cz + dz / len * (r - 1.5));
                    back = isFree(c) ? c : d.center.clone();
                }
                back = back.clone();
                back.setYaw(l.getYaw());
                back.setPitch(l.getPitch());
                le.teleport(back);
                le.setVelocity(new Vector(0, 0, 0));
            } else {
                double len = Math.max(0.0001, dist);
                double vy = Math.max(-0.1, Math.min(0.1, le.getVelocity().getY()));
                le.setVelocity(new Vector(-dx / len * 0.35, vy, -dz / len * 0.35));
            }
        }
    }

    private void wallPulse(World w, Location l, double dx, double dz, double dist) {
        w.playSound(l, Sound.ENTITY_WITHER_HURT, 0.5f, 0.5f);
        if (!Vfx.on()) return;
        double len = Math.max(0.0001, dist);
        Location hit = l.clone().add(dx / len * 0.6, 1.0, dz / len * 0.6);
        w.spawnParticle(Particle.PORTAL, hit, 25, 0.3, 0.8, 0.3, 0.5);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, hit, 10, 0.3, 0.8, 0.3, 0.05);
        Vfx.dustRing(w, l, 0.8, 0.9, 12, 0, Color.fromRGB(140, 0, 210), 1.5f);
    }

    /** Chặn thoát khỏi lãnh địa bằng ngọc ender / trái chorus. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && c != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        UUID id = e.getPlayer().getUniqueId();
        for (Domain d : domains.values()) {
            if (!d.locked.contains(id)) continue;
            Location to = e.getTo();
            boolean outside = to == null || to.getWorld() != d.center.getWorld();
            if (!outside) {
                double dx = to.getX() - d.center.getX();
                double dz = to.getZ() - d.center.getZ();
                outside = dx * dx + dz * dz > (d.radius - 0.5) * (d.radius - 0.5);
            }
            if (outside) {
                e.setCancelled(true);
                Fx.actionBar(e.getPlayer(), "§5✦ Lãnh Địa Bóng Tối chặn dịch chuyển của bạn!");
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    //  ÁNH SÁNG: Thần Phạt - 3 cột ánh sáng
    // ------------------------------------------------------------------

    private void castDivine(Player p) {
        if (!checkReady(p, CD_DIVINE)) return;
        Element el = Element.ANH_SANG;
        startCooldown(p, CD_DIVINE, settings.n(el, "skill.cooldown"));

        final int pillars = Math.max(1, Math.min(8, (int) cfg(el, "skill.pillars", 3)));
        final double radius = cfg(el, "skill.radius", 3);
        final double damage = settings.n(el, "skill.damage");
        final double range = cfg(el, "skill.range", 28);
        final int delay = Math.max(6, (int) settings.n(el, "skill.cast-delay-ticks"));
        final int stagger = Math.max(0, (int) settings.n(el, "skill.stagger-ticks"));
        final World w = p.getWorld();

        List<Location> spots = pickPillarSpots(p, pillars, range);

        p.sendTitle("§6§l✦ THẦN PHẠT ✦", "§eBa cột ánh sáng phán xét tất cả!", 3, 30, 10);
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 2f, 1.4f);
        w.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.5f, 1.2f);
        haloWings(p);

        for (int i = 0; i < spots.size(); i++) {
            final Location spot = spots.get(i);
            final int strikeAt = delay + i * stagger;
            new BukkitRunnable() {
                int t = 0;
                ItemDisplay star = null;

                private void clearStar() {
                    if (star != null) Vfx.remove(star);
                    star = null;
                }

                @Override
                public void run() {
                    if (!p.isOnline()) {
                        clearStar();
                        cancel();
                        return;
                    }
                    if (t >= strikeAt) {
                        clearStar();
                        cancel();
                        lightStrike(p, spot, radius, damage);
                        return;
                    }
                    telegraph(spot, radius, t, strikeAt);
                    star = skySigil(spot, t, strikeAt, star);
                    t += 2;
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    /** Cánh thiên thần + vầng hào quang quanh người tung chiêu. */
    private void haloWings(Player p) {
        if (!Vfx.on()) return;
        final Color gold = Color.fromRGB(255, 215, 0);
        final Color white = Color.fromRGB(255, 255, 235);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= 40) {
                    cancel();
                    return;
                }
                Location l = p.getLocation();
                World w = l.getWorld();
                double yaw = Math.toRadians(l.getYaw());
                Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
                Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
                Vfx.dustRing(w, l, 0.55, 2.35, 14, t * 0.3, gold, 1.2f);
                double flap = Math.sin(t * 0.35) * 0.18;
                Location base = l.clone().add(0, 1.35, 0).subtract(fwd.clone().multiply(0.25));
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i < 7; i++) {
                        double a = 0.25 + i * 0.2 + flap;
                        double len = 2.8 - i * 0.22;
                        Vector dir = right.clone().multiply(side * Math.cos(a))
                                .add(new Vector(0, Math.sin(a), 0))
                                .subtract(fwd.clone().multiply(0.35)).normalize();
                        Location end = base.clone().add(dir.multiply(len));
                        Vfx.line(w, base, end, 0.35, i % 2 == 0 ? white : gold, 1.1f, null);
                    }
                }
                w.spawnParticle(Particle.END_ROD, l.getX(), l.getY() + 1.5, l.getZ(), 3, 0.6, 0.8, 0.6, 0.02);
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Ngôi sao ánh sáng tụ năng lượng trên trời phía trên mỗi cột + vòng ma pháp trên không. */
    private ItemDisplay skySigil(Location spot, int t, int strikeAt, ItemDisplay star) {
        if (!Vfx.on()) return star;
        World w = spot.getWorld();
        double prog = Math.min(1.0, (double) t / strikeAt);
        Location sky = spot.clone().add(0, 16, 0);
        if (star == null) {
            star = Vfx.item(w, sky, Material.NETHER_STAR, 0.5f);
            star.setBillboard(Display.Billboard.CENTER);
        }
        if (star.isValid()) {
            float sc = (float) (0.6 + 3.6 * prog);
            star.setInterpolationDelay(0);
            star.setInterpolationDuration(2);
            star.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), new Quaternionf().rotateZ(t * 0.2f),
                    new Vector3f(sc, sc, sc), new Quaternionf()));
        }
        Color gold = Color.fromRGB(255, 215, 0);
        Color white = Color.fromRGB(255, 250, 220);
        Vfx.dustRing(w, sky, 2.0 + 2.5 * prog, 0, Vfx.pts(24, 10), t * 0.15, gold, 1.5f);
        if (t % 4 == 0) {
            Vfx.star(w, sky, 3.0 + prog, 0, -t * 0.1, 6, white, 1.2f);
            Vfx.line(w, sky, spot.clone().add(0, 1, 0), 0.9, Color.fromRGB(255, 235, 150), 1.0f, Particle.END_ROD);
        }
        w.spawnParticle(Particle.END_ROD, sky, 3, 0.6, 0.6, 0.6, 0.02);
        return star;
    }

    /** Tia nắng tỏa ra mặt đất + vầng sáng thánh còn lại sau khi cột giáng xuống. */
    private void sunburst(Location spot, double radius) {
        final World w = spot.getWorld();
        final Color gold = Color.fromRGB(255, 215, 0);
        final Color white = Color.fromRGB(255, 255, 235);
        Location c = spot.clone().add(0, 0.12, 0);
        for (int i = 0; i < 12; i++) {
            double a = 2 * Math.PI * i / 12;
            Location end = c.clone().add(Math.cos(a) * radius * 2.6, 0, Math.sin(a) * radius * 2.6);
            Vfx.line(w, c, end, 0.45, i % 2 == 0 ? gold : white, 1.4f, Particle.END_ROD);
        }
        Vfx.fallingDust(w, spot.clone().add(0, 9, 0), Material.GLOWSTONE, Vfx.pts(24, 10), radius, 4.0, radius);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t += 2;
                double rr = radius * (0.85 + 0.15 * Math.sin(t * 0.5));
                Vfx.dustRing(w, spot, rr, 0.12, Vfx.pts((int) (rr * 8) + 8, 10), t * 0.2, gold, 1.3f);
                w.spawnParticle(Particle.END_ROD, spot.getX() + (rnd.nextDouble() - 0.5) * radius * 2, spot.getY() + 0.2,
                        spot.getZ() + (rnd.nextDouble() - 0.5) * radius * 2, 2, 0.1, 0.8, 0.1, 0.02);
                if (t >= 40) cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Chọn vị trí các cột: ưu tiên kẻ địch gần nhất; thiếu thì xếp thành hình quạt phía trước người dùng. */
    private List<Location> pickPillarSpots(Player p, int n, double range) {
        final Location origin = p.getLocation();
        final World w = origin.getWorld();
        List<LivingEntity> cands = new ArrayList<>();
        for (Entity ent : w.getNearbyEntities(origin, range, range, range)) {
            if (validTarget(p, ent) && ent.getLocation().distanceSquared(origin) <= range * range) {
                cands.add((LivingEntity) ent);
            }
        }
        cands.sort(Comparator.comparingDouble(en -> en.getLocation().distanceSquared(origin)));

        List<Location> out = new ArrayList<>();
        for (int i = 0; i < cands.size() && out.size() < n; i++) {
            Location l = cands.get(i).getLocation();
            double y = Vfx.groundY(w, l.getX(), l.getZ(), l.getY());
            out.add(new Location(w, l.getX(), y, l.getZ()));
        }
        int missing = n - out.size();
        if (missing > 0) {
            double yaw = Math.toRadians(origin.getYaw());
            for (int k = 0; k < missing; k++) {
                double dir = yaw + (k - (missing - 1) / 2.0) * 0.75;
                double dist = 7.0 + (k % 2) * 2.5;
                double x = origin.getX() - Math.sin(dir) * dist;
                double z = origin.getZ() + Math.cos(dir) * dist;
                out.add(new Location(w, x, Vfx.groundY(w, x, z, origin.getY()), z));
            }
        }
        return out;
    }

    /** Báo trước: vòng ma pháp ánh sáng xoay + sao 6 cánh + tia sáng bốc lên. */
    private void telegraph(Location spot, double radius, int t, int strikeAt) {
        World w = spot.getWorld();
        if (t == 0) w.playSound(spot, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
        if (!Vfx.on()) return;
        double prog = Math.min(1.0, (double) t / strikeAt);
        Color gold = Color.fromRGB(255, 215, 0);
        Color white = Color.fromRGB(255, 250, 220);
        Vfx.dustRing(w, spot, radius * (1.0 + 0.12 * Math.sin(t * 0.4)), 0.12, Vfx.pts((int) (radius * 9), 16), t * 0.12, gold, 1.5f);
        Vfx.dustRing(w, spot, radius * (1.0 - prog * 0.85), 0.16, Vfx.pts((int) (radius * 7), 12), -t * 0.2, white, 1.3f);
        if (t % 4 == 0) Vfx.star(w, spot, radius * 0.9, 0.18, t * 0.1, 6, gold, 1.1f);
        w.spawnParticle(Particle.END_ROD, spot.getX(), spot.getY() + 0.3 + prog * 4, spot.getZ(), 3,
                radius / 3, 0.3, radius / 3, 0.01);
    }

    private void lightStrike(Player caster, Location spot, double radius, double damage) {
        final World w = spot.getWorld();
        w.strikeLightningEffect(spot);
        w.playSound(spot, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 1.3f);
        w.playSound(spot, Sound.BLOCK_BEACON_ACTIVATE, 1.8f, 1.9f);
        w.playSound(spot, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.5f);

        if (Vfx.on()) {
            // cột sáng 3 lớp (vàng - trắng - lõi), xoắn lại và thu nhỏ dần
            final float wo = (float) (radius * 0.9);
            final BlockDisplay glass = Vfx.block(w, spot, Material.YELLOW_STAINED_GLASS, Vfx.centered(wo, 70f, 0f));
            final BlockDisplay mid = Vfx.block(w, spot, Material.WHITE_STAINED_GLASS, Vfx.centered(wo * 0.5f, 70f, 0f));
            final BlockDisplay core = Vfx.block(w, spot, Material.WHITE_CONCRETE, Vfx.centered(wo * 0.2f, 70f, 0f));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (glass.isValid()) {
                    glass.setInterpolationDelay(0);
                    glass.setInterpolationDuration(30);
                    glass.setTransformation(Vfx.centered(0.05f, 70f, 1.5f));
                }
                if (mid.isValid()) {
                    mid.setInterpolationDelay(0);
                    mid.setInterpolationDuration(26);
                    mid.setTransformation(Vfx.centered(0.03f, 70f, -1.5f));
                }
                if (core.isValid()) {
                    core.setInterpolationDelay(0);
                    core.setInterpolationDuration(22);
                    core.setTransformation(Vfx.centered(0.02f, 70f, 1.0f));
                }
            }, 6L);
            Vfx.removeLater(glass, 40L);
            Vfx.removeLater(mid, 36L);
            Vfx.removeLater(core, 32L);

            Vfx.flash(w, spot.clone().add(0, 1, 0));
            sunburst(spot, radius);
            for (double y = 0; y < 40; y += 2) {
                w.spawnParticle(Particle.END_ROD, spot.getX(), spot.getY() + y, spot.getZ(), 4, 0.25, 0.4, 0.25, 0.02);
            }
            Color gold = Color.fromRGB(255, 215, 0);
            Color white = Color.fromRGB(255, 255, 240);
            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    t++;
                    double rr = radius * 1.8 * t / 6.0;
                    Vfx.dustRing(w, spot, rr, 0.2, Vfx.pts((int) (rr * 8) + 12, 14), t * 0.3, gold, 1.8f);
                    Vfx.ring(w, spot, rr, 0.35, Vfx.pts((int) (rr * 6) + 10, 10), 0, Particle.END_ROD);
                    Vfx.helix(w, spot, radius * 0.6, 14, t * 0.6, 2, gold, white, Particle.END_ROD);
                    if (t >= 8) cancel();
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }

        // sát thương: mọi kẻ địch trong cột (bán kính 'radius', cao thấp 6 block)
        final double r2 = radius * radius;
        for (Entity ent : w.getNearbyEntities(spot, radius, 6, radius)) {
            if (!validTarget(caster, ent)) continue;
            double dx = ent.getLocation().getX() - spot.getX();
            double dz = ent.getLocation().getZ() - spot.getZ();
            if (dx * dx + dz * dz > r2) continue;
            final LivingEntity le = (LivingEntity) ent;
            runBusy(() -> {
                le.setNoDamageTicks(0);
                le.damage(damage, caster);
            });
            le.setVelocity(le.getVelocity().add(new Vector(0, 0.5, 0)));
            if (Vfx.on()) {
                w.spawnParticle(Particle.END_ROD, le.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.2);
            }
            if (le instanceof Player) Fx.actionBar((Player) le, "§6✦ Cột ánh sáng giáng xuống bạn!");
        }
    }

    // ------------------------------------------------------------------
    //  RỒNG: Hơi Thở Rồng
    // ------------------------------------------------------------------

    private void stopBreath(Player p) {
        Breath b = breaths.remove(p.getUniqueId());
        if (b == null) return;
        if (b.task != null) b.task.cancel();
        for (ItemDisplay h : b.heads) Vfx.remove(h);
        b.heads.clear();
    }

    private void castBreath(Player p) {
        if (!checkReady(p, CD_BREATH)) return;
        Element el = Element.RONG;
        startCooldown(p, CD_BREATH, settings.n(el, "skill.cooldown"));
        stopBreath(p);

        final double radius = cfg(el, "skill.radius", 20);
        final double dps = settings.n(el, "skill.damage-per-second");
        final int total = (int) (cfg(el, "skill.duration", 10) * 20);
        final Location center = p.getLocation().clone();
        final World w = center.getWorld();
        final boolean vis = Vfx.on();

        p.sendTitle("§4§l✦ HƠI THỞ RỒNG ✦", "§cNgọn lửa rồng thiêu rụi tất cả!", 5, 40, 10);
        w.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.7f);
        w.playSound(center, Sound.ENTITY_ENDER_DRAGON_SHOOT, 3.0f, 0.6f);
        w.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        final Breath b = new Breath();
        final int headCount = Math.max(1, Math.min(8, (int) cfg(el, "skill.heads", 5)));
        if (vis) {
            for (int i = 0; i < headCount; i++) {
                b.heads.add(Vfx.item(w, center.clone().add(0, 40, 0), Material.DRAGON_HEAD, 5.5f + (i % 2) * 1.5f));
            }
        }
        breaths.put(p.getUniqueId(), b);

        b.task = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t > total) {
                    stopBreath(p);
                    if (p.isOnline()) Fx.actionBar(p, "§4Hơi thở rồng đã tắt.");
                    return;
                }
                if (vis) breathVisuals(b, center, radius, t);
                if (t % 40 == 0) {
                    w.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.8f);
                }
                if (t > 0 && t % 20 == 0) breathDamage(p, center, radius, dps);
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void breathDamage(Player owner, Location center, double radius, double dmg) {
        World w = center.getWorld();
        double r2 = radius * radius;
        for (Entity ent : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!validTarget(owner, ent)) continue;
            if (ent.getLocation().distanceSquared(center) > r2) continue;
            final LivingEntity le = (LivingEntity) ent;
            runBusy(() -> {
                le.setNoDamageTicks(0);
                le.damage(dmg, owner);
            });
            if (Vfx.on()) {
                Location l = le.getLocation().add(0, 1, 0);
                w.spawnParticle(Particle.DRAGON_BREATH, l, 18, 0.4, 0.7, 0.4, 0.04);
                w.spawnParticle(Particle.FLAME, l, 8, 0.3, 0.6, 0.3, 0.05);
            }
            if (le instanceof Player) Fx.actionBar((Player) le, "§4☠ Hơi Thở Rồng thiêu đốt bạn!");
        }
    }

    /** Hình ảnh vùng hơi thở: sóng xung kích, vòng biên, mây hơi thở, xoáy lốc, rồng khổng lồ bay vòng trên cao. */
    private void breathVisuals(Breath b, Location center, double radius, int t) {
        World w = center.getWorld();
        double cx = center.getX(), cy = center.getY(), cz = center.getZ();
        Color red = Color.fromRGB(190, 20, 40);
        Color violet = Color.fromRGB(120, 0, 160);
        Color ember = Color.fromRGB(255, 110, 30);

        // sóng xung kích lan ra ở 30 tick đầu
        if (t <= 30) {
            double rr = radius * Math.max(0.05, t / 30.0);
            Vfx.dustRing(w, center, rr, 0.3, Vfx.pts((int) (rr * 5) + 12, 16), t * 0.1, red, 2.0f);
            Vfx.ring(w, center, rr, 0.5, Vfx.pts((int) (rr * 4) + 10, 12), 0, Particle.DRAGON_BREATH);
        }
        // vòng biên + vòng ma pháp
        if (t % 4 == 0) {
            Vfx.dustRing(w, center, radius, 0.3, Vfx.pts((int) (radius * 3), 30), t * 0.02, red, 2.0f);
            Vfx.dustRing(w, center, radius, 1.4, Vfx.pts((int) (radius * 2), 20), -t * 0.02, violet, 1.6f);
            Vfx.dustRing(w, center, radius * 0.5, 0.2, Vfx.pts((int) (radius * 2), 20), -t * 0.04, ember, 1.5f);
            Vfx.star(w, center, radius * 0.9, 0.25, t * 0.015, 6, violet, 1.3f);
        }
        // mây hơi thở rồng rải khắp vùng, bám theo mặt đất
        int clouds = Vfx.pts(14, 6);
        for (int i = 0; i < clouds; i++) {
            double a = rnd.nextDouble() * 2 * Math.PI;
            double rr = Math.sqrt(rnd.nextDouble()) * radius;
            double x = cx + Math.cos(a) * rr;
            double z = cz + Math.sin(a) * rr;
            double y = Vfx.groundY(w, x, z, cy) + 0.3;
            w.spawnParticle(Particle.DRAGON_BREATH, x, y, z, 4, 0.8, 0.3, 0.8, 0.02);
            if (i % 3 == 0) w.spawnParticle(Particle.FLAME, x, y, z, 2, 0.4, 0.2, 0.4, 0.03);
        }
        // 3 cánh xoáy lốc xoay quanh tâm
        for (int arm = 0; arm < 3; arm++) {
            double base = t * 0.09 + arm * 2 * Math.PI / 3;
            for (double rr = 2; rr <= radius; rr += 2.5) {
                double a = base + rr * 0.16;
                double x = cx + Math.cos(a) * rr;
                double z = cz + Math.sin(a) * rr;
                Vfx.dustAt(w, x, Vfx.groundY(w, x, z, cy) + 0.5, z, arm % 2 == 0 ? violet : ember, 1.6f);
            }
        }
        // trung tâm: cột lửa rồng vút lên trời
        if (t % 4 == 0) {
            for (int y = 0; y <= 16; y += 2) {
                double spread = 1.4 * (1.0 - y / 22.0);
                w.spawnParticle(Particle.DRAGON_BREATH, cx, cy + y, cz, 4, spread, 0.3, spread, 0.02);
                if (y % 4 == 0) w.spawnParticle(Particle.FLAME, cx, cy + y, cz, 3, spread * 0.6, 0.3, spread * 0.6, 0.03);
            }
        }
        // mặt đất nứt toác: phun trào lửa ở vài điểm ngẫu nhiên
        if (t > 20 && t % 24 == 0) {
            for (int k = 0; k < 3; k++) {
                double a = rnd.nextDouble() * 2 * Math.PI;
                double rr = Math.sqrt(rnd.nextDouble()) * radius * 0.9;
                double x = cx + Math.cos(a) * rr;
                double z = cz + Math.sin(a) * rr;
                Location p0 = new Location(w, x, Vfx.groundY(w, x, z, cy), z);
                Vfx.dustRing(w, p0, 1.6, 0.2, 14, 0, ember, 1.7f);
                for (int y = 0; y < 7; y++) {
                    w.spawnParticle(Particle.FLAME, x, p0.getY() + y, z, 5, 0.3, 0.3, 0.3, 0.06);
                    if (y % 2 == 0) w.spawnParticle(Particle.LAVA, x, p0.getY() + y, z, 1, 0.3, 0.3, 0.3, 0);
                }
                w.playSound(p0, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.5f);
            }
        }
        if (t == 40) w.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.6f);

        // các đầu rồng: lao xuống từ trên trời rồi bay vòng nhiều tầng, nhiều chiều, phun hơi thở xuống đất
        Color[] palette = {red, violet, ember, Color.fromRGB(255, 70, 140), Color.fromRGB(70, 0, 90)};
        int n = b.heads.size();
        double ease = Math.min(1.0, t / 40.0);
        ease = ease * ease * (3 - 2 * ease);
        for (int i = 0; i < n; i++) {
            ItemDisplay head = b.heads.get(i);
            if (!head.isValid()) continue;
            double sign = (i % 2 == 0) ? 1.0 : -1.0;
            double orbit = radius * HEAD_ORBIT[i % HEAD_ORBIT.length] * (1.0 + 0.7 * (1.0 - ease));
            double ang = i * 2 * Math.PI / n + sign * t * (0.04 + 0.006 * (i % 3));
            double hy = cy + HEAD_HEIGHT[i % HEAD_HEIGHT.length] + 26 * (1.0 - ease) + Math.sin(t * 0.1 + i) * 1.2;
            Location pos = new Location(w, cx + Math.cos(ang) * orbit, hy, cz + Math.sin(ang) * orbit);
            Vector dir = new Vector(-Math.sin(ang) * sign, -0.35 * (1.0 - ease), Math.cos(ang) * sign);
            Location facing = pos.clone();
            facing.setDirection(dir);
            head.teleport(facing);

            Color c1 = palette[i % palette.length];
            Color c2 = palette[(i + 1) % palette.length];
            // thân và đuôi uốn lượn phía sau đầu
            int segs = Vfx.pts(18, 8);
            for (int k = 1; k <= segs; k++) {
                double a2 = ang - sign * k * 0.07;
                double yy = hy + Math.sin((t - k * 2) * 0.1 + i) * 0.6 + Math.sin(k * 0.5 + t * 0.2) * 0.5;
                Vfx.dustAt(w, cx + Math.cos(a2) * orbit, yy, cz + Math.sin(a2) * orbit,
                        k % 2 == 0 ? c1 : c2, Math.max(1.1f, 3.0f - k * 0.1f));
            }
            w.spawnParticle(Particle.DRAGON_BREATH, pos, 4, 0.5, 0.4, 0.5, 0.04);

            // phun hơi thở xuống đất
            if (((t / 2) + i * 5) % 12 == 0) {
                double ta = ang + sign * 0.7;
                double tr = radius * (0.25 + 0.65 * rnd.nextDouble());
                double gx = cx + Math.cos(ta) * tr;
                double gz = cz + Math.sin(ta) * tr;
                Location ground = new Location(w, gx, Vfx.groundY(w, gx, gz, cy), gz);
                Vfx.line(w, pos, ground, 0.8, ember, 1.7f, Particle.DRAGON_BREATH);
                w.spawnParticle(Particle.FLAME, ground, 14, 1.0, 0.2, 1.0, 0.06);
                w.spawnParticle(Particle.DRAGON_BREATH, ground, 20, 1.2, 0.3, 1.2, 0.03);
                w.playSound(ground, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.5f);
            }
        }
    }

    // ------------------------------------------------------------------
    //  ÁNH SÁNG: Lá chắn máu vàng (nội tại ẩn)
    // ------------------------------------------------------------------

    private double thresholdHealth(Player p) {
        return manager.maxHealth(p) * settings.n(Element.ANH_SANG, "shield.health-threshold-percent") / 100.0;
    }

    private void tickShield(Player p) {
        if (p.isDead() || remainingMs(p, CD_SHIELD) > 0) return;
        if (p.getHealth() < thresholdHealth(p)) activateShield(p);
    }

    /** Gọi từ sự kiện nhận sát thương: nếu đòn này làm máu xuống dưới ngưỡng thì bật khiên và hấp thụ ngay. */
    public void onVictimDamage(Player v, EntityDamageEvent e) {
        if (!manager.has(v, Element.ANH_SANG)) return;
        double dmg = e.getFinalDamage();
        if (dmg <= 0 || remainingMs(v, CD_SHIELD) > 0) return;
        if (v.getHealth() - dmg >= thresholdHealth(v)) return;

        double shield = activateShield(v);
        double absorbed = Math.min(shield, dmg);
        e.setDamage(e.getDamage() * (dmg - absorbed) / dmg);
        v.setAbsorptionAmount(Math.max(0, shield - absorbed));
    }

    private double activateShield(Player p) {
        Element el = Element.ANH_SANG;
        double amount = manager.maxHealth(p) * settings.n(el, "shield.shield-percent") / 100.0;
        startCooldown(p, CD_SHIELD, settings.n(el, "shield.cooldown"));
        p.setAbsorptionAmount(Math.max(0, amount));

        World w = p.getWorld();
        Location l = p.getLocation().add(0, 1, 0);
        w.playSound(l, Sound.ITEM_TOTEM_USE, 0.7f, 1.6f);
        w.spawnParticle(Fx.particle("TOTEM_OF_UNDYING", "TOTEM"), l, 40, 0.4, 0.7, 0.4, 0.3);
        Fx.dust(w, l, 25, 0.5, Color.fromRGB(255, 215, 0), 1.5f);
        Fx.actionBar(p, "§6✦ Lá chắn ánh sáng kích hoạt!");

        long ticks = (long) (settings.n(el, "shield.duration-seconds") * 20);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.setAbsorptionAmount(0);
        }, Math.max(20L, ticks));
        return amount;
    }
}
