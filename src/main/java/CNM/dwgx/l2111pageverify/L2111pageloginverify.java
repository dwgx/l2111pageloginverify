package CNM.dwgx.l2111pageverify;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class L2111pageloginverify extends JavaPlugin {

    private UserStore userStore;
    private VerificationManager verificationManager;
    private VerificationBookService bookService;
    private WebAdminServer webAdminServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().isSet("encryption-enabled")) {
            getConfig().set("encryption-enabled", true);
        }
        if (!isInitialized()) {
            setInitialized(true);
        }
        sanitizeWebText();
        saveConfig();

        userStore = new UserStore(this);
        userStore.load();

        verificationManager = new VerificationManager();

        bookService = new VerificationBookService(this, verificationManager, userStore);

        var listener = new VerificationListener(this, userStore, verificationManager, bookService);
        Bukkit.getPluginManager().registerEvents(listener, this);

        Bukkit.getScheduler().runTaskTimer(this,
                new VerificationEnforcer(this, userStore, verificationManager, bookService),
                20L,
                10L);

        PluginCommand command = getCommand("dwgxverify");
        if (command != null) {
            VerifyCommand executor = new VerifyCommand(this, userStore, verificationManager, bookService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command dwgxverify not found in plugin.yml.");
        }

        if (!isInitialized()) {
            getLogger().warning("Verification plugin not initialized. Run /dwgxverify pass set <hash|plain> as admin.");
        }

        webAdminServer = new WebAdminServer(this, userStore);
        webAdminServer.start();
    }

    @Override
    public void onDisable() {
        if (webAdminServer != null) {
            webAdminServer.stop();
        }
        if (userStore != null) {
            userStore.save();
        }
    }

    public boolean isInitialized() {
        return getConfig().getBoolean("initialized", false);
    }

    public void setInitialized(boolean initialized) {
        getConfig().set("initialized", initialized);
        saveConfig();
    }

    public PasswordMode getDefaultPasswordMode() {
        return isEncryptionEnabled() ? PasswordMode.HASHED : PasswordMode.PLAINTEXT;
    }

    public void setDefaultPasswordMode(PasswordMode mode) {
        boolean enabled = mode != null && mode == PasswordMode.HASHED;
        getConfig().set("encryption-enabled", enabled);
        saveConfig();
    }

    public boolean isEncryptionEnabled() {
        return getConfig().getBoolean("encryption-enabled", true);
    }

    public void setEncryptionEnabled(boolean enabled) {
        getConfig().set("encryption-enabled", enabled);
        saveConfig();
    }

    public boolean isVerificationEnabled() {
        return getConfig().getBoolean("enabled", true);
    }

    public void setVerificationEnabled(boolean enabled) {
        getConfig().set("enabled", enabled);
        saveConfig();
    }

    public boolean isChatAllowedBeforeVerify() {
        return getConfig().getBoolean("allow-chat-before-verify", false);
    }

    public void setChatAllowedBeforeVerify(boolean allowed) {
        getConfig().set("allow-chat-before-verify", allowed);
        saveConfig();
    }

    public String message(String key) {
        return getConfig().getString("messages." + key, key);
    }

    public File getUsersFile() {
        return new File(getDataFolder(), "users.yml");
    }

    public VerificationBookService getBookService() {
        return bookService;
    }

    public void refreshVisibility() {
        boolean hide = getConfig().getBoolean("hide-unverified", false);
        var players = Bukkit.getOnlinePlayers();
        for (Player viewer : players) {
            for (Player target : players) {
                if (viewer.equals(target)) {
                    continue;
                }
                boolean targetUnverified = !verificationManager.isVerified(target.getUniqueId());
                if (hide && targetUnverified) {
                    viewer.hidePlayer(this, target);
                } else {
                    viewer.showPlayer(this, target);
                }
            }
        }
    }

    private void sanitizeWebText() {
        ensureWebText("title", "Verification Console");
        ensureWebText("subtitle", "l2111pageloginverify Web");
        ensureWebText("section-users", "Users");
        ensureWebText("section-settings", "Settings");
        ensureWebText("column-uuid", "UUID");
        ensureWebText("column-name", "Minecraft Name");
        ensureWebText("column-avatar", "Avatar");
        ensureWebText("column-account", "Account");
        ensureWebText("column-password", "Password/Hash");
        ensureWebText("column-key", "Key/Salt");
        ensureWebText("column-register", "Registered");
        ensureWebText("column-login", "Last Login");
        ensureWebText("column-pending", "Pending Item");
        ensureWebText("column-pending-log", "Pending Log");
        ensureWebText("setting-enabled", "Verification");
        ensureWebText("setting-encryption", "Encryption");
        ensureWebText("setting-chat", "Chat Before Verify");
        ensureWebText("setting-hide", "Hide Unverified");
        ensureWebText("setting-sound", "Success Sound");
        ensureWebText("setting-volume", "Volume / Pitch");
        ensureWebText("setting-save", "Save");
        ensureWebText("status-on", "ON");
        ensureWebText("status-off", "OFF");
        ensureWebText("pending-stored-label", "stored");
        ensureWebText("pending-restored-label", "restored");
    }

    private void ensureWebText(String key, String fallback) {
        String path = "web.text." + key;
        String value = getConfig().getString(path, "");
        if (value == null || value.isBlank() || value.contains("?")) {
            getConfig().set(path, fallback);
        }
    }
}
