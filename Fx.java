package vn.nguyento;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Tiện ích hiệu ứng, tương thích nhiều phiên bản (tên hiệu ứng/hạt đổi theo bản). */
public final class Fx {
    private Fx() {}

    public static PotionEffectType type(String... names) {
        for (String n : names) {
            PotionEffectType t = PotionEffectType.getByName(n);
            if (t != null) return t;
        }
        return null;
    }

    public static void addEffect(LivingEntity e, int ticks, int amplifier, boolean particles, String... names) {
        PotionEffectType t = type(names);
        if (t == null) return;
        e.addPotionEffect(new PotionEffect(t, ticks, Math.max(0, amplifier), false, particles, particles));
    }

    public static Particle particle(String... names) {
        for (String n : names) {
            try {
                return Particle.valueOf(n);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Particle.CLOUD;
    }

    public static void dust(World w, Location l, int count, double spread, Color color, float size) {
        Particle p = particle("DUST", "REDSTONE");
        try {
            w.spawnParticle(p, l, count, spread, spread, spread, 0, new Particle.DustOptions(color, size));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public static void actionBar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }
}
