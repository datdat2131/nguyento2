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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Chiêu thức: Lãnh Địa Bóng Tối, Thần Phạt, Lá chắn ánh sáng + hồi chiêu. */
public class SkillManager {

    private static final String CD_DOMAIN = "domain";
    private static final String CD_DIVINE = "divine";
    private static final String CD_SHIELD = "shield";

    private static class Domain {
        final Location center;
        final double radius;
        BukkitTask task;

        Domain(Location center, double radius) {
            this.center = center;
            this.radius = radius;
        }
    }

    private final NguyenToPlugin plugin;
    private final ElementManager manager;
    private final Settings settings;
    private final Random rnd = new Random();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Domain> domains = new HashMap<>();
    private boolean busy = false;
    private int hudCounter = 0;

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

    private double dens() {
        return Math.max(0.2, Math.min(1.0, settings.perf("particle-density")));
    }

    private int ringPoints(double r) {
        return Math.max(12, (int) (r * 3 * dens()));
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

    // ------------------------------------------------------------------
    //  Vòng lặp: lá chắn + HUD
    // ------------------------------------------------------------------

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            hudCounter += 4;
            boolean hud = settings.gb("show-skill-hud") && hudCounter % 20 == 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (manager.has(p, Element.ANH_SANG)) tickShield(p);
                if (hud) hud(p);
            }
        }, 4L, 4L);
    }

    public void shutdown() {
        for (Domain d : domains.values()) {
            if (d.task != null) d.task.cancel();
        }
        domains.clear();
    }

    private void hud(Player p) {
        if (!manager.hasSpecial(p)) return;
        StringBuilder sb = new StringBuilder();
        for (Element e : manager.getEquipped(p)) {
            String part = null;
            if (e == Element.BONG_TOI) part = hudPart(p, "§5✦ Lãnh Địa", CD_DOMAIN);
            else if (e == Element.ANH_SANG) part = hudPart(p, "§6✦ Thần Phạt", CD_DIVINE);
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
        return false;
    }

    // ------------------------------------------------------------------
    //  BÓNG TỐI: Lãnh Địa Bóng Tối
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

        final int maxTicks = (int) (settings.n(el, "skill.duration") * 20);
        final Domain d = new Domain(p.getLocation().clone(), settings.n(el, "skill.radius"));
        domains.put(p.getUniqueId(), d);

        World w = d.center.getWorld();
        p.sendTitle("§5§l✦ LÃNH ĐỊA BÓNG TỐI ✦", "§7Bóng đêm nuốt chửng tất cả...", 5, 30, 10);
        w.playSound(d.center, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 0.6f);
        w.playSound(d.center, Sound.ENTITY_WITHER_SHOOT, 0.7f, 0.5f);

        d.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || ticks >= maxTicks) {
                    endDomain(p, true);
                    return;
                }
                tickDomain(p, d, ticks);
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void endDomain(Player p, boolean announce) {
        Domain d = domains.remove(p.getUniqueId());
        if (d == null) return;
        if (d.task != null) d.task.cancel();
        if (announce && p.isOnline()) {
            Fx.actionBar(p, "§5Lãnh địa bóng tối đã tan biến.");
            d.center.getWorld().playSound(d.center, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f);
        }
    }

    private void tickDomain(Player owner, Domain d, int ticks) {
        Element el = Element.BONG_TOI;
        World w = d.center.getWorld();
        double cx = d.center.getX(), cy = d.center.getY(), cz = d.center.getZ();
        double r = d.radius;

        // --- hiệu ứng hình ảnh (số lần gọi hạt được giảm để tránh lag) ---
        if (settings.pb("particles")) {
            double dens = dens();
            int pts = ringPoints(r);
            double rot = ticks * 0.03;
            for (int i = 0; i < pts; i++) {
                double a = 2 * Math.PI * i / pts + rot;
                double x = cx + Math.cos(a) * r;
                double z = cz + Math.sin(a) * r;
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, x, cy + 0.15, z, 1, 0, 0, 0, 0);
                if (i % 6 == 0) w.spawnParticle(Particle.PORTAL, x, cy + 1.0, z, 4, 0.1, 0.8, 0.1, 0.1);
            }
            int fill = Math.max(4, (int) (8 * dens));
            for (int i = 0; i < fill; i++) {
                double a = rnd.nextDouble() * 2 * Math.PI;
                double rr = Math.sqrt(rnd.nextDouble()) * r;
                w.spawnParticle(Particle.SQUID_INK, cx + Math.cos(a) * rr, cy + 0.1, cz + Math.sin(a) * rr,
                        1, 0, 0.02, 0, 0.01);
            }
        }

        // --- hiệu ứng lên người ---
        int ownerSpeed = (int) settings.n(el, "skill.owner-speed-amplifier");
        int enemySlow = (int) settings.n(el, "skill.enemy-slow-amplifier");
        boolean nausea = settings.n(el, "skill.enemy-nausea") > 0;

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
            }
        }
    }

    // ------------------------------------------------------------------
    //  ÁNH SÁNG: Thần Phạt
    // ------------------------------------------------------------------

    private void castDivine(Player p) {
        if (!checkReady(p, CD_DIVINE)) return;
        Element el = Element.ANH_SANG;
        startCooldown(p, CD_DIVINE, settings.n(el, "skill.cooldown"));

        final double radius = settings.n(el, "skill.radius");
        final double damage = settings.n(el, "skill.damage");
        final int delay = (int) settings.n(el, "skill.cast-delay-ticks");
        final Location c = p.getLocation().clone();
        final World w = c.getWorld();

        p.sendTitle("§6§l✦ THẦN PHẠT ✦", "§eÁnh sáng phán xét tất cả!", 3, 25, 10);
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 1.4f);

        if (delay <= 0) {
            strike(p, c, radius, damage);
            return;
        }

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                // vòng báo trước + vòng thu hẹp vào tâm (4 tick một lần cho nhẹ)
                if (settings.pb("particles") && t % 4 == 0) {
                    ring(w, c, radius, ringPoints(radius), 0.15, Particle.END_ROD);
                    double shrink = radius * (1.0 - (double) t / delay);
                    ring(w, c, shrink, ringPoints(shrink), 0.3, Particle.END_ROD);
                    Fx.dust(w, c.clone().add(0, 0.2, 0), 6, radius / 2, Color.fromRGB(255, 215, 0), 1.4f);
                }

                if (t >= delay) {
                    cancel();
                    strike(p, c, radius, damage);
                    return;
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void ring(World w, Location c, double r, int pts, double yOff, Particle particle) {
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            w.spawnParticle(particle, c.getX() + Math.cos(a) * r, c.getY() + yOff, c.getZ() + Math.sin(a) * r,
                    1, 0, 0, 0, 0);
        }
    }

    private void strike(Player caster, Location c, double radius, double damage) {
        World w = c.getWorld();
        w.strikeLightningEffect(c);
        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1.3f);
        w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.8f);

        // cột hào quang
        spawnPillar(w, c, 3.2f, 60f, Material.YELLOW_STAINED_GLASS);
        spawnPillar(w, c, 0.8f, 60f, Material.WHITE_CONCRETE);
        if (settings.pb("particles")) {
            for (int y = 0; y < 45; y += 2) {
                w.spawnParticle(Particle.END_ROD, c.getX(), c.getY() + y, c.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
            }

            // sóng xung kích lan ra (5 bước, mỗi bước 2 tick)
            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    t++;
                    double r = radius * t / 5.0;
                    int pts = ringPoints(r);
                    ring(w, c, r, pts, 0.2, Particle.END_ROD);
                    ring(w, c, r, pts, 1.2, Particle.END_ROD);
                    if (t >= 5) cancel();
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }

        // gây sát thương
        for (Entity ent : w.getNearbyEntities(c, radius, radius, radius)) {
            if (!validTarget(caster, ent)) continue;
            final LivingEntity le = (LivingEntity) ent;
            if (le.getLocation().distance(c) > radius) continue;
            runBusy(() -> {
                le.setNoDamageTicks(0);
                le.damage(damage, caster);
            });
            le.setVelocity(le.getVelocity().add(new Vector(0, 0.5, 0)));
            w.spawnParticle(Particle.END_ROD, le.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.1);
        }
    }

    private void spawnPillar(World w, Location c, float width, float height, Material mat) {
        final BlockDisplay bd = w.spawn(c.clone(), BlockDisplay.class);
        bd.setBlock(mat.createBlockData());
        bd.setPersistent(false);
        bd.setBrightness(new Display.Brightness(15, 15));
        bd.setTransformation(new Transformation(new Vector3f(-width / 2f, 0f, -width / 2f), new Quaternionf(),
                new Vector3f(width, height, width), new Quaternionf()));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!bd.isValid()) return;
            bd.setInterpolationDuration(30);
            bd.setInterpolationDelay(0);
            bd.setTransformation(new Transformation(new Vector3f(-0.02f, 0f, -0.02f), new Quaternionf(),
                    new Vector3f(0.04f, height, 0.04f), new Quaternionf()));
        }, 6L);
        Bukkit.getScheduler().runTaskLater(plugin, bd::remove, 40L);
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
