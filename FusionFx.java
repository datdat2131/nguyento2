package vn.nguyento;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Hiệu ứng (hạt, âm thanh, Display) của các hợp thể. Chỉ vẽ - không gây sát thương. */
public final class FusionFx {

    private FusionFx() {}

    private static NguyenToPlugin plugin() { return Vfx.plugin(); }

    // ------------------------------------------------------------------
    //  Fire Water: vụ nổ hơi nước
    // ------------------------------------------------------------------

    public static void steam(Location loc, double r) {
        World w = loc.getWorld();
        w.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.8f, 1.2f);
        if (!Vfx.on()) return;
        final Location c = loc.clone().add(0, 0.2, 0);
        w.spawnParticle(Particle.CLOUD, c.clone().add(0, 0.8, 0), Vfx.pts(40, 15), r / 3, 0.5, r / 3, 0.08);
        w.spawnParticle(Particle.BUBBLE_POP, c.clone().add(0, 1.0, 0), Vfx.pts(25, 8), r / 3, 0.6, r / 3, 0.05);
        final Color mist = Color.fromRGB(210, 235, 255);
        final Color warm = Color.fromRGB(255, 190, 150);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                double rr = r * t / 6.0;
                Vfx.dustRing(w, c, rr, 0.3, Vfx.pts(24, 10), t * 0.5, mist, 1.6f);
                Vfx.dustRing(w, c, rr * 0.65, 0.9, Vfx.pts(16, 8), -t * 0.5, warm, 1.3f);
                w.spawnParticle(Particle.CLOUD, c.getX(), c.getY() + t * 0.35, c.getZ(), 3, rr / 3, 0.1, rr / 3, 0.02);
                if (t >= 6) cancel();
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    // ------------------------------------------------------------------
    //  Bão Cát: cơn lốc cát
    // ------------------------------------------------------------------

    public static void sandstorm(Location loc, double r) {
        World w = loc.getWorld();
        w.playSound(loc, Sound.BLOCK_SAND_BREAK, 1.2f, 0.6f);
        w.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 0.8f, 0.5f);
        if (!Vfx.on()) return;
        final Location c = loc.clone();
        final Color sand = Color.fromRGB(235, 200, 120);
        final Color dark = Color.fromRGB(190, 150, 80);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                double grow = Math.min(1.0, t / 8.0);
                Vfx.helix(w, c, r * grow, 4.0, t * 0.45, 3, sand, dark, Particle.CLOUD);
                Vfx.fallingDust(w, c.clone().add(0, 2.5, 0), Material.SAND, Vfx.pts(10, 4), r / 2.5, 1.2, r / 2.5);
                Vfx.dustRing(w, c, r * grow, 0.2, Vfx.pts(26, 10), -t * 0.4, sand, 1.5f);
                if (t >= 14) cancel();
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    // ------------------------------------------------------------------
    //  Hàn Lôi: tia sét băng, đóng băng, phá băng
    // ------------------------------------------------------------------

