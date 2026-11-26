package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Các khối nguyên tố trên vòng sau lưng tự chiến đấu:
 * - TỰ TẤN CÔNG: lao ra đánh kẻ địch đang giao chiến với chủ nhân rồi bay về vòng.
 * - HỘ VỆ: lao ra chặn một đòn đánh vào chủ nhân (giảm sát thương), vỡ ra rồi mọc lại sau vài giây.
 * - GIÁC NGỘ: Shift + F (hoặc /nguyento awaken) vào trạng thái mạnh hơn trong vài giây (vòng nở to, quay nhanh).
 * Mọi con số chỉnh trong config.yml, mục orb và awaken.
 */
public class OrbBattle implements Listener {

    private final NguyenToPlugin plugin;
    private final ElementManager manager;
    private final Settings settings;
    private final SkillManager skills;
    private final Marks marks;
    /** Chủ nhân -> (kẻ địch -> thời điểm hết hạn "đang giao chiến"). */
    private final Map<UUID, Map<UUID, Long>> engaged = new HashMap<>();

    public OrbBattle(NguyenToPlugin plugin, ElementManager manager, Settings settings, SkillManager skills, Marks marks) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.skills = skills;
        this.marks = marks;
    }

    private double cfg(String key, double def) {
        return settings.cfg().getDouble("orb." + key, def);
    }

    private double awk(String key, double def) {
        return settings.cfg().getDouble("awaken." + key, def);
    }

    private boolean attackOn() {
        return settings.cfg().getBoolean("orb.attack", true);
    }

    private boolean guardOn() {
        return settings.cfg().getBoolean("orb.guard", true);
    }

    private boolean awakenOn() {
        return settings.cfg().getBoolean("awaken.enabled", true);
    }

    // ------------------------------------------------------------------
    //  Ghi nhận giao chiến
    // ------------------------------------------------------------------

    /** Chủ nhân đánh 'src' hoặc 'src' đánh chủ nhân: từ giờ các khối nguyên tố sẽ nhắm vào nó. */
    public void noteEngaged(Player owner, Entity src) {
        Entity e = src;
        if (e instanceof Projectile) {
            ProjectileSource s = ((Projectile) e).getShooter();
            e = s instanceof Entity ? (Entity) s : null;
        }
        if (!(e instanceof LivingEntity) || e.equals(owner) || e instanceof ArmorStand) return;
        engaged.computeIfAbsent(owner.getUniqueId(), k -> new HashMap<>())
                .put(e.getUniqueId(), System.currentTimeMillis() + (long) (cfg("combat-seconds", 8.0) * 1000));
    }

    private LivingEntity nearestTarget(Player p, double range) {
        Location pl = p.getLocation();
        double best = range * range;
        LivingEntity pick = null;
        long now = System.currentTimeMillis();

        Map<UUID, Long> m = engaged.get(p.getUniqueId());
        if (m != null) {
            Iterator<Map.Entry<UUID, Long>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> en = it.next();
                Entity ent = Bukkit.getEntity(en.getKey());
                if (en.getValue() < now || !(ent instanceof LivingEntity) || !ent.isValid() || ent.isDead()) {
                    it.remove();
                    continue;
                }
                if (ent.getWorld() != pl.getWorld() || !skills.validTarget(p, ent)) continue;
                double d = ent.getLocation().distanceSquared(pl);
                if (d <= best) {
                    best = d;
                    pick = (LivingEntity) ent;
                }
            }
        }
        // quái đang nhắm vào chủ nhân cũng bị tấn công
        for (Entity ent : p.getNearbyEntities(range, range, range)) {
            if (!(ent instanceof Mob) || !skills.validTarget(p, ent)) continue;
            if (!p.equals(((Mob) ent).getTarget())) continue;
            double d = ent.getLocation().distanceSquared(pl);
            if (d < best) {
                best = d;
                pick = (LivingEntity) ent;
            }
        }
        return pick;
    }

    // ------------------------------------------------------------------
    //  Tự tấn công
    // ------------------------------------------------------------------

    /** Gọi mỗi lần vòng nguyên tố của người chơi được cập nhật. */
    void tick(Player p, List<ElementManager.Orbit> orbits, long tick) {
        if (!attackOn() || orbits.isEmpty() || p.isDead()) return;
        int first = -1;
        for (int i = 0; i < orbits.size(); i++) {
            ElementManager.Orbit o = orbits.get(i);
            if (o.busyUntil <= tick && tick >= o.nextAttack && o.display.isValid()) {
                first = i;
                break;
            }
        }
        if (first < 0) return;

        double range = Math.min(11.0, Math.max(3.0, cfg("range", 10.0)));
        LivingEntity target = nearestTarget(p, range);
        if (target == null) {
            // không có địch: kiểm tra lại sau nửa giây
            for (ElementManager.Orbit o : orbits) {
                if (o.busyUntil <= tick && tick >= o.nextAttack) o.nextAttack = tick + 10;
            }
            return;
        }
        boolean awake = marks.has(p.getUniqueId(), Marks.AWAKEN);
        double interval = Math.max(0.3, cfg("attack-interval-seconds", 3.0));
        if (awake) interval *= Math.max(0.1, awk("attack-interval-multiplier", 0.5));
        ElementManager.Orbit o = orbits.get(first);
        o.nextAttack = tick + (long) (interval * 20) + first * 7L;
        launch(p, o, first, orbits.size(), target);
    }

    private static double ease(double u) {
        return u * u * (3 - 2 * u);
    }

    private void launch(final Player p, final ElementManager.Orbit o, final int index, final int n, final LivingEntity target) {
        final ItemDisplay d = o.display;
        final Location start = d.getLocation().clone();
        final Element el = o.element;
        o.busyUntil = Long.MAX_VALUE;
        d.setTeleportDuration(1);
        final int out = 6;
        final int hold = 2;
        final int back = 7;

        new BukkitRunnable() {
            int t = 0;
            Location far = start.clone();

            private void end() {
                cancel();
                release(o, d);
            }

            @Override
            public void run() {
                if (!p.isOnline() || !d.isValid()) {
                    end();
                    return;
                }
                if (t < out) {
                    double u = ease((t + 1.0) / out);
                    Location to = target.isValid() ? target.getLocation().add(0, 1.0, 0) : far;
                    far = to.clone();
                    Location pos = start.clone().add(to.clone().subtract(start).toVector().multiply(u));
                    pos.add(0, Math.sin(Math.PI * u) * 0.8, 0);
                    d.teleport(pos);
                    trail(pos, el);
                } else if (t == out) {
                    impact(p, target, el, far);
                } else if (t < out + hold) {
                    // nán lại một chút ở chỗ mục tiêu
                } else if (t <= out + hold + back) {
                    double u = ease((t - out - hold) / (double) back);
                    Location home = manager.homeOf(p, index, n);
                    Location pos = far.clone().add(home.clone().subtract(far).toVector().multiply(u));
                    d.teleport(pos);
                } else {
                    end();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void trail(Location pos, Element el) {
        if (!Vfx.on()) return;
        World w = pos.getWorld();
        w.spawnParticle(el.getParticle(), pos, 3, 0.1, 0.1, 0.1, 0.01);
        Vfx.dustAt(w, pos.getX(), pos.getY(), pos.getZ(), Vfx.colorOf(el), 1.4f);
    }

    private void impact(Player p, LivingEntity t, Element el, Location at) {
        World w = at.getWorld();
        if (t.isValid() && !t.isDead() && skills.validTarget(p, t)) {
            double base = settings.n(el, "orb-damage");
            if (base <= 0) base = cfg("damage", 4.0);
            final double dmg = base * manager.outgoingMultiplier(p) * settings.pvp(t) * manager.counterMultiplier(p, t);
            skills.runBusy(() -> {
                t.setNoDamageTicks(0);
                t.damage(dmg, p);
            });
            effects(p, t, el, dmg);
        }
        w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.4f);
        if (Vfx.on()) {
            w.spawnParticle(el.getParticle(), at, Vfx.pts(25, 10), 0.3, 0.3, 0.3, 0.08);
            Vfx.dustRing(w, at, 1.0, 0.0, 14, 0, Vfx.colorOf(el), 1.4f);
        }
    }

    private void heal(Player p, double amount) {
        if (amount <= 0 || p.isDead()) return;
        p.setHealth(Math.min(manager.maxHealth(p), p.getHealth() + amount));
    }

    private void knock(Player p, LivingEntity t, double power) {
        Vector v = t.getLocation().toVector().subtract(p.getLocation().toVector());
        v.setY(0);
        if (v.lengthSquared() < 0.001) return;
        v.normalize().multiply(power);
        v.setY(0.25);
        t.setVelocity(t.getVelocity().add(v));
    }

    /** Hiệu ứng phụ theo nguyên tố (hợp thể dùng hiệu ứng của cả hai nguyên tố gốc). */
    private void effects(Player p, LivingEntity t, Element el, double dmg) {
        if (el.isFusion()) {
            for (Element part : el.getParts()) effects(p, t, part, dmg);
            return;
        }
        switch (el) {
            case LUA:
                t.setFireTicks(Math.max(t.getFireTicks(), 60));
                break;
            case BANG:
                Fx.addEffect(t, 40, 1, true, "SLOW", "SLOWNESS");
                break;
            case NUOC:
                heal(p, 1.5);
                break;
            case SET:
                t.getWorld().strikeLightningEffect(t.getLocation());
                break;
            case GIO:
                knock(p, t, 0.7);
                break;
            case DAT:
                t.setVelocity(t.getVelocity().add(new Vector(0, 0.4, 0)));
                break;
            case BONG_TOI:
                heal(p, dmg * 0.2);
                break;
            case ANH_SANG:
                heal(p, 2.0);
                break;
            case RONG:
                t.setFireTicks(Math.max(t.getFireTicks(), 60));
                knock(p, t, 0.4);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------
    //  Hộ vệ
    // ------------------------------------------------------------------

    /**
     * Một khối nguyên tố đang rảnh lao ra chặn đòn đánh của một thực thể vào chủ nhân.
     * @return hệ số nhân sát thương của đòn này (1.0 = không chặn được)
     */
    public double guard(Player p, EntityDamageEvent e) {
        if (!guardOn() || !(e instanceof EntityDamageByEntityEvent) || e.getFinalDamage() <= 0) return 1.0;
        List<ElementManager.Orbit> list = manager.orbitsOf(p);
        if (list == null || list.isEmpty()) return 1.0;
        UUID id = p.getUniqueId();
        if (marks.has(id, "orb_guard_cd")) return 1.0;
        long now = manager.tickNow();
        for (int i = 0; i < list.size(); i++) {
            ElementManager.Orbit o = list.get(i);
            if (o.busyUntil > now || !o.display.isValid()) continue;
            marks.set(id, "orb_guard_cd", Math.max(0.2, cfg("guard-interval-seconds", 1.5)), 1.0);
            guardOrb(p, o, ((EntityDamageByEntityEvent) e).getDamager().getLocation());
            return Math.max(0.0, 1.0 - cfg("guard-reduction-percent", 35.0) / 100.0);
        }
        return 1.0;
    }

    private void guardOrb(final Player p, final ElementManager.Orbit o, final Location src) {
        final ItemDisplay d = o.display;
        final Element el = o.element;
        o.busyUntil = Long.MAX_VALUE;
        d.setTeleportDuration(1);

        Location pl = p.getLocation();
        Vector dir = src.toVector().subtract(pl.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = pl.getDirection();
            dir.setY(0);
        }
        if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
        dir.normalize();
        final Location front = pl.clone().add(dir.multiply(1.3)).add(0, p.getHeight() * 0.6, 0);
        final Location from = d.getLocation().clone();

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !d.isValid()) {
                    cancel();
                    release(o, d);
                    return;
                }
                if (t < 4) {
                    double u = ease((t + 1.0) / 4.0);
                    d.teleport(from.clone().add(front.clone().subtract(from).toVector().multiply(u)));
                    t++;
                    return;
                }
                cancel();
                shatter(p, o, front);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void release(ElementManager.Orbit o, ItemDisplay d) {
        o.busyUntil = 0;
        o.nextKey = 0; // buộc vòng quay áp lại dáng xoay ngay
        if (d.isValid()) {
            d.setTeleportDuration(manager.teleportInterval());
            // đặt vị trí gửi gần nhất = vị trí hiện tại của khối: vòng quay sẽ tự đưa nó về đúng chỗ (không bị dựng lại)
            o.lastSent = d.getLocation().clone();
        }
    }

    /** Khối nguyên tố chặn đòn rồi vỡ ra (ẩn đi), vài giây sau mọc lại trên vòng. */
    private void shatter(final Player p, final ElementManager.Orbit o, Location at) {
        final ItemDisplay d = o.display;
        final Element el = o.element;
        World w = at.getWorld();
        w.playSound(at, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
        w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.5f);
        Fx.actionBar(p, el.getColor() + "✦ Khối " + el.getName() + " đã chặn đòn thay bạn!");
        if (Vfx.on()) {
            w.spawnParticle(el.getParticle(), at, Vfx.pts(30, 12), 0.35, 0.35, 0.35, 0.1);
            Vfx.dustRing(w, at, 0.9, 0.0, 16, 0, Vfx.colorOf(el), 1.6f);
            Vfx.dustRing(w, at, 0.6, 0.0, 12, 0.5, Color.fromRGB(255, 255, 255), 1.2f);
        }
        if (d.isValid()) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(0);
            d.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0f, 0f, 0f), new Quaternionf()));
        }
        long delay = (long) (Math.max(1.0, cfg("respawn-seconds", 6.0)) * 20);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            release(o, d);
            if (!p.isOnline() || !d.isValid()) return;
            Location home = manager.homeOf(p, Math.max(0, manager.indexOf(p, o)), Math.max(1, manager.orbitCount(p)));
            p.getWorld().playSound(home, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.6f);
            if (Vfx.on()) {
                p.getWorld().spawnParticle(el.getParticle(), home, Vfx.pts(20, 8), 0.25, 0.25, 0.25, 0.05);
            }
        }, delay);
    }

    // ------------------------------------------------------------------
    //  Giác ngộ
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking() || !awakenOn() || !settings.cfg().getBoolean("awaken.sneak-swap", true)) return;
        if (manager.getEquipped(p).isEmpty()) return;
        e.setCancelled(true);
        awaken(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        engaged.remove(e.getPlayer().getUniqueId());
    }

    /** Vào trạng thái Giác Ngộ. @return true nếu kích hoạt được. */
    public boolean awaken(final Player p) {
        final UUID id = p.getUniqueId();
        if (!awakenOn()) {
            p.sendMessage("§cGiác Ngộ đang bị tắt.");
            return false;
        }
        List<Element> eq = manager.getEquipped(p);
        if (eq.isEmpty()) {
            p.sendMessage("§cBạn cần trang bị ít nhất 1 nguyên tố để Giác Ngộ.");
            return false;
        }
        if (marks.has(id, Marks.AWAKEN)) {
            Fx.actionBar(p, "§dBạn đang trong trạng thái Giác Ngộ!");
            return false;
        }
        long left = marks.remainingMs(id, "awaken_cd");
        if (left > 0) {
            Fx.actionBar(p, "§cGiác Ngộ đang hồi: §f" + (left / 1000 + 1) + "s");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            return false;
        }

        double secs = Math.max(1.0, awk("seconds", 15.0));
        double cd = Math.max(secs, awk("cooldown", 180.0));
        marks.set(id, Marks.AWAKEN, secs, 1.0);
        marks.set(id, "awaken_cd", cd, 1.0);
        manager.applyStats(p);

        p.sendTitle("§d§l✦ GIÁC NGỘ ✦", "§7Sức mạnh nguyên tố đã thức tỉnh!", 5, 40, 15);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.5f);
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.6f);
        if (Vfx.on()) burst(p, eq);

        // hào quang trong suốt thời gian giác ngộ
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !marks.has(id, Marks.AWAKEN)) {
                    cancel();
                    return;
                }
                List<Element> cur = manager.getEquipped(p);
                if (Vfx.on() && !cur.isEmpty()) {
                    Location l = p.getLocation();
                    Element e = cur.get((t / 4) % cur.size());
                    Color c = Vfx.colorOf(e);
                    Vfx.helix(l.getWorld(), l, 1.0, 2.2, t * 0.3, 2, c, Color.fromRGB(255, 255, 255), e.getParticle());
                    Vfx.dustRing(l.getWorld(), l, 1.5, 0.05, Vfx.pts(18, 8), -t * 0.2, c, 1.3f);
                }
                t += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            manager.applyStats(p);
            p.sendMessage("§7Trạng thái §dGiác Ngộ §7đã kết thúc.");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.2f);
        }, (long) (secs * 20) + 2L);
        return true;
    }

    /** Sóng xung kích khi bắt đầu giác ngộ: các vòng màu nguyên tố tỏa ra. */
    private void burst(final Player p, final List<Element> eq) {
        Vfx.flash(p.getWorld(), p.getLocation().add(0, 1, 0));
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                t++;
                Location base = p.getLocation();
                World pw = base.getWorld();
                double rr = 0.8 + t * 0.7;
                for (int i = 0; i < eq.size(); i++) {
                    Vfx.dustRing(pw, base, rr, 0.1 + i * 0.5, Vfx.pts(28, 12), (i % 2 == 0 ? 1 : -1) * t * 0.4,
                            Vfx.colorOf(eq.get(i)), 1.7f);
                }
                if (t >= 7) cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
