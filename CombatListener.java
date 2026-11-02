package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Random;

/** Toàn bộ logic chiến đấu của nguyên tố: nội tại, buff đặc biệt, phím dùng chiêu. */
public class CombatListener implements Listener {

    private final NguyenToPlugin plugin;
    private final ElementManager manager;
    private final Settings settings;
    private final SkillManager skills;
    private final Random rnd = new Random();

    public CombatListener(NguyenToPlugin plugin, ElementManager manager, Settings settings, SkillManager skills) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.skills = skills;
    }

    private boolean roll(double chance) {
        return chance > 0 && rnd.nextDouble() < chance;
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            ProjectileSource s = ((Projectile) damager).getShooter();
            if (s instanceof Player) return (Player) s;
        }
        return null;
    }

    private void heal(LivingEntity le, double amount) {
        if (amount <= 0) return;
        le.setHealth(Math.min(manager.maxHealth(le), le.getHealth() + amount));
    }

    // ------------------------------------------------------------------
    //  Người tấn công: nội tại khi đánh
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (skills.isBusy()) return;
        if (!(e.getEntity() instanceof LivingEntity) || e.getEntity() instanceof ArmorStand) return;
        LivingEntity victim = (LivingEntity) e.getEntity();

        // Gió: né đòn
        if (victim instanceof Player) {
            Player v = (Player) victim;
            if (manager.has(v, Element.GIO) && roll(settings.chance(Element.GIO, "dodge-chance"))) {
                e.setCancelled(true);
                v.getWorld().spawnParticle(Particle.CLOUD, v.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                v.playSound(v.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.6f);
                Fx.actionBar(v, "§f✦ Né đòn!");
                return;
            }
        }

        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        List<Element> eq = manager.getEffective(attacker);
        if (eq.isEmpty()) return;

        for (Element el : eq) {
            switch (el) {
                case LUA:
                    if (roll(settings.chance(el, "ignite-chance"))) {
                        int ticks = (int) (settings.n(el, "ignite-seconds") * 20);
                        victim.setFireTicks(Math.max(victim.getFireTicks(), ticks));
                    }
                    break;
                case BANG:
                    if (roll(settings.chance(el, "slow-chance"))) {
                        Fx.addEffect(victim, (int) (settings.n(el, "slow-seconds") * 20),
                                (int) settings.n(el, "slow-amplifier"), true, "SLOW", "SLOWNESS");
                        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0),
                                12, 0.3, 0.5, 0.3, 0.02);
                    }
                    break;
                case NUOC:
                    if (roll(settings.chance(el, "heal-on-hit-chance"))) {
                        heal(attacker, settings.n(el, "heal-on-hit-amount"));
                        attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0),
                                3, 0.3, 0.2, 0.3, 0);
                    }
                    break;
                case SET:
                    if (roll(settings.chance(el, "lightning-chance"))) lightning(attacker, victim);
                    break;
                case BONG_TOI:
                    if (roll(settings.chance(el, "dark-chance"))) {
                        e.setDamage(e.getDamage() * (1 + settings.n(el, "dark-bonus-percent") / 100.0));
                        Location l = victim.getLocation().add(0, 1, 0);
                        victim.getWorld().spawnParticle(Particle.SQUID_INK, l, 15, 0.3, 0.5, 0.3, 0.05);
                        victim.getWorld().spawnParticle(Particle.PORTAL, l, 25, 0.3, 0.5, 0.3, 0.4);
                        victim.getWorld().playSound(l, Sound.ENTITY_PHANTOM_BITE, 0.8f, 0.7f);
                    }
                    break;
                case RONG: {
                    if (roll(settings.chance(el, "breath-chance"))) {
                        burst(attacker, victim, settings.n(el, "breath-radius"), settings.n(el, "breath-damage"),
                                0, Particle.DRAGON_BREATH, Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.6f, 1.2f);
                    }
                    break;
                }
                case PLASMA: {
                    if (roll(settings.chance(el, "plasma-chance"))) {
                        burst(attacker, victim, settings.n(el, "plasma-radius"), settings.n(el, "plasma-damage"),
                                settings.n(el, "plasma-ignite-seconds"), Particle.ELECTRIC_SPARK,
                                Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
                    }
                    break;
                }
                case BANG_THUY: {
                    if (roll(settings.chance(el, "freeze-chance"))) freeze(attacker, victim);
                    break;
                }
                case THANH_LONG: {
                    if (roll(settings.chance(el, "smite-chance"))) smite(attacker, victim);
                    break;
                }
                case HAC_LONG: {
                    if (roll(settings.chance(el, "curse-chance"))) curse(attacker, victim);
                    break;
                }
                case DUNG_NHAM: {
                    if (roll(settings.chance(el, "magma-chance"))) {
                        burst(attacker, victim, settings.n(el, "magma-radius"), settings.n(el, "magma-damage"),
                                settings.n(el, "magma-ignite-seconds"), Particle.LAVA,
                                Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
                    }
                    break;
                }
                case VIEM_LONG: {
                    if (roll(settings.chance(el, "inferno-chance"))) {
                        burst(attacker, victim, settings.n(el, "inferno-radius"), settings.n(el, "inferno-damage"),
                                settings.n(el, "inferno-ignite-seconds"), Particle.DRAGON_BREATH,
                                Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.6f, 0.8f);
                    }
                    break;
                }
                case BAO_TO: {
                    if (roll(settings.chance(el, "storm-chance"))) storm(attacker, victim);
                    break;
                }
                case BAO_TUYET: {
                    if (roll(settings.chance(el, "blizzard-chance"))) blizzard(attacker, victim);
                    break;
                }
                default:
                    break;
            }
        }

        // trong Lãnh Địa Bóng Tối: +sát thương
        if (skills.isInDomain(attacker)) {
            e.setDamage(e.getDamage() + settings.n(Element.BONG_TOI, "skill.owner-bonus-damage"));
        }
    }

    private void lightning(Player attacker, LivingEntity victim) {
        double dmg = settings.n(Element.SET, "lightning-damage");
        double r = settings.n(Element.SET, "lightning-radius");
        Location loc = victim.getLocation();
        World w = loc.getWorld();
        w.strikeLightningEffect(loc);
        w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.3);

        // chạy ở tick sau để không gây sát thương lồng trong sự kiện đang xử lý
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(dmg, attacker);
                });
            }
        });
    }

    // ------------------------------------------------------------------
    //  Nội tại của Rồng và các hợp thể (Fusion)
    // ------------------------------------------------------------------

    /** Gây sát thương nhỏ ở tick sau (tránh lồng sự kiện), không kích hoạt lại nội tại. */
    private void deferredHit(Player attacker, LivingEntity target, double dmg) {
        if (dmg <= 0) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!target.isValid() || target.isDead() || !skills.validTarget(attacker, target)) return;
            skills.runBusy(() -> {
                target.setNoDamageTicks(0);
                target.damage(dmg, attacker);
            });
        });
    }

    /** Vụ nổ lan quanh mục tiêu (Hơi Thở Rồng, Plasma): gây sát thương + đốt trong bán kính. */
    private void burst(Player attacker, LivingEntity victim, double radius, double dmg, double igniteSeconds,
                       Particle particle, Sound sound, float volume, float pitch) {
        final Location loc = victim.getLocation().clone();
        final World w = loc.getWorld();
        final double r = Math.max(1.0, radius);
        w.spawnParticle(particle, loc.clone().add(0, 1, 0), 50, r / 3, 0.6, r / 3, 0.15);
        if (particle != Particle.FLAME) {
            w.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 20, r / 3, 0.5, r / 3, 0.05);
        }
        w.playSound(loc, sound, volume, pitch);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(dmg, attacker);
                });
                if (igniteSeconds > 0) le.setFireTicks(Math.max(le.getFireTicks(), (int) (igniteSeconds * 20)));
            }
        });
    }

    /** Bão Tố: sét lan + hất tung kẻ địch xung quanh mục tiêu. */
    private void storm(Player attacker, LivingEntity victim) {
        Element el = Element.BAO_TO;
        final double dmg = settings.n(el, "storm-damage");
        final double r = Math.max(1.0, settings.n(el, "storm-radius"));
        final Location loc = victim.getLocation().clone();
        final World w = loc.getWorld();
        w.strikeLightningEffect(loc);
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 40, r / 3, 0.6, r / 3, 0.1);
        w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 40, r / 3, 0.8, r / 3, 0.3);
        w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(dmg, attacker);
                });
                le.setVelocity(le.getVelocity().add(new org.bukkit.util.Vector(0, 0.6, 0)));
            }
        });
    }

    /** Bão Tuyết: gió lạnh làm chậm + sát thương cả vùng quanh mục tiêu. */
    private void blizzard(Player attacker, LivingEntity victim) {
        Element el = Element.BAO_TUYET;
        final double dmg = settings.n(el, "blizzard-damage");
        final double r = Math.max(1.0, settings.n(el, "blizzard-radius"));
        final int ticks = (int) (settings.n(el, "blizzard-seconds") * 20);
        final int amp = (int) settings.n(el, "blizzard-amplifier");
        final Location loc = victim.getLocation().clone();
        final World w = loc.getWorld();
        w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 60, r / 3, 0.8, r / 3, 0.1);
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 25, r / 3, 0.6, r / 3, 0.05);
        w.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(dmg, attacker);
                });
                Fx.addEffect(le, ticks, amp, true, "SLOW", "SLOWNESS");
            }
        });
    }

    /** Băng Thủy: đóng băng mục tiêu (làm chậm cực mạnh) + sát thương. */
    private void freeze(Player attacker, LivingEntity victim) {
        Element el = Element.BANG_THUY;
        int ticks = (int) (settings.n(el, "freeze-seconds") * 20);
        Fx.addEffect(victim, ticks, (int) settings.n(el, "freeze-amplifier"), true, "SLOW", "SLOWNESS");
        try {
            victim.setFreezeTicks(victim.getMaxFreezeTicks());
        } catch (Throwable ignored) {
            // bản không hỗ trợ hiệu ứng đóng băng thị giác
        }
        Location l = victim.getLocation().add(0, 1, 0);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 40, 0.4, 0.7, 0.4, 0.05);
        victim.getWorld().spawnParticle(Particle.BUBBLE_POP, l, 15, 0.4, 0.7, 0.4, 0.05);
        victim.getWorld().playSound(l, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.8f);
        deferredHit(attacker, victim, settings.n(el, "freeze-damage"));
    }

    /** Thánh Long: sát thương thánh lên mục tiêu + hồi máu cho bản thân. */
    private void smite(Player attacker, LivingEntity victim) {
        Element el = Element.THANH_LONG;
        Location l = victim.getLocation();
        World w = l.getWorld();
        w.strikeLightningEffect(l);
        w.spawnParticle(Particle.END_ROD, l.clone().add(0, 1, 0), 40, 0.3, 1.0, 0.3, 0.1);
        w.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.8f);
        heal(attacker, settings.n(el, "smite-heal"));
        attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
        deferredHit(attacker, victim, settings.n(el, "smite-damage"));
    }

    /** Hắc Long: héo mòn + sát thương bóng tối. */
    private void curse(Player attacker, LivingEntity victim) {
        Element el = Element.HAC_LONG;
        Fx.addEffect(victim, (int) (settings.n(el, "curse-seconds") * 20), 1, true, "WITHER");
        Location l = victim.getLocation().add(0, 1, 0);
        victim.getWorld().spawnParticle(Particle.SQUID_INK, l, 25, 0.3, 0.6, 0.3, 0.05);
        victim.getWorld().spawnParticle(Particle.SOUL, l, 12, 0.3, 0.6, 0.3, 0.03);
        victim.getWorld().playSound(l, Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.6f);
        deferredHit(attacker, victim, settings.n(el, "curse-damage"));
    }

    // ------------------------------------------------------------------
    //  Hút máu (Bóng Tối) - sau khi đòn đánh thực sự gây sát thương
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitDone(EntityDamageByEntityEvent e) {
        if (skills.isBusy()) return;
        Player a = attackerOf(e.getDamager());
        if (a == null || e.getEntity().equals(a) || !(e.getEntity() instanceof LivingEntity)) return;
        if (!manager.has(a, Element.BONG_TOI)) return;

        double pct = settings.n(Element.BONG_TOI, "lifesteal-percent");
        if (skills.isInDomain(a)) pct += settings.n(Element.BONG_TOI, "skill.owner-extra-lifesteal-percent");
        double amount = e.getFinalDamage() * pct / 100.0;
        if (amount <= 0) return;
        heal(a, amount);
        a.getWorld().spawnParticle(Particle.SOUL, a.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0.02);
    }

    // ------------------------------------------------------------------
    //  Người bị đánh: kháng lửa, giảm sát thương, lá chắn
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player v = (Player) e.getEntity();

        // Lửa: kháng lửa / dung nham
        if (manager.has(v, Element.LUA)) {
            switch (e.getCause()) {
                case FIRE:
                case FIRE_TICK:
                case LAVA:
                    v.setFireTicks(0);
                    e.setCancelled(true);
                    return;
                case HOT_FLOOR:
                    e.setCancelled(true);
                    return;
                default:
                    break;
            }
        }

        // Ánh Sáng: giảm sát thương nhận vào + lá chắn máu vàng
        if (manager.has(v, Element.ANH_SANG)) {
            double red = settings.n(Element.ANH_SANG, "damage-reduction-percent");
            double mult = Math.max(0, 1 - red / 100.0);
            e.setDamage(e.getDamage() * mult);
            skills.onVictimDamage(v, e);
        }
    }

    // ------------------------------------------------------------------
    //  Nước: tăng hiệu quả hồi máu
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (!manager.has(p, Element.NUOC)) return;
        e.setAmount(e.getAmount() * (1 + settings.n(Element.NUOC, "heal-bonus-percent") / 100.0));
    }
}
