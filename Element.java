package vn.nguyento;

import org.bukkit.Material;
import org.bukkit.Particle;

/** Danh sách nguyên tố. Muốn thêm nguyên tố mới chỉ cần thêm 1 dòng ở đây
 *  (nhớ thêm slot hiển thị trong ElementGui.ELEMENT_SLOTS nếu GUI hết chỗ). */
public enum Element {
    LUA("lua", "Lửa", "§c", Material.MAGMA_BLOCK, Particle.FLAME),
    NUOC("nuoc", "Nước", "§b", Material.SEA_LANTERN, Particle.BUBBLE_POP),
    DAT("dat", "Đất", "§2", Material.GRASS_BLOCK, Particle.COMPOSTER),
    GIO("gio", "Gió", "§f", Material.WHITE_STAINED_GLASS, Particle.CLOUD),
    SET("set", "Sét", "§e", Material.GLOWSTONE, Particle.ELECTRIC_SPARK),
    BANG("bang", "Băng", "§9", Material.BLUE_ICE, Particle.SNOWFLAKE);

    private final String id;
    private final String name;
    private final String color;
    private final Material material;
    private final Particle particle;

    Element(String id, String name, String color, Material material, Particle particle) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.material = material;
        this.particle = particle;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public Material getMaterial() { return material; }
    public Particle getParticle() { return particle; }
    public String colored() { return color + name; }

    public static Element fromId(String id) {
        if (id == null) return null;
        for (Element e : values()) {
            if (e.id.equalsIgnoreCase(id.trim())) return e;
        }
        return null;
    }
}
