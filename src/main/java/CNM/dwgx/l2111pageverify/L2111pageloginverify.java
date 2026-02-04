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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().isSet("encryption-enabled")) {
            getConfig().set("encryption-enabled", true);
        }
        if (!isInitialized()) {
            setInitialized(true);
        }
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
    }

    @Override
    public void onDisable() {
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
}
