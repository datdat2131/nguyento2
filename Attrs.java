package vn.nguyento;

import org.bukkit.attribute.Attribute;

/** Tìm Attribute theo tên, chạy được cả bản cũ (GENERIC_xxx) lẫn bản mới (xxx). */
public final class Attrs {
    private Attrs() {}

    public static final Attribute MAX_HEALTH = find("GENERIC_MAX_HEALTH", "MAX_HEALTH");
    public static final Attribute ATTACK_DAMAGE = find("GENERIC_ATTACK_DAMAGE", "ATTACK_DAMAGE");
    public static final Attribute MOVEMENT_SPEED = find("GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED");
    public static final Attribute ATTACK_SPEED = find("GENERIC_ATTACK_SPEED", "ATTACK_SPEED");
    public static final Attribute ARMOR = find("GENERIC_ARMOR", "ARMOR");

    private static Attribute find(String... names) {
        for (String n : names) {
            try {
                Object o = Attribute.class.getField(n).get(null);
                if (o instanceof Attribute) return (Attribute) o;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // thử tên tiếp theo
            }
        }
        return null;
    }
}
