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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Toàn bộ logic chiến đấu của nguyên tố: nội tại, buff đặc biệt, trạng thái, khiên khi máu thấp. */
public class CombatListener implements Listener {

    private final NguyenToPlugin plugin;
    private final ElementManager manager;
    private final Settings settings;
    private final SkillManager skills;
    private final Marks marks;
    private final Random rnd = new Random();

    public CombatListener(NguyenToPlugin plugin, ElementManager manager, Settings settings, SkillManager skills,
                          Marks marks) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.skills = skills;
        this.marks = marks;
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

    /** Hệ số sát thương gây ra từ các buff tạm thời (vd Hắc Long Hộ Thể +20%). */
    private double outgoing(Player a) {
        double up = 0;
        UUID id = a.getUniqueId();
        for (Element el : manager.getEquipped(a)) up += marks.value(id, Marks.dmgUp(el));
        return 1.0 + up;
    }

    // ------------------------------------------------------------------
    //  Trạng thái / hiệu ứng dùng chung
    // ------------------------------------------------------------------

    /** Ướt: đang ở trong nước, đang bị mưa dội, hoặc vừa dính hơi nước (Fire Water). */
    private boolean isWet(LivingEntity le) {
        if (marks.has(le, Marks.WET) || le.isInWater()) return true;
        World w = le.getWorld();
        if (!w.hasStorm()) return false;
        Location l = le.getLocation();
        return w.getHighestBlockYAt(l) <= l.getBlockY();
    }

    private boolean hasSlow(LivingEntity le) {
        PotionEffectType t = Fx.type("SLOW", "SLOWNESS");
        return t != null && le.hasPotionEffect(t);
    }

    /** Làm chậm theo % (Slowness mỗi cấp = 15%: 15% = I, 30% = II, 35% làm tròn về II...). */
    private void slow(LivingEntity le, double seconds, double percent) {
        int amp = Math.max(0, (int) Math.round(percent / 15.0) - 1);
        Fx.addEffect(le, (int) (seconds * 20), amp, true, "SLOW", "SLOWNESS");
    }

    /** Choáng: không di chuyển được trong thời gian ngắn. */
    private void stun(LivingEntity le, int ticks) {
        if (ticks <= 0) return;
        Fx.addEffect(le, ticks, 6, false, "SLOW", "SLOWNESS");
        le.setVelocity(new Vector(0, Math.min(0, le.getVelocity().getY()), 0));
    }

