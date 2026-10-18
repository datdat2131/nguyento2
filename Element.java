package vn.nguyento;

import org.bukkit.Material;
import org.bukkit.Particle;

/** Danh sách nguyên tố. Giá trị buff nằm trong config.yml. */
public enum Element {
    LUA("lua", "Lửa", "§c", Material.MAGMA_BLOCK, Particle.FLAME, 10, false),
    NUOC("nuoc", "Nước", "§b", Material.SEA_LANTERN, Particle.BUBBLE_POP, 11, false),
    DAT("dat", "Đất", "§2", Material.GRASS_BLOCK, Particle.COMPOSTER, 12, false),
    GIO("gio", "Gió", "§f", Material.WHITE_STAINED_GLASS, Particle.CLOUD, 14, false),
    SET("set", "Sét", "§e", Material.GLOWSTONE, Particle.ELECTRIC_SPARK, 15, false),
    BANG("bang", "Băng", "§9", Material.BLUE_ICE, Particle.SNOWFLAKE, 16, false),
    BONG_TOI("bong_toi", "Bóng Tối", "§5", Material.CRYING_OBSIDIAN, Particle.PORTAL, 30, true),
    ANH_SANG("anh_sang", "Ánh Sáng", "§6", Material.NETHER_STAR, Particle.END_ROD, 32, true);

    private final String id;
    private final String name;
    private final String color;
    private final Material material;
    private final Particle particle;
    private final int guiSlot;
    private final boolean special;

    Element(String id, String name, String color, Material material, Particle particle, int guiSlot, boolean special) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.material = material;
        this.particle = particle;
        this.guiSlot = guiSlot;
        this.special = special;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public Material getMaterial() { return material; }
    public Particle getParticle() { return particle; }
    public int getGuiSlot() { return guiSlot; }
    public boolean isSpecial() { return special; }
    public String colored() { return color + name; }

    public String rarityLine() {
        return special ? "§5§l✦ HUYỀN THOẠI ✦" : "§8Nguyên tố thường";
    }

    public static Element fromId(String id) {
        if (id == null) return null;
        String k = id.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
        for (Element e : values()) {
            if (e.id.replace("_", "").equals(k)) return e;
        }
        return null;
    }
}