    public static void iceBolt(Location target) {
        World w = target.getWorld();
        w.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.9f);
        w.playSound(target, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
        if (!Vfx.on()) return;
        w.strikeLightningEffect(target);
        Location top = target.clone().add(0, 15, 0);
        Vfx.bolt(w, top, target.clone().add(0, 0.5, 0), Color.fromRGB(150, 230, 255), Color.fromRGB(255, 255, 255),
                Particle.ELECTRIC_SPARK);
        Location c = target.clone().add(0, 0.2, 0);
        w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 1, 0), Vfx.pts(40, 15), 0.6, 0.8, 0.6, 0.08);
        w.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 1, 0), Vfx.pts(30, 10), 0.6, 0.8, 0.6, 0.3);
        Vfx.dustRing(w, c, 1.6, 0.1, Vfx.pts(22, 10), 0, Color.fromRGB(170, 235, 255), 1.5f);
        Vfx.dustRing(w, c, 2.4, 0.1, Vfx.pts(30, 12), 0.3, Color.fromRGB(255, 255, 255), 1.2f);
    }

    public static void freeze(LivingEntity le) {
        World w = le.getWorld();
        Location l = le.getLocation().add(0, 1, 0);
        w.playSound(l, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.7f);
        w.playSound(l, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.8f);
        if (!Vfx.on()) return;
        Vfx.blockBurst(w, l, Material.ICE, Vfx.pts(30, 10), 0.4);
        w.spawnParticle(Particle.SNOWFLAKE, l, Vfx.pts(30, 10), 0.4, 0.7, 0.4, 0.04);
        Vfx.dustRing(w, le.getLocation(), 0.9, 0.3, 14, 0, Color.fromRGB(190, 240, 255), 1.4f);
        Vfx.dustRing(w, le.getLocation(), 0.9, 1.2, 14, 0.4, Color.fromRGB(190, 240, 255), 1.4f);
    }

    public static void shatter(Location l) {
        World w = l.getWorld();
        w.playSound(l, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.1f);
        w.playSound(l, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 1.4f);
        if (!Vfx.on()) return;
        Vfx.blockBurst(w, l, Material.ICE, Vfx.pts(60, 20), 0.6);
        Vfx.blockBurst(w, l, Material.BLUE_ICE, Vfx.pts(30, 10), 0.5);
        w.spawnParticle(Particle.SNOWFLAKE, l, Vfx.pts(50, 15), 0.5, 0.8, 0.5, 0.15);
        w.spawnParticle(Particle.ELECTRIC_SPARK, l, Vfx.pts(25, 8), 0.5, 0.8, 0.5, 0.3);
    }

    // ------------------------------------------------------------------
    //  Lôi Viêm: sét lửa, bộc phát, tia điện lan
    // ------------------------------------------------------------------

    public static void thunderFlame(Location target, boolean burst) {
        World w = target.getWorld();
        w.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, burst ? 1.0f : 0.7f, burst ? 1.1f : 1.4f);
        w.playSound(target, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.7f);
        if (!Vfx.on()) return;
        w.strikeLightningEffect(target);
        Location c = target.clone().add(0, 0.2, 0);
        Vfx.bolt(w, target.clone().add(0, 16, 0), c.clone().add(0, 0.3, 0), Color.fromRGB(255, 70, 20),
                Color.fromRGB(255, 190, 60), Particle.ELECTRIC_SPARK);
        w.spawnParticle(Particle.FLAME, c.clone().add(0, 1, 0), Vfx.pts(40, 15), 0.5, 0.7, 0.5, 0.12);
        w.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 1, 0), Vfx.pts(25, 8), 0.5, 0.7, 0.5, 0.3);
        if (!burst) {
            Vfx.dustRing(w, c, 1.4, 0.1, Vfx.pts(20, 10), 0, Color.fromRGB(255, 120, 30), 1.5f);
            return;
        }
        // Bộc phát: vòng lửa lan ra + chớp sáng
        Vfx.flash(w, c.clone().add(0, 1, 0));
        w.spawnParticle(Particle.LAVA, c.clone().add(0, 1, 0), Vfx.pts(25, 10), 0.8, 0.6, 0.8, 0.0);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                double rr = 4.0 * t / 5.0;
                Vfx.dustRing(w, c, rr, 0.2, Vfx.pts(28, 12), t * 0.3, Color.fromRGB(255, 90, 20), 1.7f);
                Vfx.ring(w, c, rr, 0.5, Vfx.pts(22, 10), -t * 0.3, Particle.FLAME);
                if (t >= 5) cancel();
            }
        }.runTaskTimer(plugin(), 0L, 2L);
    }

    /** Tia điện lan từ nguồn sang mục tiêu phụ. */
    public static void arc(Location from, Location to) {
        if (!Vfx.on()) return;
        World w = from.getWorld();
        Vfx.line(w, from.clone().add(0, 1, 0), to.clone().add(0, 1, 0), 0.4, Color.fromRGB(255, 160, 50),
                1.3f, Particle.ELECTRIC_SPARK);
        w.spawnParticle(Particle.FLAME, to.clone().add(0, 1, 0), Vfx.pts(15, 6), 0.3, 0.5, 0.3, 0.08);
        w.playSound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
    }

    // ------------------------------------------------------------------
    //  Thánh Long / Hắc Long: vòng ma pháp -> bóng rồng khổng lồ -> long hồn xuyên qua -> nổ
    // ------------------------------------------------------------------

    /**
     * Chạy hiệu ứng triệu hồi long hồn lên 'target'. Tới khoảnh khắc long hồn xuyên qua mục tiêu thì gọi 'onImpact'
     * (luôn được gọi dù hiệu ứng hạt có bị tắt trong config).
     */
    public static void dragonSoul(final LivingEntity target, final boolean dark, final Runnable onImpact) {
        final World w = target.getWorld();
        final boolean vis = Vfx.on();
        final Color c1 = dark ? Color.fromRGB(110, 0, 170) : Color.fromRGB(255, 215, 0);
        final Color c2 = dark ? Color.fromRGB(15, 0, 25) : Color.fromRGB(255, 255, 255);
        final Particle accent = dark ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD;
        final Particle smoke = dark ? Particle.SQUID_INK : Particle.END_ROD;
        final double ang = Math.random() * Math.PI * 2;
        final Vector fromDir = new Vector(Math.cos(ang), 0, Math.sin(ang));

        w.playSound(target.getLocation(), dark ? Sound.BLOCK_RESPAWN_ANCHOR_CHARGE : Sound.BLOCK_BEACON_POWER_SELECT,
                1.2f, dark ? 0.6f : 1.5f);

        new BukkitRunnable() {
            int t = 0;
            ItemDisplay head = null;
            Location last = target.getLocation().clone();
            boolean impacted = false;
            // mốc thời gian (tick)
            final int appear = 6;
            final int hit = 18;
            final int end = 24;

            private void finish() {
                if (head != null) Vfx.remove(head);
                head = null;
                cancel();
            }

            @Override
            public void run() {
                try {
                    if (target.isValid() && !target.isDead()) last = target.getLocation().clone();
                    Location base = last.clone();

                    if (vis && t <= hit) {
                        // vòng ma pháp trên mặt đất: 2 vòng ngược chiều + sao 6 cánh
                        double grow = Math.min(1.0, (t + 1) / 6.0);
                        Vfx.dustRing(w, base, 3.2 * grow, 0.1, Vfx.pts(36, 14), t * 0.25, c1, 1.6f);
                        Vfx.dustRing(w, base, 2.2 * grow, 0.15, Vfx.pts(26, 10), -t * 0.3, c2, 1.3f);
                        Vfx.star(w, base, 2.9 * grow, 0.12, t * 0.2, 6, c1, 1.2f);
                        if (t % 3 == 0) {
                            w.spawnParticle(accent, base.getX(), base.getY() + 0.2, base.getZ(), 4, 1.5, 0.1, 1.5, 0.01);
                        }
                    }

                    if (t >= appear && t <= end) {
                        double p = Math.min(1.0, (double) (t - appear) / (hit - appear)); // 0 -> 1 tới lúc xuyên qua
                        Location aim = base.clone().add(0, 1.0, 0);
                        Location start = aim.clone().add(fromDir.clone().multiply(15)).add(0, 10, 0);
                        Vector travel = aim.toVector().subtract(start.toVector());
                        double eased = p * p * (3 - 2 * p);
                        Location pos = start.clone().add(travel.clone().multiply(eased));
                        if (t > hit) pos = aim.clone().add(travel.clone().normalize().multiply((t - hit) * 1.2));

                        Vector dir = travel.clone().normalize();
                        if (vis) {
                            if (head == null) {
                                head = Vfx.item(w, pos, Material.DRAGON_HEAD, 4.5f);
                            }
                            Location hl = pos.clone();
                            hl.setDirection(dir);
                            head.teleport(hl);

                            // thân rồng uốn lượn phía sau đầu
                            Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
                            if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0);
                            side.normalize();
                            Vector up = side.clone().crossProduct(dir).normalize();
                            for (int k = 1; k <= 16; k++) {
                                double wave = k * 0.6 + t * 0.7;
                                Location bp = pos.clone()
                                        .subtract(dir.clone().multiply(k * 0.75))
                                        .add(side.clone().multiply(Math.sin(wave) * 0.9))
                                        .add(up.clone().multiply(Math.cos(wave) * 0.45));
                                float size = (float) (2.6 - k * 0.11);
                                Vfx.dustAt(w, bp.getX(), bp.getY(), bp.getZ(), k % 2 == 0 ? c1 : c2, Math.max(1.0f, size));
                                if (k % 3 == 0) w.spawnParticle(smoke, bp, 1, 0.15, 0.15, 0.15, 0.01);
                            }
                            w.spawnParticle(accent, pos, 6, 0.4, 0.4, 0.4, 0.05);
                        }
                        if (t == appear) {
                            w.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, dark ? 0.6f : 1.3f);
                        }
                    }

                    if (t == hit && !impacted) {
                        impacted = true;
                        w.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, dark ? 0.5f : 1.1f);
                        w.playSound(base, dark ? Sound.ENTITY_WITHER_SPAWN : Sound.BLOCK_BEACON_ACTIVATE,
                                dark ? 0.5f : 1.4f, dark ? 0.7f : 1.8f);
                        if (vis) explosion(base, dark, c1, c2, accent);
                        onImpact.run();
                    }

                    t++;
                    if (t > end) finish();
                } catch (Throwable ex) {
                    finish();
                    if (!impacted) {
                        try {
                            onImpact.run();
                        } catch (Throwable ignored) {
                            // bỏ qua
                        }
                    }
                }
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    private static void explosion(Location base, boolean dark, Color c1, Color c2, Particle accent) {
        final World w = base.getWorld();
        final Location c = base.clone().add(0, 1.0, 0);
        Vfx.flash(w, c);
        w.spawnParticle(accent, c, Vfx.pts(80, 30), 0.6, 0.8, 0.6, 0.35);
        w.spawnParticle(dark ? Particle.PORTAL : Particle.END_ROD, c, Vfx.pts(60, 20), 0.5, 0.7, 0.5, 0.9);
        w.spawnParticle(dark ? Particle.SQUID_INK : Particle.GLOW, c, Vfx.pts(30, 10), 0.6, 0.8, 0.6, 0.2);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                double rr = 5.0 * t / 6.0;
                Vfx.dustRing(w, base, rr, 0.2, Vfx.pts(34, 14), t * 0.3, c1, 1.8f);
                Vfx.dustRing(w, base, rr * 0.7, 0.9, Vfx.pts(24, 10), -t * 0.3, c2, 1.4f);
                Vfx.ring(w, base, rr, 0.4, Vfx.pts(26, 10), 0, accent);
                if (t >= 6) cancel();
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    /** Hút máu Thôn Phệ: vệt linh hồn bay từ mục tiêu về người chơi. */
    public static void devourTrail(Location from, Location to) {
        World w = from.getWorld();
        w.playSound(to, Sound.ENTITY_PHANTOM_BITE, 0.9f, 0.6f);
        if (!Vfx.on()) return;
        Vfx.line(w, from.clone().add(0, 1, 0), to.clone().add(0, 1.2, 0), 0.35, Color.fromRGB(120, 0, 180), 1.5f,
                Particle.SOUL_FIRE_FLAME);
        w.spawnParticle(Particle.SQUID_INK, from.clone().add(0, 1, 0), Vfx.pts(20, 8), 0.4, 0.6, 0.4, 0.05);
    }

    /** Dấu ấn Thánh Ấn / Hắc Ấn nổ lên khi bị đánh trúng. */
    public static void markPop(Location l, boolean dark) {
        World w = l.getWorld();
        if (!Vfx.on()) return;
        Location c = l.clone().add(0, 1, 0);
        w.spawnParticle(dark ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD, c, Vfx.pts(20, 8), 0.35, 0.6, 0.35, 0.08);
        Vfx.dustRing(w, l, 0.9, 1.0, 12, 0, dark ? Color.fromRGB(110, 0, 170) : Color.fromRGB(255, 215, 0), 1.3f);
    }

    // ------------------------------------------------------------------
    //  Khiên / hào quang khi máu thấp
    // ------------------------------------------------------------------

    public static void guardAura(final Player p, final Element el, double seconds) {
        World w = p.getWorld();
        Location l = p.getLocation();
        switch (el) {
            case FIRE_WATER:
                w.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.7f);
                w.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                break;
            case BAO_CAT:
                w.playSound(l, Sound.BLOCK_SAND_BREAK, 1.2f, 0.5f);
                w.playSound(l, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 0.8f);
                break;
            case HAN_LOI:
                w.playSound(l, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 1.2f);
                w.playSound(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.9f);
                break;
            case THANH_LONG:
                w.playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 1.4f);
                w.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.8f);
                break;
            case HAC_LONG:
                w.playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 0.5f);
                w.playSound(l, Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.8f);
                break;
            default:
                break;
        }
        if (!Vfx.on()) return;

        final Color c1;
        final Color c2;
        final Particle part;
        switch (el) {
            case FIRE_WATER:
                c1 = Color.fromRGB(215, 235, 255);
                c2 = Color.fromRGB(255, 190, 150);
                part = Particle.CLOUD;
                break;
            case BAO_CAT:
                c1 = Color.fromRGB(235, 200, 120);
                c2 = Color.fromRGB(190, 150, 80);
                part = Particle.WHITE_ASH;
                break;
            case HAN_LOI:
                c1 = Color.fromRGB(150, 230, 255);
                c2 = Color.fromRGB(255, 255, 255);
                part = Particle.SNOWFLAKE;
                break;
            case THANH_LONG:
                c1 = Color.fromRGB(255, 215, 0);
                c2 = Color.fromRGB(255, 255, 255);
                part = Particle.END_ROD;
                break;
            default:
                c1 = Color.fromRGB(110, 0, 170);
                c2 = Color.fromRGB(20, 0, 30);
                part = Particle.SOUL_FIRE_FLAME;
                break;
        }

        // bùng nổ lúc kích hoạt
        Location c = l.clone().add(0, 1, 0);
        w.spawnParticle(part, c, Vfx.pts(40, 15), 0.5, 0.8, 0.5, 0.15);
        final int total = (int) Math.max(20, seconds * 20);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= total) {
                    cancel();
                    return;
                }
                Location b = p.getLocation();
                World pw = b.getWorld();
                Vfx.helix(pw, b, 1.1, 2.1, t * 0.3, 2, c1, c2, part);
                Vfx.dustRing(pw, b, 1.4, 0.05, Vfx.pts(16, 8), -t * 0.2, c1, 1.2f);
                t += 4;
            }
        }.runTaskTimer(plugin(), 0L, 4L);
    }

    // ------------------------------------------------------------------
    //  Kết hợp thành công
    // ------------------------------------------------------------------

    public static void fuseSuccess(final Player p, final Element f) {
        World w = p.getWorld();
        Location l = p.getLocation();
        w.playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.7f);
        w.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        w.playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.6f);
        if (!Vfx.on()) return;
        final List<Element> parts = f.getParts();
        final Color a = parts.size() > 0 ? Vfx.colorOf(parts.get(0)) : Vfx.colorOf(f);
        final Color b = parts.size() > 1 ? Vfx.colorOf(parts.get(1)) : Vfx.colorOf(f);
        final Color m = Vfx.colorOf(f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                Location base = p.getLocation();
                World pw = base.getWorld();
                double k = 1.0 - Math.min(1.0, t / 20.0); // 1 -> 0 : hai luồng hội tụ vào người
                double r = 0.4 + 2.6 * k;
                Vfx.helix(pw, base, r, 2.2, t * 0.4, 1, a, a, parts.size() > 0 ? parts.get(0).getParticle() : null);
                Vfx.helix(pw, base, r, 2.2, t * 0.4 + Math.PI, 1, b, b, parts.size() > 1 ? parts.get(1).getParticle() : null);
                if (t >= 20) {
                    Location c = base.clone().add(0, 1, 0);
                    pVfx.flash(w, c);
                    pw.spawnParticle(f.getParticle(), c, Vfx.pts(80, 30), 0.6, 0.9, 0.6, 0.2);
                    Vfx.dustRing(pw, base, 2.0, 0.1, Vfx.pts(32, 14), 0, m, 1.8f);
                    Vfx.dustRing(pw, base, 3.0, 0.1, Vfx.pts(40, 16), 0.3, m, 1.5f);
                    cancel();
                    return;
                }
                t += 2;
            }
        }.runTaskTimer(plugin(), 0L, 2L);
    }
}
