package vn.nguyento;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class NguyenToPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Settings settings;
    private ElementManager manager;
    private SkillManager skills;
    private SkillItems items;
    private ElementGui gui;

    @Override
    public void onEnable() {
        settings = new Settings(this);
        manager = new ElementManager(this, settings);
        skills = new SkillManager(this, manager, settings);
        gui = new ElementGui(manager, settings);
        items = new SkillItems(this, manager, settings);
        manager.setSkillItems(items);

        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(new ElementListener(manager), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this, manager, settings, skills), this);
        Bukkit.getPluginManager().registerEvents(new SkillItemListener(this, items, skills), this);

        getCommand("nguyento").setExecutor(this);
        getCommand("nguyento").setTabCompleter(this);

        manager.startTask();
        skills.startTask();
        items.startTask();
        // hỗ trợ /reload
        for (Player p : Bukkit.getOnlinePlayers()) manager.load(p);
        getLogger().info("NguyenTo da bat!");
    }

    @Override
    public void onDisable() {
        if (skills != null) skills.shutdown();
        if (manager != null) manager.shutdown();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            gui.open((Player) sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("stats")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            for (String line : manager.statLines((Player) sender)) sender.sendMessage(line);
            return true;
        }

        if (sub.equals("reset")) {
            if (!sender.hasPermission("nguyento.admin")) {
                sender.sendMessage("§cBạn không có quyền dùng lệnh này.");
                return true;
            }
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                    : (sender instanceof Player ? (Player) sender : null);
            if (target == null) {
                sender.sendMessage("§eDùng: /nguyento reset [người chơi]");
                return true;
            }
            manager.resetPlayer(target);
            sender.sendMessage("§aĐã đặt lại nguyên tố & chỉ số của " + target.getName() + ".");
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("nguyento.admin")) {
                sender.sendMessage("§cBạn không có quyền dùng lệnh này.");
                return true;
            }
            settings.reload();
            manager.startTask();
            items.startTask();
            manager.reloadStats();
            sender.sendMessage("§aĐã tải lại config.yml! Buff mới đã được áp dụng.");
            return true;
        }

        if (sub.equals("give") || sub.equals("unlock")) {
            if (!sender.hasPermission("nguyento.admin")) {
                sender.sendMessage("§cBạn không có quyền dùng lệnh này.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§eDùng: /nguyento " + sub + " <người chơi> <nguyên tố|all>"
                        + (sub.equals("give") ? " [số lượng]" : ""));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi đó.");
                return true;
            }

            List<Element> targets = new ArrayList<>();
            if (args[2].equalsIgnoreCase("all")) {
                targets.addAll(Arrays.asList(Element.values()));
            } else {
                Element el = Element.fromId(args[2]);
                if (el == null) {
                    sender.sendMessage("§cNguyên tố không tồn tại. Có: " + idList());
                    return true;
                }
                targets.add(el);
            }

            int amount = 1;
            if (sub.equals("give") && args.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cSố lượng không hợp lệ.");
                    return true;
                }
            }

            for (Element el : targets) {
                if (sub.equals("give")) {
                    ItemStack scroll = manager.createScroll(el, amount);
                    target.getInventory().addItem(scroll).values()
                            .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                } else {
                    manager.grant(target, el);
                }
            }
            sender.sendMessage("§aĐã " + (sub.equals("give") ? "đưa cuộn giấy" : "mở khóa nguyên tố")
                    + " cho " + target.getName() + ".");
            if (sub.equals("unlock")) target.sendMessage("§aBạn đã được mở khóa nguyên tố mới! Gõ §f/nguyento");
            return true;
        }

        sender.sendMessage("§e/nguyento §7- mở menu nguyên tố");
        sender.sendMessage("§e/nguyento stats §7- xem chỉ số buff thật đang có");
        if (sender.hasPermission("nguyento.admin")) {
            sender.sendMessage("§e/nguyento give <người chơi> <nguyên tố|all> [số lượng] §7- đưa cuộn giấy");
            sender.sendMessage("§e/nguyento unlock <người chơi> <nguyên tố|all> §7- mở khóa trực tiếp");
            sender.sendMessage("§e/nguyento reset [người chơi] §7- bỏ trang bị + xóa buff kẹt, chỉ số về mặc định");
            sender.sendMessage("§e/nguyento reload §7- tải lại config.yml");
        }
        return true;
    }

    private String idList() {
        StringBuilder sb = new StringBuilder();
        for (Element e : Element.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getId());
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("stats");
            if (sender.hasPermission("nguyento.admin")) out.addAll(Arrays.asList("give", "unlock", "reset", "reload"));
        } else if (sender.hasPermission("nguyento.admin")) {
            if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock")
                    || args[0].equalsIgnoreCase("reset"))) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock"))) {
                out.add("all");
                for (Element e : Element.values()) out.add(e.getId());
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
