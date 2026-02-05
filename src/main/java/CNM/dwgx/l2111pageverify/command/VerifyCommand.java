package CNM.dwgx.l2111pageverify.command;
import CNM.dwgx.l2111pageverify.L2111pageloginverify;
import CNM.dwgx.l2111pageverify.UserStore;
import CNM.dwgx.l2111pageverify.ResetRequestStore;
import CNM.dwgx.l2111pageverify.VerificationBookService;
import CNM.dwgx.l2111pageverify.VerificationListener;
import CNM.dwgx.l2111pageverify.NoticeType;
import CNM.dwgx.l2111pageverify.manager.VerificationManager;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class VerifyCommand implements CommandExecutor, TabCompleter {

    private final L2111pageloginverify plugin;
    private final UserStore userStore;
    private final ResetRequestStore resetStore;
    private final VerificationManager verificationManager;
    private final VerificationBookService bookService;
    private final VerificationListener verificationListener;

    public VerifyCommand(L2111pageloginverify plugin, UserStore userStore, ResetRequestStore resetStore,
                         VerificationManager verificationManager,
                         VerificationBookService bookService,
                         VerificationListener verificationListener) {
        this.plugin = plugin;
        this.userStore = userStore;
        this.resetStore = resetStore;
        this.verificationManager = verificationManager;
        this.bookService = bookService;
        this.verificationListener = verificationListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && "login".equalsIgnoreCase(args[0])) {
            return handleDirectLogin(sender, args);
        }
        if (args.length >= 1 && ("reg".equalsIgnoreCase(args[0]) || "register".equalsIgnoreCase(args[0]))) {
            return handleDirectRegister(sender, args);
        }
        if (args.length >= 1 && ("resetpass".equalsIgnoreCase(args[0]) || "restpass".equalsIgnoreCase(args[0]))) {
            return handleResetPass(sender);
        }
        if (args.length >= 1 && "toggle".equalsIgnoreCase(args[0])) {
            return handleToggle(sender);
        }
        if (args.length >= 1 && "chat".equalsIgnoreCase(args[0])) {
            return handleChat(sender, args);
        }
        if (args.length >= 1 && "sound".equalsIgnoreCase(args[0])) {
            return handleSound(sender, args);
        }
        if (args.length >= 1 && "hide".equalsIgnoreCase(args[0])) {
            return handleHide(sender, args);
        }
        if (args.length >= 1 && "encryption".equalsIgnoreCase(args[0])) {
            return handleEncryption(sender, args);
        }
        if (args.length >= 1 && "adminverify".equalsIgnoreCase(args[0])) {
            return handleAdminVerify(sender, args);
        }
        if (args.length >= 1 && "approve".equalsIgnoreCase(args[0])) {
            return handleApprove(sender, args, true);
        }
        if (args.length >= 1 && "unapprove".equalsIgnoreCase(args[0])) {
            return handleApprove(sender, args, false);
        }
        if (args.length >= 1 && "reset".equalsIgnoreCase(args[0])) {
            return handleReset(sender, args);
        }
        if (args.length >= 1 && "reloadconfig".equalsIgnoreCase(args[0])) {
            return handleReloadConfig(sender);
        }

        if (sender instanceof Player player) {
            UUID uuid = player.getUniqueId();
            if (!plugin.isInitialized()) {
                player.sendMessage(plugin.message("not-initialized"));
                return true;
            }
            if (!plugin.isVerificationEnabled()) {
                player.sendMessage(plugin.message("verification-disabled"));
                return true;
            }
            if (userStore.hasUser(uuid)) {
                verificationManager.setSession(uuid, VerificationManager.SessionType.LOGIN);
                verificationManager.setNotice(uuid, plugin.message("open-login-book"), NoticeType.INFO);
                bookService.giveBook(player, VerificationManager.SessionType.LOGIN,
                        verificationManager.getNotice(uuid), verificationManager.getNoticeType(uuid));
                player.sendMessage(plugin.message("open-login-book"));
            } else {
                verificationManager.setSession(uuid, VerificationManager.SessionType.REGISTER);
                verificationManager.setNotice(uuid, plugin.message("open-register-book"), NoticeType.INFO);
                bookService.giveBook(player, VerificationManager.SessionType.REGISTER,
                        verificationManager.getNotice(uuid), verificationManager.getNoticeType(uuid));
                player.sendMessage(plugin.message("open-register-book"));
            }
            return true;
        }

        sender.sendMessage(plugin.message("player-only"));
        return true;
    }

    private boolean handleToggle(CommandSender sender) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        boolean enabled = !plugin.isVerificationEnabled();
        plugin.setVerificationEnabled(enabled);
        sender.sendMessage(enabled ? plugin.message("verification-enabled") : plugin.message("verification-disabled"));
        return true;
    }

    private boolean handleChat(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.message("usage-chat"));
            return true;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if ("on".equals(mode)) {
            plugin.setChatAllowedBeforeVerify(true);
            sender.sendMessage(plugin.message("chat-allowed"));
            return true;
        }
        if ("off".equals(mode)) {
            plugin.setChatAllowedBeforeVerify(false);
            sender.sendMessage(plugin.message("chat-blocked"));
            return true;
        }
        sender.sendMessage(plugin.message("usage-chat"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("dwgxverify.admin");
        if (args.length == 1) {
            if ("login".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("login");
            }
            if ("reg".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("reg");
            }
            if ("resetpass".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("resetpass");
            }
            if ("restpass".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("restpass");
            }
            if (!isAdmin) {
                return completions;
            }
            if ("toggle".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("toggle");
            }
            if ("chat".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("chat");
            }
            if ("sound".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("sound");
            }
            if ("hide".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("hide");
            }
            if ("encryption".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("encryption");
            }
            if ("adminverify".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("adminverify");
            }
            if ("approve".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("approve");
            }
            if ("unapprove".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("unapprove");
            }
            if ("reset".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("reset");
            }
            if ("reloadconfig".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("reloadconfig");
            }
        } else if (args.length == 2) {
            if (!isAdmin) {
                return completions;
            }
            if ("chat".equalsIgnoreCase(args[0])) {
                if ("on".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("on");
                }
                if ("off".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("off");
                }
            } else if ("sound".equalsIgnoreCase(args[0])) {
                completions.add("ENTITY_PLAYER_LEVELUP");
            } else if ("hide".equalsIgnoreCase(args[0])) {
                if ("on".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("on");
                }
                if ("off".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("off");
                }
            } else if ("encryption".equalsIgnoreCase(args[0])) {
                if ("true".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("true");
                }
                if ("false".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("false");
                }
            } else if ("adminverify".equalsIgnoreCase(args[0])) {
                if ("on".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("on");
                }
                if ("off".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("off");
                }
            } else if ("reset".equalsIgnoreCase(args[0])) {
                if ("approve".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("approve");
                }
                if ("reject".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add("reject");
                }
            }
        }
        return completions;
    }

    private boolean handleSound(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.message("usage-sound"));
            return true;
        }
        String sound = args[1].toUpperCase(Locale.ROOT);
        double vol = args.length >= 3 ? parseDouble(args[2], 1.0) : 1.0;
        double pitch = args.length >= 4 ? parseDouble(args[3], 1.0) : 1.0;
        plugin.getConfig().set("sound.success", sound);
        plugin.getConfig().set("sound.volume", vol);
        plugin.getConfig().set("sound.pitch", pitch);
        plugin.saveConfig();
        sender.sendMessage(plugin.message("sound-set")
                .replace("%sound%", sound)
                .replace("%vol%", String.valueOf(vol))
                .replace("%pitch%", String.valueOf(pitch)));
        return true;
    }

    private boolean handleHide(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.message("usage-hide"));
            return true;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if ("on".equals(mode)) {
            plugin.getConfig().set("hide-unverified", true);
            plugin.saveConfig();
            plugin.refreshVisibility();
            sender.sendMessage(plugin.message("hide-on"));
            return true;
        }
        if ("off".equals(mode)) {
            plugin.getConfig().set("hide-unverified", false);
            plugin.saveConfig();
            plugin.refreshVisibility();
            sender.sendMessage(plugin.message("hide-off"));
            return true;
        }
        sender.sendMessage(plugin.message("usage-hide"));
        return true;
    }

    private boolean handleEncryption(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.message("usage-encryption"));
            return true;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        boolean enabled;
        if ("true".equals(raw) || "on".equals(raw)) {
            enabled = true;
        } else if ("false".equals(raw) || "off".equals(raw)) {
            enabled = false;
        } else {
            sender.sendMessage(plugin.message("usage-encryption"));
            return true;
        }
        plugin.setEncryptionEnabled(enabled);
        plugin.setInitialized(true);
        userStore.reloadForMode(plugin.getDefaultPasswordMode());
        sender.sendMessage(enabled ? plugin.message("encryption-on") : plugin.message("encryption-off"));
        return true;
    }

    private boolean handleAdminVerify(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.message("usage-admin-verify"));
            return true;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if ("on".equals(mode)) {
            plugin.setAdminVerifyEnabled(true);
            sender.sendMessage(plugin.message("admin-verify-on"));
            return true;
        }
        if ("off".equals(mode)) {
            plugin.setAdminVerifyEnabled(false);
            sender.sendMessage(plugin.message("admin-verify-off"));
            return true;
        }
        sender.sendMessage(plugin.message("usage-admin-verify"));
        return true;
    }

    private boolean handleApprove(CommandSender sender, String[] args, boolean approved) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.message("usage-approve"));
            return true;
        }
        String target = args[1];
        java.util.UUID uuid = null;
        try {
            uuid = java.util.UUID.fromString(target);
        } catch (IllegalArgumentException ex) {
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(target);
            if (offline != null) {
                uuid = offline.getUniqueId();
            }
        }
        if (uuid == null) {
            sender.sendMessage(plugin.message("approve-failed"));
            return true;
        }
        boolean ok = userStore.setApproved(uuid, approved);
        if (!ok) {
            sender.sendMessage(plugin.message("approve-failed"));
            return true;
        }
        if (approved) {
            sender.sendMessage(plugin.message("approve-success"));
        } else {
            sender.sendMessage(plugin.message("unapprove-success"));
        }
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null && approved) {
            plugin.unlockPlayerAfterApproval(online);
            online.sendMessage(plugin.message("admin-verify-approved"));
        }
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.message("usage-reset"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String target = args[2];
        UUID uuid = null;
        try {
            uuid = UUID.fromString(target);
        } catch (IllegalArgumentException ex) {
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(target);
            if (offline != null) {
                uuid = offline.getUniqueId();
            }
        }
        if (uuid == null || resetStore == null) {
            sender.sendMessage(plugin.message("reset-approve-failed"));
            return true;
        }
        boolean ok;
        if ("reject".equals(action)) {
            ok = resetStore.reject(uuid, sender.getName());
            if (ok) {
                userStore.logAction(uuid, "reset-reject", "by=" + sender.getName());
            }
        } else if ("approve".equals(action)) {
            ok = resetStore.approve(uuid, sender.getName());
            if (ok) {
                plugin.lockPlayerForReset(uuid, sender.getName());
            }
        } else {
            sender.sendMessage(plugin.message("usage-reset"));
            return true;
        }
        if (!ok) {
            sender.sendMessage(plugin.message("reset-approve-failed"));
            return true;
        }
        sender.sendMessage("approve".equals(action)
                ? plugin.message("reset-approve-success")
                : plugin.message("reset-reject-success"));
        return true;
    }

    private boolean handleDirectLogin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!plugin.isInitialized()) {
            player.sendMessage(plugin.message("not-initialized"));
            return true;
        }
        if (!plugin.isVerificationEnabled()) {
            player.sendMessage(plugin.message("verification-disabled"));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.message("usage-login"));
            return true;
        }
        String account = args[1];
        String pass = args[2];
        if (verificationListener != null) {
            verificationListener.handleDirectLogin(player, account, pass);
        }
        return true;
    }

    private boolean handleDirectRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!plugin.isInitialized()) {
            player.sendMessage(plugin.message("not-initialized"));
            return true;
        }
        if (!plugin.isVerificationEnabled()) {
            player.sendMessage(plugin.message("verification-disabled"));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.message("usage-reg"));
            return true;
        }
        String account = args[1];
        String pass = args[2];
        String confirm = args.length >= 4 ? args[3] : pass;
        if (verificationListener != null) {
            verificationListener.handleDirectRegister(player, account, pass, confirm);
        }
        return true;
    }

    private boolean handleResetPass(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!plugin.isInitialized()) {
            player.sendMessage(plugin.message("not-initialized"));
            return true;
        }
        if (!plugin.isVerificationEnabled()) {
            player.sendMessage(plugin.message("verification-disabled"));
            return true;
        }
        if (verificationListener != null) {
            verificationListener.openResetRequest(player);
        }
        return true;
    }

    private boolean handleReloadConfig(CommandSender sender) {
        if (!sender.hasPermission("dwgxverify.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        plugin.reloadPluginConfig();
        sender.sendMessage(plugin.message("config-reloaded"));
        return true;
    }

    private double parseDouble(String raw, double def) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}