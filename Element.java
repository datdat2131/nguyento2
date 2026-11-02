package vn.nguyento;

import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Danh sách nguyên tố. Giá trị buff nằm trong config.yml.
 * Nguyên tố HỢP THỂ (fusion) là nguyên tố ghép từ 2 nguyên tố khác: chỉ chiếm 1 ô trang bị
 * nhưng nhận buff / nội tại / chiêu của cả hai + buff riêng của nó.
 */
public enum Element {
    LUA("lua", "Lửa", "§c", Material.MAGMA_BLOCK, Particle.FLAME, 10, false),
    NUOC("nuoc", "Nước", "§b", Material.SEA_LANTERN, Particle.BUBBLE_POP, 11, false),
    DAT("dat", "Đất", "§2", Material.GRASS_BLOCK, Particle.COMPOSTER, 12, false),
    GIO("gio", "Gió", "§f", Material.WHITE_STAINED_GLASS, Particle.CLOUD, 14, false),
    SET("set", "Sét", "§e", Material.GLOWSTONE, Particle.ELECTRIC_SPARK, 15, false),
    BANG("bang", "Băng", "§9", Material.BLUE_ICE, Particle.SNOWFLAKE, 16, false),
    BONG_TOI("bong_toi", "Bóng Tối", "§5", Material.CRYING_OBSIDIAN, Particle.PORTAL, 30, true),
    RONG("rong", "Rồng", "§4", Material.DRAGON_HEAD, Particle.DRAGON_BREATH, 31, true, false),
    ANH_SANG("anh_sang", "Ánh Sáng", "§6", Material.NETHER_STAR, Particle.END_ROD, 32, true),

    // ---- HỢP THỂ (ghép 2 nguyên tố) ----
    PLASMA("plasma", "Plasma", "§d", Material.PEARLESCENT_FROGLIGHT, Particle.ELECTRIC_SPARK, 38, "lua", "set"),
    BANG_THUY("bang_thuy", "Băng Thủy", "§3", Material.HEART_OF_THE_SEA, Particle.SNOWFLAKE, 39, "nuoc", "bang"),
    THANH_LONG("thanh_long", "Thánh Long", "§e", Material.BEACON, Particle.END_ROD, 41, "anh_sang", "rong"),
    HAC_LONG("hac_long", "Hắc Long", "§8", Material.DRAGON_EGG, Particle.PORTAL, 42, "bong_toi", "rong"),
    DUNG_NHAM("dung_nham", "Dung Nham", "§6", Material.SHROOMLIGHT, Particle.LAVA, 47, "lua", "dat"),
    BAO_TO("bao_to", "Bão Tố", "§b", Material.LIGHTNING_ROD, Particle.CLOUD, 48, "gio", "set"),
    BAO_TUYET("bao_tuyet", "Bão Tuyết", "§f", Material.PACKED_ICE, Particle.SNOWFLAKE, 50, "gio", "bang"),
    VIEM_LONG("viem_long", "Viêm Long", "§c", Material.FIRE_CHARGE, Particle.FLAME, 51, "lua", "rong");

    private final String id;
    private final String name;
    private final String color;
    private final Material material;
    private final Particle particle;
    private final int guiSlot;
    /** Nguyên tố huyền thoại (viền sáng, hạt đặc biệt). */
    private final boolean special;
    /** Nguyên tố có vật phẩm kỹ năng (chuột phải dùng chiêu): chỉ Bóng Tối / Ánh Sáng. */
    private final boolean hasSkill;
    /** id của 2 nguyên tố thành phần, null nếu không phải hợp thể. */
    private final String[] partIds;

    Element(String id, String name, String color, Material material, Particle particle, int guiSlot, boolean special) {
        this(id, name, color, material, particle, guiSlot, special, special);
    }

    Element(String id, String name, String color, Material material, Particle particle, int guiSlot,
            boolean special, boolean hasSkill) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.material = material;
        this.particle = particle;
        this.guiSlot = guiSlot;
        this.special = special;
        this.hasSkill = hasSkill;
        this.partIds = null;
    }

    /** Hợp thể: ghép từ 2 nguyên tố (khai báo bằng id). */
    Element(String id, String name, String color, Material material, Particle particle, int guiSlot,
            String partA, String partB) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.material = material;
        this.particle = particle;
        this.guiSlot = guiSlot;
        this.special = true;
        this.hasSkill = false;
        this.partIds = new String[]{partA, partB};
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public Material getMaterial() { return material; }
    public Particle getParticle() { return particle; }
    public int getGuiSlot() { return guiSlot; }
    /** Nguyên tố huyền thoại (kể cả hợp thể). */
    public boolean isSpecial() { return special; }
    /** Có vật phẩm kỹ năng để dùng chiêu (Bóng Tối / Ánh Sáng). */
    public boolean hasSkill() { return hasSkill; }
    public boolean isFusion() { return partIds != null; }
    public String colored() { return color + name; }

    /** 2 nguyên tố thành phần của hợp thể (rỗng nếu không phải hợp thể). */
    public List<Element> getParts() {
        if (partIds == null) return Collections.emptyList();
        List<Element> out = new ArrayList<>(2);
        for (String s : partIds) {
            Element e = fromId(s);
            if (e != null) out.add(e);
        }
        return out;
    }

    /** Nguyên tố này (hoặc các thành phần của nó) có chứa 'other' không. */
    public boolean covers(Element other) {
        if (this == other) return true;
        return isFusion() && getParts().contains(other);
    }

    public String rarityLine() {
        if (isFusion()) return "§d§l✦ HỢP THỂ ✦";
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
