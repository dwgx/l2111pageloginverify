package CNM.dwgx.l2111pageverify;

import java.io.File;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import CNM.dwgx.l2111pageverify.manager.VerifyModuleManager;
import CNM.dwgx.l2111pageverify.manager.VerificationManager;

public final class L2111pageloginverify extends JavaPlugin {

    private VerifyModuleManager moduleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        moduleManager = new VerifyModuleManager(this);
        moduleManager.enable();
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disable();
            moduleManager = null;
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

    public File getLogsFile() {
        return new File(getDataFolder(), "logs.yml");
    }

    public File getResetFile() {
        return new File(getDataFolder(), "resets.yml");
    }

    public VerificationBookService getBookService() {
        return moduleManager != null ? moduleManager.getBookService() : null;
    }

    public VerificationManager getVerificationManager() {
        return moduleManager != null ? moduleManager.getVerificationManager() : null;
    }

    public UserStore getUserStore() {
        return moduleManager != null ? moduleManager.getUserStore() : null;
    }

    public boolean isAdminVerifyEnabled() {
        return getConfig().getBoolean("admin-verify-enabled", false);
    }

    public void setAdminVerifyEnabled(boolean enabled) {
        getConfig().set("admin-verify-enabled", enabled);
        saveConfig();
    }

    public void unlockPlayerAfterApproval(Player player) {
        if (moduleManager != null) {
            moduleManager.unlockPlayerAfterApproval(player);
        }
    }

    public void lockPlayerAfterUnapproval(Player player) {
        if (moduleManager != null) {
            moduleManager.lockPlayerAfterUnapproval(player);
        }
    }

    public void lockPlayerForReset(UUID uuid, String decidedBy) {
        if (moduleManager != null) {
            moduleManager.lockPlayerForReset(uuid, decidedBy);
        }
    }

    public void refreshVisibility() {
        if (moduleManager != null) {
            moduleManager.refreshVisibility();
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        if (moduleManager != null) {
            moduleManager.reloadConfig();
        }
    }
}
