package CNM.dwgx.l2111pageverify;

import org.bukkit.Bukkit;
import java.util.UUID;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class VerifyModuleManager {

    private final L2111pageloginverify plugin;
    private LogsStore logsStore;
    private UserStore userStore;
    private ResetRequestStore resetStore;
    private VerificationManager verificationManager;
    private VerificationBookService bookService;
    private WebAdminServer webAdminServer;
    private BukkitTask enforcerTask;

    public VerifyModuleManager(L2111pageloginverify plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        initializeConfig();

        logsStore = new LogsStore(plugin);
        logsStore.load();

        userStore = new UserStore(plugin, logsStore);
        userStore.load();

        resetStore = new ResetRequestStore(plugin);
        resetStore.load();

        verificationManager = new VerificationManager();
        bookService = new VerificationBookService(plugin, verificationManager, userStore, resetStore);

        var listener = new VerificationListener(plugin, userStore, resetStore, verificationManager, bookService);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        enforcerTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                new VerificationEnforcer(plugin, userStore, resetStore, verificationManager, bookService),
                20L,
                10L
        );

        PluginCommand command = plugin.getCommand("dwgxverify");
        if (command != null) {
            VerifyCommand executor = new VerifyCommand(plugin, userStore, resetStore, verificationManager, bookService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            plugin.getLogger().warning("Command dwgxverify not found in plugin.yml.");
        }

        webAdminServer = new WebAdminServer(plugin, userStore, resetStore);
        webAdminServer.start();
    }

    public void disable() {
        if (webAdminServer != null) {
            webAdminServer.stop();
            webAdminServer = null;
        }
        if (enforcerTask != null) {
            enforcerTask.cancel();
            enforcerTask = null;
        }
        if (userStore != null) {
            userStore.save();
        }
        if (resetStore != null) {
            resetStore.save();
        }
    }

    public UserStore getUserStore() {
        return userStore;
    }

    public ResetRequestStore getResetStore() {
        return resetStore;
    }

    public VerificationManager getVerificationManager() {
        return verificationManager;
    }

    public VerificationBookService getBookService() {
        return bookService;
    }

    public void unlockPlayerAfterApproval(Player player) {
        if (player == null || verificationManager == null || bookService == null || userStore == null) {
            return;
        }
        verificationManager.markVerified(player.getUniqueId());
        verificationManager.clearNotice(player.getUniqueId());
        bookService.removeVerificationBook(player);
        bookService.purgeVerificationBooks(player.getInventory(), player.getUniqueId());
        bookService.purgeVerificationBooksLater(player);
        userStore.tryRestorePendingItem(player, true);
        restoreFlight(player);
        refreshVisibility();
    }

    public void lockPlayerAfterUnapproval(Player player) {
        if (player == null || verificationManager == null) {
            return;
        }
        verificationManager.markUnverified(player.getUniqueId());
        verificationManager.setSession(player.getUniqueId(), VerificationManager.SessionType.LOGIN);
        verificationManager.setNotice(player.getUniqueId(), plugin.message("admin-verify-required"), NoticeType.ERROR);
        if (plugin.getConfig().getBoolean("allow-flight-while-unverified", true)) {
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                verificationManager.snapshotFlight(player.getUniqueId(), player.getAllowFlight(), player.isFlying());
                player.setAllowFlight(true);
                player.setFlying(false);
            }
        }
        refreshVisibility();
    }

    public void lockPlayerForReset(UUID uuid, String decidedBy) {
        if (uuid == null || verificationManager == null || userStore == null || bookService == null) {
            return;
        }
        userStore.removeUser(uuid, "reset-approved");
        verificationManager.markUnverified(uuid);
        verificationManager.setSession(uuid, VerificationManager.SessionType.REGISTER);
        verificationManager.setNotice(uuid, plugin.message("open-reset-book"), NoticeType.INFO);
        if (logsStore != null) {
            logsStore.appendAction(uuid, "reset-approved", "by=" + (decidedBy == null ? "" : decidedBy));
        }
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            bookService.giveBook(player, VerificationManager.SessionType.REGISTER,
                    verificationManager.getNotice(uuid), verificationManager.getNoticeType(uuid));
            player.sendMessage(plugin.message("open-reset-book"));
        }
        refreshVisibility();
    }

    private void restoreFlight(Player player) {
        if (player == null || verificationManager == null) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        Boolean prevAllow = verificationManager.getPrevAllowFlight(player.getUniqueId());
        Boolean prevFlying = verificationManager.getPrevFlying(player.getUniqueId());
        if (prevAllow == null || prevFlying == null) {
            // Fallback: ensure survival players don't keep flight after verify.
            player.setAllowFlight(false);
            player.setFlying(false);
            return;
        }
        player.setAllowFlight(prevAllow);
        player.setFlying(prevFlying && prevAllow);
    }

    public void refreshVisibility() {
        if (verificationManager == null) {
            return;
        }
        boolean hide = plugin.getConfig().getBoolean("hide-unverified", false);
        var players = Bukkit.getOnlinePlayers();
        for (Player viewer : players) {
            for (Player target : players) {
                if (viewer.equals(target)) {
                    continue;
                }
                boolean targetUnverified = !verificationManager.isVerified(target.getUniqueId());
                if (hide && targetUnverified) {
                    viewer.hidePlayer(plugin, target);
                } else {
                    viewer.showPlayer(plugin, target);
                }
            }
        }
    }

    private void initializeConfig() {
        if (!plugin.getConfig().isSet("encryption-enabled")) {
            plugin.getConfig().set("encryption-enabled", true);
        }
        if (!plugin.isInitialized()) {
            plugin.setInitialized(true);
        }
        sanitizeWebText();
        plugin.saveConfig();
    }

    private void sanitizeWebText() {
        ensureWebText("title", "Verification Console");
        ensureWebText("subtitle", "l2111pageloginverify Web");
        ensureWebText("section-users", "Users");
        ensureWebText("section-settings", "Settings");
        ensureWebText("section-resets", "Reset Requests");
        ensureWebText("column-uuid", "UUID");
        ensureWebText("column-name", "Minecraft Name");
        ensureWebText("column-avatar", "Avatar");
        ensureWebText("column-account", "Account");
        ensureWebText("column-password", "Password/Hash");
        ensureWebText("column-key", "Key/Salt");
        ensureWebText("column-register", "Registered");
        ensureWebText("column-login", "Last Login");
        ensureWebText("column-pending", "Pending Item");
        ensureWebText("reset-column-uuid", "UUID");
        ensureWebText("reset-column-account", "Account");
        ensureWebText("reset-column-qq", "QQ");
        ensureWebText("reset-column-mc", "Minecraft Name");
        ensureWebText("reset-column-status", "Status");
        ensureWebText("reset-column-time", "Created");
        ensureWebText("setting-enabled", "Verification");
        ensureWebText("setting-encryption", "Encryption");
        ensureWebText("setting-chat", "Chat Before Verify");
        ensureWebText("setting-hide", "Hide Unverified");
        ensureWebText("setting-local-only", "Local Only");
        ensureWebText("setting-sound", "Success Sound");
        ensureWebText("setting-volume", "Volume / Pitch");
        ensureWebText("status-on", "ON");
        ensureWebText("status-off", "OFF");
        ensureWebText("action-unapprove", "Unapprove");
        ensureWebText("action-reset-approve", "Approve");
        ensureWebText("action-reset-reject", "Reject");
    }

    private void ensureWebText(String key, String fallback) {
        String path = "web.text." + key;
        String value = plugin.getConfig().getString(path, "");
        if (value == null || value.isBlank() || value.contains("?") || value.contains("\uFFFD")) {
            plugin.getConfig().set(path, fallback);
        }
    }
}
