package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Công cụ vẽ hiệu ứng dùng chung: hạt, đường sét, vòng ma pháp, thực thể hiển thị (Display). */
public final class Vfx {

    private static NguyenToPlugin plugin;
    private static Settings settings;
    private static final Random RND = new Random();
    /** Các Display đang tồn tại do plugin tạo ra (để dọn sạch khi tắt plugin). */
    private static final List<Entity> TRACKED = new ArrayList<>();

    private Vfx() {}

    public static void init(NguyenToPlugin p, Settings s) {
        plugin = p;
        settings = s;
    }

    public static NguyenToPlugin plugin() { return plugin; }

    /** Hiệu ứng hình ảnh có được bật không (performance.particles). */
    public static boolean on() {
        return settings != null && settings.pb("particles");
    }

    public static double density() {
        if (settings == null) return 0.6;
        return Math.max(0.2, Math.min(1.0, settings.perf("particle-density")));
    }

    /** Số lượng hạt / điểm vẽ đã nhân theo mật độ (không bao giờ dưới 'min'). */
    public static int pts(int n, int min) {
        return Math.max(min, (int) Math.round(n * (0.5 + density() * 0.5)));
    }

    /** Màu hạt đại diện cho từng nguyên tố. */
    public static Color colorOf(Element el) {
        switch (el) {
            case LUA: return Color.fromRGB(255, 90, 20);
            case NUOC: return Color.fromRGB(60, 170, 255);
            case DAT: return Color.fromRGB(110, 80, 40);
            case GIO: return Color.fromRGB(235, 245, 255);
            case SET: return Color.fromRGB(255, 235, 80);
            case BANG: return Color.fromRGB(170, 230, 255);
            case BONG_TOI: return Color.fromRGB(110, 0, 170);
            case RONG: return Color.fromRGB(170, 20, 20);
            case ANH_SANG: return Color.fromRGB(255, 215, 0);
            case FIRE_WATER: return Color.fromRGB(210, 225, 255);
            case BAO_CAT: return Color.fromRGB(230, 195, 110);
            case HAN_LOI: return Color.fromRGB(140, 225, 255);
            case LOI_VIEM: return Color.fromRGB(255, 110, 30);
            case BANG_THUY: return Color.fromRGB(90, 220, 255);
            case THANH_LONG: return Color.fromRGB(255, 240, 140);
            case HAC_LONG: return Color.fromRGB(60, 0, 90);
            case DUNG_NHAM: return Color.fromRGB(255, 120, 20);
            case BAO_TO: return Color.fromRGB(120, 200, 255);
            case BAO_TUYET: return Color.fromRGB(220, 245, 255);
            case VIEM_LONG: return Color.fromRGB(255, 70, 20);
            default: return Color.fromRGB(200, 200, 200);
        }
    }

    // ------------------------------------------------------------------
    //  Hạt cơ bản
    // ------------------------------------------------------------------

    public static void dustAt(World w, double x, double y, double z, Color c, float size) {
        Fx.dust(w, new Location(w, x, y, z), 1, 0.0, c, size);
    }

    /** Vòng tròn hạt bụi màu nằm ngang. */
    public static void dustRing(World w, Location c, double r, double yOff, int points, double rot, Color color, float size) {
        for (int i = 0; i < points; i++) {
            double a = rot + 2 * Math.PI * i / points;
            dustAt(w, c.getX() + Math.cos(a) * r, c.getY() + yOff, c.getZ() + Math.sin(a) * r, color, size);
        }
    }

    /** Vòng tròn hạt thường nằm ngang. */
    public static void ring(World w, Location c, double r, double yOff, int points, double rot, Particle particle) {
        for (int i = 0; i < points; i++) {
            double a = rot + 2 * Math.PI * i / points;
            w.spawnParticle(particle, c.getX() + Math.cos(a) * r, c.getY() + yOff, c.getZ() + Math.sin(a) * r,
                    1, 0, 0, 0, 0);
        }
    }