    /** Gây sát thương ở tick sau (tránh lồng sự kiện) mà không kích hoạt lại nội tại. */
    private void deferredHit(Player attacker, LivingEntity target, double dmg) {
        if (dmg <= 0) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!target.isValid() || target.isDead() || !skills.validTarget(attacker, target)) return;
            skills.runBusy(() -> {
                target.setNoDamageTicks(0);
                target.damage(dmg * outgoing(attacker), attacker);
            });
        });
    }

    private void giveSpeed(Player p, Element el, double percent, double seconds) {
        if (percent <= 0 || seconds <= 0) return;
        marks.set(p.getUniqueId(), Marks.speed(el), seconds, percent / 100.0);
        manager.applyStats(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) manager.applyStats(p);
        }, (long) (seconds * 20) + 2L);
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
        List<Element> eq = manager.getEquipped(attacker);
        if (eq.isEmpty()) return;

        // 1) Khuếch đại theo trạng thái đang có trên mục tiêu (đọc TRƯỚC khi nội tại của đòn này kích hoạt)
        if (eq.contains(Element.HAN_LOI) && marks.has(victim, Marks.FROZEN)) shatter(attacker, victim, e);
        if (eq.contains(Element.THANH_LONG) && marks.has(victim, Marks.HOLY)) holyStrike(victim, e);
        if (eq.contains(Element.HAC_LONG) && marks.has(victim, Marks.DARK)) devour(attacker, victim, e);

        // 2) Nội tại kích hoạt theo xác suất
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
                case FIRE_WATER:
                    if (roll(settings.procChance(el, "steam-chance"))) steamBurst(attacker, victim);
                    break;
                case BAO_CAT:
                    if (roll(settings.procChance(el, "sand-chance"))) sandstorm(attacker, victim);
                    break;
                case HAN_LOI:
                    if (roll(settings.procChance(el, "bolt-chance"))) iceBolt(attacker, victim);
                    break;
                case LOI_VIEM:
                    if (roll(settings.procChance(el, "strike-chance"))) thunderFlame(attacker, victim);
                    break;
                case THANH_LONG:
                case HAC_LONG:
                    if (roll(settings.procChance(el, "soul-chance"))) dragonSoul(attacker, victim, el);
                    break;
                case BANG_THUY:
                    if (roll(settings.procChance(el, "freeze-chance"))) freezeHit(attacker, victim);
                    break;
                case DUNG_NHAM:
                    if (roll(settings.procChance(el, "magma-chance"))) {
                        burst(attacker, victim, settings.n(el, "magma-radius"), settings.n(el, "magma-damage"),
                                settings.n(el, "magma-ignite-seconds"), Particle.LAVA,
                                Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
                    }
                    break;
                case VIEM_LONG:
                    if (roll(settings.procChance(el, "inferno-chance"))) {
                        burst(attacker, victim, settings.n(el, "inferno-radius"), settings.n(el, "inferno-damage"),
                                settings.n(el, "inferno-ignite-seconds"), Particle.DRAGON_BREATH,
                                Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
                    }
                    break;
                case BAO_TO:
                    if (roll(settings.procChance(el, "storm-chance"))) storm(attacker, victim);
                    break;
                case BAO_TUYET:
                    if (roll(settings.procChance(el, "blizzard-chance"))) blizzard(attacker, victim);
                    break;
                default:
                    break;
            }
        }

        // 3) Buff sát thương tạm thời (vd Hắc Long Hộ Thể)
        double mult = outgoing(attacker);
        if (mult != 1.0) e.setDamage(e.getDamage() * mult);

        // trong Lãnh Địa Bóng Tối: +sát thương
        if (skills.isInDomain(attacker)) {
            e.setDamage(e.getDamage() + settings.n(Element.BONG_TOI, "skill.owner-bonus-damage"));
        }
    }

    // ------------------------------------------------------------------
    //  Sét (nguyên tố gốc)
    // ------------------------------------------------------------------

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
    //  Vụ nổ lan chung (Dung Nham, Viêm Long)
    // ------------------------------------------------------------------

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
            double mult = outgoing(attacker);
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(dmg * mult, attacker);
                });
                if (igniteSeconds > 0) le.setFireTicks(Math.max(le.getFireTicks(), (int) (igniteSeconds * 20)));
            }
        });
    }

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
            double mult = outgoing(attacker);
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(dmg * mult, attacker);
                });
                le.setVelocity(le.getVelocity().add(new Vector(0, 0.6, 0)));
            }
        });
    }

    private void blizzard(Player attacker, LivingEntity victim) {
        Element el = Element.BAO_TUYET;
        final double dmg = settings.n(el, "blizzard-damage");
        final double r = Math.max(1.0, settings.n(el, "blizzard-radius"));
        final double secs = settings.n(el, "blizzard-seconds");
        final int amp = (int) settings.n(el, "blizzard-amplifier");
        final Location loc = victim.getLocation().clone();
        final World w = loc.getWorld();
        w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 60, r / 3, 0.8, r / 3, 0.1);
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 25, r / 3, 0.6, r / 3, 0.05);
        w.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
        Bukkit.getScheduler().runTask(plugin, () -> {
            double mult = outgoing(attacker);
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(dmg * mult, attacker);
                });
                Fx.addEffect(le, (int) (secs * 20), amp, true, "SLOW", "SLOWNESS");
            }
        });
    }

    /** Băng Thủy: đóng băng mục tiêu (làm chậm cực mạnh + trạng thái Đóng Băng) + sát thương. */
    private void freezeHit(Player attacker, LivingEntity victim) {
        Element el = Element.BANG_THUY;
        double secs = settings.n(el, "freeze-seconds");
        int ticks = (int) (secs * 20);
        Fx.addEffect(victim, ticks, (int) settings.n(el, "freeze-amplifier"), true, "SLOW", "SLOWNESS");
        marks.set(victim, Marks.FROZEN, secs);
        try {
            victim.setFreezeTicks(victim.getMaxFreezeTicks());
        } catch (Throwable ignored) {
            // bản không hỗ trợ hiệu ứng đóng băng thị giác
        }
        FusionFx.freeze(victim);
        deferredHit(attacker, victim, settings.n(el, "freeze-damage"));
    }

    // ------------------------------------------------------------------
    //  FIRE WATER (Lửa + Nước): vụ nổ hơi nước
    // ------------------------------------------------------------------

    private void steamBurst(Player attacker, LivingEntity victim) {
        Element el = Element.FIRE_WATER;
        final double r = Math.max(1.5, settings.n(el, "steam-radius"));
        final double dmg = settings.n(el, "steam-damage");
        final double burnBonus = settings.n(el, "steam-burn-bonus-percent") / 100.0;
        final double stunChance = settings.chance(el, "steam-stun-chance");
        final int stunTicks = (int) (settings.n(el, "steam-stun-seconds") * 20);
        final double slowSeconds = settings.n(el, "steam-slow-seconds");
        final double wetSeconds = settings.n(el, "steam-wet-seconds");
        final Location loc = victim.getLocation().clone();
        final World w = loc.getWorld();
        FusionFx.steam(loc, r);

        Bukkit.getScheduler().runTask(plugin, () -> {
            double mult = outgoing(attacker);
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                // đọc trạng thái trước khi vụ nổ này tác động
                boolean burning = le.getFireTicks() > 0;
                boolean wet = isWet(le);
                final double d = dmg * (burning ? 1.0 + burnBonus : 1.0) * mult;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(d, attacker);
                });
                slow(le, slowSeconds, 15);               // Giảm Tốc I
                if (wet && roll(stunChance)) {
                    stun(le, stunTicks);
                    w.spawnParticle(Particle.CLOUD, le.getLocation().add(0, 2.1, 0), 8, 0.2, 0.1, 0.2, 0.02);
                }
                marks.set(le, Marks.WET, wetSeconds);    // dính hơi nước -> Ướt
            }
        });
    }

    // ------------------------------------------------------------------
    //  BÃO CÁT (Đất + Gió): cơn bão cát nhỏ
    // ------------------------------------------------------------------

    private void sandstorm(Player attacker, LivingEntity victim) {
        Element el = Element.BAO_CAT;
        final double r = Math.max(1.5, settings.n(el, "sand-radius"));
        final double dmg = settings.n(el, "sand-damage");
        final double slowPct = settings.n(el, "sand-slow-percent");
        final double slowSecs = settings.n(el, "sand-slow-seconds");
        final double knockChance = settings.chance(el, "sand-knockup-chance");
        final int needed = (int) settings.n(el, "sand-multi-count");
        final Location loc = victim.getLocation().clone();
        final World w = loc.getWorld();
        FusionFx.sandstorm(loc, r);

        Bukkit.getScheduler().runTask(plugin, () -> {
            double mult = outgoing(attacker);
            int hits = 0;
            for (Entity ent : w.getNearbyEntities(loc, r, r, r)) {
                if (!skills.validTarget(attacker, ent)) continue;
                final LivingEntity le = (LivingEntity) ent;
                skills.runBusy(() -> {
                    le.setNoDamageTicks(0);
                    le.damage(dmg * mult, attacker);
                });
                slow(le, slowSecs, slowPct);
                if (roll(knockChance)) le.setVelocity(le.getVelocity().add(new Vector(0, 0.45, 0)));
                hits++;
            }
            if (needed > 0 && hits >= needed && attacker.isOnline()) {
                giveSpeed(attacker, el, settings.n(el, "sand-speed-bonus-percent"), settings.n(el, "sand-speed-seconds"));
                Fx.actionBar(attacker, "§6✦ Bão cát cuốn theo bước chân bạn!");
            }
        });
    }

    // ------------------------------------------------------------------
    //  HÀN LÔI (Sét + Băng): tia sét băng, đóng băng, phá băng
    // ------------------------------------------------------------------

    private void iceBolt(Player attacker, LivingEntity victim) {
        Element el = Element.HAN_LOI;
        final boolean wasSlowed = hasSlow(victim);   // kiểm tra TRƯỚC khi áp Chậm mới
        final double dmg = settings.n(el, "bolt-damage") + settings.n(el, "frost-damage");
        final Location loc = victim.getLocation().clone();

        slow(victim, settings.n(el, "slow-seconds"), settings.n(el, "slow-percent"));
        FusionFx.iceBolt(loc);

        if (wasSlowed && roll(settings.chance(el, "freeze-chance"))) {
            double secs = settings.n(el, "freeze-seconds");
            marks.set(victim, Marks.FROZEN, secs);
            stun(victim, (int) (secs * 20));
            try {
                victim.setFreezeTicks(victim.getMaxFreezeTicks());
            } catch (Throwable ignored) {
                // bản không hỗ trợ hiệu ứng đóng băng thị giác
            }
            FusionFx.freeze(victim);
        }
        deferredHit(attacker, victim, dmg);
    }

    /** Đánh trúng mục tiêu đang Đóng Băng: phá băng, thêm % sát thương. */
    private void shatter(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        Element el = Element.HAN_LOI;
        e.setDamage(e.getDamage() * (1.0 + settings.n(el, "shatter-bonus-percent") / 100.0));
        marks.clear(victim, Marks.FROZEN);
        FusionFx.shatter(victim.getLocation().add(0, 1, 0));
        Fx.actionBar(attacker, "§b✦ Phá băng!");
    }

    // ------------------------------------------------------------------
    //  LÔI VIÊM (Lửa + Sét): Thiên Hỏa Lôi Bạo
    // ------------------------------------------------------------------

    private void thunderFlame(Player attacker, LivingEntity victim) {
        Element el = Element.LOI_VIEM;
        UUID aid = attacker.getUniqueId();
        final int burnTicks = (int) (settings.n(el, "burn-seconds") * 20);
        final double staticSecs = settings.n(el, "static-seconds");
        final double base = settings.n(el, "fire-damage") + settings.n(el, "lightning-damage");

        boolean burning = victim.getFireTicks() > 0;
        boolean charged = marks.has(victim, Marks.STATIC);
        boolean cooling = marks.has(aid, Marks.cooldown(el, "burst"));
        final Location loc = victim.getLocation().clone();

        if (burning && charged && !cooling) {
            // Lôi Viêm Bộc Phát
            marks.set(aid, Marks.cooldown(el, "burst"), settings.n(el, "burst-cooldown"), 1.0);
            marks.clear(victim, Marks.STATIC);
            final double total = base * (1.0 + settings.n(el, "burst-bonus-percent") / 100.0);
            final double spread = total * settings.n(el, "burst-spread-percent") / 100.0;
            final double radius = Math.max(1.0, settings.n(el, "burst-radius"));
            final int maxTargets = (int) settings.n(el, "burst-targets");

            List<LivingEntity> others = new ArrayList<>();
            for (Entity ent : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                if (ent.equals(victim) || !skills.validTarget(attacker, ent)) continue;
                others.add((LivingEntity) ent);
            }
            others.sort(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(loc)));
            final List<LivingEntity> chain = new ArrayList<>(others.subList(0, Math.min(maxTargets, others.size())));

            victim.setFireTicks(Math.max(victim.getFireTicks(), burnTicks));
            FusionFx.thunderFlame(loc, true);
            for (LivingEntity o : chain) {
                FusionFx.arc(loc, o.getLocation());
                o.setFireTicks(Math.max(o.getFireTicks(), burnTicks));
                marks.set(o, Marks.STATIC, staticSecs);
            }
            deferredHit(attacker, victim, total);
            for (LivingEntity o : chain) deferredHit(attacker, o, spread);
            Fx.actionBar(attacker, "§c✦ Lôi Viêm Bộc Phát!");
            return;
        }

        // Lôi Viêm thường: Thiêu Đốt + Nhiễm Điện
        victim.setFireTicks(Math.max(victim.getFireTicks(), burnTicks));
        marks.set(victim, Marks.STATIC, staticSecs);
        FusionFx.thunderFlame(loc, false);
        deferredHit(attacker, victim, base);
    }

    // ------------------------------------------------------------------
    //  THÁNH LONG / HẮC LONG: long hồn
    // ------------------------------------------------------------------

    private void dragonSoul(Player attacker, LivingEntity victim, Element el) {
        final boolean dark = el == Element.HAC_LONG;
        final double dmg = settings.n(el, "soul-damage");
        final double markSecs = settings.n(el, "mark-seconds");
        FusionFx.dragonSoul(victim, dark, () -> {
            if (!attacker.isOnline() || !victim.isValid() || victim.isDead()
                    || !skills.validTarget(attacker, victim)) return;
            skills.runBusy(() -> {
                victim.setNoDamageTicks(0);
                victim.damage(dmg * outgoing(attacker), attacker);
            });
            marks.set(victim, dark ? Marks.DARK : Marks.HOLY, markSecs);
        });
    }

    /** Thánh Ấn: đánh trúng thêm % sát thương. */
    private void holyStrike(LivingEntity victim, EntityDamageByEntityEvent e) {
        Element el = Element.THANH_LONG;
        e.setDamage(e.getDamage() * (1.0 + settings.n(el, "holy-bonus-percent") / 100.0));
        FusionFx.markPop(victim.getLocation(), false);
        if (settings.n(el, "holy-consume-mark") > 0) marks.clear(victim, Marks.HOLY);
    }

    /** Hắc Ấn: Thôn Phệ - thêm % sát thương và hồi % máu tối đa. */
    private void devour(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        Element el = Element.HAC_LONG;
        e.setDamage(e.getDamage() * (1.0 + settings.n(el, "devour-bonus-percent") / 100.0));
        heal(attacker, manager.maxHealth(attacker) * settings.n(el, "devour-heal-percent") / 100.0);
        FusionFx.markPop(victim.getLocation(), true);
        FusionFx.devourTrail(victim.getLocation(), attacker.getLocation());
        if (settings.n(el, "devour-consume-mark") > 0) marks.clear(victim, Marks.DARK);
    }

    // ------------------------------------------------------------------
    //  Hút máu - sau khi đòn đánh thực sự gây sát thương
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitDone(EntityDamageByEntityEvent e) {
        if (skills.isBusy()) return;
        Player a = attackerOf(e.getDamager());
        if (a == null || e.getEntity().equals(a) || !(e.getEntity() instanceof LivingEntity)) return;

        double pct = 0;
        for (Element el : manager.getEquipped(a)) pct += settings.n(el, "lifesteal-percent");
        if (pct <= 0) return;
        if (manager.has(a, Element.BONG_TOI) && skills.isInDomain(a)) {
            pct += settings.n(Element.BONG_TOI, "skill.owner-extra-lifesteal-percent");
        }
        double amount = e.getFinalDamage() * pct / 100.0;
        if (amount <= 0) return;
        heal(a, amount);
        a.getWorld().spawnParticle(Particle.SOUL, a.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0.02);
    }

    // ------------------------------------------------------------------
    //  Người bị đánh: kháng lửa, giảm sát thương, lá chắn, khiên khi máu thấp
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

        List<Element> eq = manager.getEquipped(v);
        if (eq.isEmpty()) return;

        // Giảm sát thương vĩnh viễn (Ánh Sáng, Thánh Long...)
        double red = 0;
        for (Element el : eq) red += settings.n(el, "damage-reduction-percent");
        if (red != 0) e.setDamage(e.getDamage() * Math.max(0, 1 - red / 100.0));

        // Ánh Sáng: lá chắn máu vàng
        if (eq.contains(Element.ANH_SANG)) skills.onVictimDamage(v, e);

        // Khiên khi máu thấp của hợp thể
        applyGuards(v, eq, e);
    }

    private void applyGuards(Player v, List<Element> eq, EntityDamageEvent e) {
        UUID id = v.getUniqueId();
        double finalDmg = e.getFinalDamage();
        if (finalDmg > 0) {
            double max = manager.maxHealth(v);
            double after = v.getHealth() - finalDmg;
            for (Element el : eq) {
                if (!el.isFusion()) continue;
                double thr = settings.n(el, "guard.health-threshold-percent");
                if (thr <= 0 || after >= max * thr / 100.0) continue;
                if (marks.has(id, Marks.cooldown(el, "guard"))) continue;
                activateGuard(v, el);
            }
        }
        // áp dụng giảm sát thương của mọi khiên đang chạy
        double keep = 1.0;
        for (Element el : eq) {
            double dr = marks.value(id, Marks.guard(el));
            if (dr > 0) keep *= Math.max(0.0, 1.0 - dr);
        }
        if (keep < 1.0) e.setDamage(e.getDamage() * keep);
    }

    private void activateGuard(Player v, Element el) {
        UUID id = v.getUniqueId();
        double secs = settings.n(el, "guard.seconds");
        marks.set(id, Marks.guard(el), secs, settings.n(el, "guard.reduction-percent") / 100.0);
        marks.set(id, Marks.cooldown(el, "guard"), settings.n(el, "guard.cooldown"), 1.0);
        double dmgUp = settings.n(el, "guard.damage-percent");
        if (dmgUp > 0) marks.set(id, Marks.dmgUp(el), secs, dmgUp / 100.0);
        giveSpeed(v, el, settings.n(el, "guard.speed-percent"), secs);
        FusionFx.guardAura(v, el, secs);
        Fx.actionBar(v, el.getColor() + "✦ " + guardName(el) + " kích hoạt!");
    }

    private static String guardName(Element el) {
        switch (el) {
            case FIRE_WATER: return "Màn Sương";
            case BAO_CAT: return "Khiên Cát";
            case HAN_LOI: return "Lôi Băng Hộ Thể";
            case THANH_LONG: return "Long Hộ";
            case HAC_LONG: return "Hắc Long Hộ Thể";
            default: return "Hộ Thể";
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

    // ------------------------------------------------------------------
    //  Dọn trạng thái
    // ------------------------------------------------------------------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        marks.clearAll(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        marks.clearAll(e.getPlayer().getUniqueId());
    }
}
