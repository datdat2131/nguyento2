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