    /** Đường thẳng hạt bụi từ a tới b (extra = hạt phụ rải rác dọc đường, có thể null). */
    public static void line(World w, Location a, Location b, double step, Color color, float size, Particle extra) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.01) return;
        int n = (int) Math.min(120, Math.max(2, len / step));
        d.multiply(1.0 / n);
        Location cur = a.clone();
        for (int i = 0; i <= n; i++) {
            Fx.dust(w, cur, 1, 0.02, color, size);
            if (extra != null && i % 3 == 0) w.spawnParticle(extra, cur, 1, 0, 0, 0, 0);
            cur.add(d);
        }
    }

    /** Ngôi sao nhiều cánh (n = 6 -> sao 6 cánh) nằm ngang - dùng làm vòng ma pháp. */
    public static void star(World w, Location c, double r, double yOff, double rot, int n, Color color, float size) {
        Location[] v = new Location[n];
        for (int i = 0; i < n; i++) {
            double a = rot + 2 * Math.PI * i / n;
            v[i] = new Location(w, c.getX() + Math.cos(a) * r, c.getY() + yOff, c.getZ() + Math.sin(a) * r);
        }
        for (int i = 0; i < n; i++) line(w, v[i], v[(i + 2) % n], 0.45, color, size, null);
    }

    /** Tia sét ngoằn ngoèo từ top xuống bottom. */
    public static void bolt(World w, Location top, Location bottom, Color c1, Color c2, Particle spark) {
        double total = top.getY() - bottom.getY();
        if (total <= 0.5) return;
        int segs = Math.max(6, (int) (total / 0.9));
        double dy = total / segs;
        Location prev = top.clone();
        for (int i = 1; i <= segs; i++) {
            double t = (double) i / segs;
            Location next;
            if (i == segs) {
                next = bottom.clone();
            } else {
                double jx = (RND.nextDouble() - 0.5) * 1.8 * (1.0 - t);
                double jz = (RND.nextDouble() - 0.5) * 1.8 * (1.0 - t);
                next = new Location(w, bottom.getX() + jx, top.getY() - dy * i, bottom.getZ() + jz);
            }
            line(w, prev, next, 0.5, i % 2 == 0 ? c1 : c2, 1.3f, spark);
            prev = next;
        }
    }

    /** Xoắn ốc đi lên (strands = số sợi). */
    public static void helix(World w, Location base, double r, double height, double rot, int strands,
                             Color c1, Color c2, Particle particle) {
        int steps = pts(26, 12);
        for (int s = 0; s < strands; s++) {
            for (int i = 0; i < steps; i++) {
                double t = (double) i / steps;
                double a = rot + t * Math.PI * 4 + s * 2 * Math.PI / strands;
                double rr = r * (1.0 - 0.25 * t);
                double x = base.getX() + Math.cos(a) * rr;
                double y = base.getY() + t * height;
                double z = base.getZ() + Math.sin(a) * rr;
                dustAt(w, x, y, z, i % 2 == 0 ? c1 : c2, 1.2f);
                if (particle != null && i % 4 == 0) w.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Chớp sáng. Bọc try/catch vì một số bản Minecraft mới đổi dữ liệu của hạt này. */
    public static void flash(World w, Location l) {
        try {
            w.spawnParticle(Particle.FLASH, l, 1, 0, 0, 0, 0);
        } catch (Throwable ignored) {
            w.spawnParticle(Particle.END_ROD, l, 20, 0.3, 0.3, 0.3, 0.2);
        }
    }

    /** Hạt hình khối (vỡ băng, cát...). An toàn với mọi phiên bản. */
    public static void blockBurst(World w, Location l, Material m, int count, double spread) {
        Particle p = Fx.particle("BLOCK", "BLOCK_CRACK");
        try {
            w.spawnParticle(p, l, count, spread, spread, spread, 0.1, m.createBlockData());
        } catch (IllegalArgumentException ignored) {
            // phiên bản không hỗ trợ
        }
    }

    /** Hạt bụi rơi hình khối (cát rơi...). */
    public static void fallingDust(World w, Location l, Material m, int count, double dx, double dy, double dz) {
        try {
            w.spawnParticle(Particle.FALLING_DUST, l, count, dx, dy, dz, 0.0, m.createBlockData());
        } catch (IllegalArgumentException ignored) {
            // phiên bản không hỗ trợ
        }
    }

    /** Độ cao mặt đất gần nearY tại (x, z): tìm xuống tối đa 14 block, không thấy thì trả về nearY. */
    public static double groundY(World w, double x, double z, double nearY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return nearY;
        int start = (int) Math.floor(nearY) + 3;
        int min = w.getMinHeight();
        for (int y = start; y >= Math.max(min, start - 14); y--) {
            Block b = w.getBlockAt(bx, y, bz);
            if (!b.isPassable()) return y + 1.0;
        }
        return nearY;
    }

    // ------------------------------------------------------------------
    //  Thực thể hiển thị (Display)
    // ------------------------------------------------------------------

    /** Khối hiển thị sáng hết cỡ, không lưu vào world, tự được dọn khi tắt plugin. */
    public static BlockDisplay block(World w, Location at, Material m, Transformation tr) {
        BlockDisplay bd = w.spawn(at, BlockDisplay.class);
        bd.setBlock(m.createBlockData());
        bd.setPersistent(false);
        bd.setBrightness(new Display.Brightness(15, 15));
        bd.setViewRange(2.0f);
        bd.setTransformation(tr);
        TRACKED.add(bd);
        return bd;
    }

    /** Vật phẩm hiển thị (dùng cho đầu rồng...). */
    public static ItemDisplay item(World w, Location at, Material m, float scale) {
        ItemDisplay d = w.spawn(at, ItemDisplay.class);
        d.setItemStack(new ItemStack(m));
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setPersistent(false);
        d.setGravity(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2.0f);
        d.setTeleportDuration(2);
        d.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
        TRACKED.add(d);
        return d;
    }

    /** Biến đổi cho khối có kích thước (w, h, w) xoay quanh trục Y nhưng giữ tâm đáy tại gốc. */
    public static Transformation centered(float w, float h, float angleY) {
        Quaternionf q = new Quaternionf().rotateY(angleY);
        Vector3f t = new Vector3f(0.5f * w, 0f, 0.5f * w);
        q.transform(t);
        t.negate();
        return new Transformation(t, q, new Vector3f(w, h, w), new Quaternionf());
    }

    public static void remove(Entity e) {
        if (e == null) return;
        TRACKED.remove(e);
        if (e.isValid()) e.remove();
    }

    public static void removeLater(Entity e, long ticks) {
        if (e == null || plugin == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> remove(e), Math.max(1L, ticks));
    }

    /** Dọn toàn bộ Display còn sót (gọi khi tắt plugin). */
    public static void shutdown() {
        for (Entity e : new ArrayList<>(TRACKED)) {
            try {
                if (e.isValid()) e.remove();
            } catch (Throwable ignored) {
                // bỏ qua
            }
        }
        TRACKED.clear();
    }
}
