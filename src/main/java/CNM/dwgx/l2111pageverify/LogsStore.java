package CNM.dwgx.l2111pageverify;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class LogsStore {

    private final L2111pageloginverify plugin;
    private YamlConfiguration config;
    private int maxLogin;
    private int maxPending;

    public LogsStore(L2111pageloginverify plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = plugin.getLogsFile();
        if (!file.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                file.createNewFile();
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create logs.yml", ex);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        maxLogin = plugin.getConfig().getInt("logs.max-login", 200);
        maxPending = plugin.getConfig().getInt("logs.max-pending", 200);
    }

    public void appendLogin(UUID uuid, String account, String mcName, String ip, long time, String loginSalt, PasswordMode mode) {
        if (config == null) {
            return;
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("uuid", uuid.toString());
        entry.put("account", account == null ? "" : account);
        entry.put("name", mcName == null ? "" : mcName);
        entry.put("ip", ip == null ? "" : ip);
        entry.put("time", time);
        entry.put("salt", loginSalt == null ? "" : loginSalt);
        entry.put("mode", mode == null ? "" : mode.name());
        List<Map<?, ?>> list = config.getMapList("login");
        List<Map<?, ?>> updated = new ArrayList<>(list);
        updated.add(entry);
        while (updated.size() > maxLogin) {
            updated.remove(0);
        }
        config.set("login", updated);
        save();
    }

    public void appendPending(UUID uuid, boolean stored, ItemStack item, int slot) {
        if (config == null || item == null) {
            return;
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("uuid", uuid.toString());
        entry.put("action", stored ? "stored" : "restored");
        entry.put("slot", slot);
        entry.put("type", item.getType().name());
        entry.put("amount", item.getAmount());
        entry.put("time", System.currentTimeMillis());
        List<Map<?, ?>> list = config.getMapList("pending");
        List<Map<?, ?>> updated = new ArrayList<>(list);
        updated.add(entry);
        while (updated.size() > maxPending) {
            updated.remove(0);
        }
        config.set("pending", updated);
        save();
    }

    private void save() {
        if (config == null) {
            return;
        }
        try {
            config.save(plugin.getLogsFile());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save logs.yml", ex);
        }
    }
}
