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
    private FusionGui fusionGui;
    private Marks marks;
    private QuestManager quest;
    private EnergyManager energy;
    private OrbBattle orbBattle;

    @Override
    public void onEnable() {
        settings = new Settings(this);
        marks = new Marks();
        marks.start(this);
        Vfx.init(this, settings);
        manager = new ElementManager(this, settings, marks);
        skills = new SkillManager(this, manager, settings);
        gui = new ElementGui(manager, settings);
        fusionGui = new FusionGui(manager, settings, gui);
        gui.setFusionGui(fusionGui);
        quest = new QuestManager(this, manager, settings);
        manager.setQuest(quest);
        quest.start();
        gui.setQuest(quest);
        items = new SkillItems(this, manager, settings);
        manager.setSkillItems(items);

        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(fusionGui, this);
        Bukkit.getPluginManager().registerEvents(skills, this);
        Bukkit.getPluginManager().registerEvents(quest, this);
        ElementListener elementListener = new ElementListener(manager, settings);
        elementListener.setFusionGui(fusionGui);
        Bukkit.getPluginManager().registerEvents(elementListener, this);
        energy = new EnergyManager(manager, settings);
        skills.setEnergy(energy);
        CombatListener combat = new CombatListener(this, manager, settings, skills, marks);
        combat.setEnergy(energy);
        orbBattle = new OrbBattle(this, manager, settings, skills, marks);
        manager.setBattle(orbBattle);
        combat.setBattle(orbBattle);
        Bukkit.getPluginManager().registerEvents(orbBattle, this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        Bukkit.getPluginManager().registerEvents(energy, this);
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
        if (marks != null) marks.shutdown();
        if (energy != null) energy.shutdown();
        Vfx.shutdown();
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

        if (sub.equals("awaken") || sub.equals("giacngo")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            orbBattle.awaken((Player) sender);
            return true;
        }

        if (sub.equals("unfuse") || sub.equals("tach")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            if (!settings.unfuseEnabled()) {
                sender.sendMessage("§cTính năng tách hợp thể đang tắt.");
                return true;
            }
            fusionGui.openUnfuse((Player) sender);
            return true;
        }

        if (sub.equals("fusion") || sub.equals("hopthe") || sub.equals("fuse")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            fusionGui.open((Player) sender);
            return true;
        }

        if (sub.equals("quest") || sub.equals("nhiemvu")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("dungeon")) {
                // /nguyento quest dungeon <người chơi> [rank]  - dành cho plugin dungeon chạy bằng lệnh console khi người chơi thắng cổng
                if (!sender.hasPermission("nguyento.admin")) {
                    sender.sendMessage("§cBạn không có quyền dùng lệnh này.");
                    return true;
                }
                Player t = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) {
                    sender.sendMessage("§eDùng: /nguyento quest dungeon <người chơi đang online> [rank]");
                    return true;
                }
                String err = quest.addDungeon(t, args.length >= 4 ? args[3] : null);
                sender.sendMessage(err == null ? "§aĐã ghi nhận 1 cổng dungeon cho " + t.getName() + "." : "§e" + err);
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                if (!sender.hasPermission("nguyento.admin")) {
                    sender.sendMessage("§cBạn không có quyền dùng lệnh này.");
                    return true;
                }
                Player t = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) {
                    sender.sendMessage("§eDùng: /nguyento quest reset <người chơi đang online>");
                    return true;
                }
                quest.reset(t);
                sender.sendMessage("§aĐã xóa tiến độ nhiệm vụ ô thứ 4 của " + t.getName() + ".");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage("Lệnh này chỉ dành cho người chơi.");
                return true;
            }
            sender.sendMessage("§6✦ Nhiệm vụ mở khóa ô nguyên tố thứ 4:");
            for (String line : quest.lines((Player) sender)) sender.sendMessage("  " + line);
            return true;
        }

        if (sub.equals("slot")) {
            if (!sender.hasPermission("nguyento.admin")) {
                sender.sendMessage("§cBạn không có quyền dùng lệnh này.");
                return true;
            }
            String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
            Player t = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
            if (!(action.equals("give") || action.equals("remove") || action.equals("status")) || t == null) {
                sender.sendMessage("§eDùng: /nguyento slot <give|remove|status> <người chơi đang online>");
                return true;
            }
            if (action.equals("status")) {
                sender.sendMessage("§e" + t.getName() + "§7: " + manager.maxSlots(t) + " ô nguyên tố"
                        + (manager.hasExtraSlot(t) ? " (đã mở khóa ô thứ 4)" : " (chưa mở ô thứ 4)"));
                return true;
            }
            if (action.equals("give")) {
                if (!settings.extraSlotEnabled()) {
                    sender.sendMessage("§cTính năng ô thứ 4 đang tắt trong config (extra-slot.enabled).");
                    return true;
                }
                if (manager.hasExtraSlot(t)) {
                    sender.sendMessage("§e" + t.getName() + " §7đã có ô thứ 4 rồi.");
                    return true;
                }
                manager.setExtraSlot(t, true);
                quest.announceUnlock(t, false);
                sender.sendMessage("§aĐã cấp ô nguyên tố thứ 4 cho " + t.getName() + ".");
            } else {
                manager.setExtraSlot(t, false);
                sender.sendMessage("§aĐã thu hồi ô nguyên tố thứ 4 của " + t.getName() + ".");
                t.sendMessage("§cÔ nguyên tố thứ 4 của bạn đã bị thu hồi.");
            }
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

            if (sub.equals("give") && args[2].equalsIgnoreCase("unfuse")) {
                int n = 1;
                if (args.length >= 4) {
                    try {
                        n = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage("§cSố lượng không hợp lệ.");
                        return true;
                    }
                }
                ItemStack scroll = manager.createUnfuseScroll(n);
                target.getInventory().addItem(scroll).values()
                        .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                sender.sendMessage("§aĐã đưa " + n + " Cuộn Tách Hợp Thể cho " + target.getName() + ".");
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
        sender.sendMessage("§e/nguyento fusion §7- mở menu Hợp Thể (kết hợp 2 nguyên tố, mất 2 nguyên tố gốc)");
        sender.sendMessage("§e/nguyento quest §7- xem tiến độ nhiệm vụ mở ô nguyên tố thứ 4");
        sender.sendMessage("§e/nguyento unfuse §7- mở menu Tách Hợp Thể (cần Cuộn Tách Hợp Thể)");
        sender.sendMessage("§e/nguyento awaken §7- Giác Ngộ (hoặc giữ Shift rồi bấm phím F đổi tay)");
        if (sender.hasPermission("nguyento.admin")) {
            sender.sendMessage("§e/nguyento give <người chơi> <nguyên tố|all> [số lượng] §7- đưa cuộn giấy");
            sender.sendMessage("§e/nguyento give <người chơi> unfuse [số lượng] §7- đưa Cuộn Tách Hợp Thể");
            sender.sendMessage("§e/nguyento unlock <người chơi> <nguyên tố|all> §7- mở khóa trực tiếp");
            sender.sendMessage("§e/nguyento reset [người chơi] §7- bỏ trang bị + xóa buff kẹt, chỉ số về mặc định");
            sender.sendMessage("§e/nguyento slot <give|remove|status> <người chơi> §7- cấp / thu hồi ô nguyên tố thứ 4");
            sender.sendMessage("§e/nguyento quest reset <người chơi> §7- xóa tiến độ nhiệm vụ ô thứ 4");
            sender.sendMessage("§e/nguyento quest dungeon <người chơi> [rank] §7- ghi nhận 1 cổng dungeon (plugin dungeon gọi)");
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
            out.add("fusion");
            out.add("quest");
            out.add("unfuse");
            out.add("awaken");
            if (sender.hasPermission("nguyento.admin")) out.addAll(Arrays.asList("give", "unlock", "reset", "reload", "slot"));
        } else if (sender.hasPermission("nguyento.admin")) {
            if (args.length == 2 && args[0].equalsIgnoreCase("slot")) {
                out.addAll(Arrays.asList("give", "remove", "status"));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("slot")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("quest")) {
                out.add("reset");
                out.add("dungeon");
            } else if (args.length == 3 && args[0].equalsIgnoreCase("quest")
                    && (args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("dungeon"))) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock")
                    || args[0].equalsIgnoreCase("reset"))) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock"))) {
                out.add("all");
                if (args[0].equalsIgnoreCase("give")) out.add("unfuse");
                for (Element e : Element.values()) out.add(e.getId());
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
