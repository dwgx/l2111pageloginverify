package CNM.dwgx.l2111pageverify;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ResetRequestStore {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    public record ResetRequest(
            UUID uuid,
            String account,
            String qq,
            String qqPlain,
            String qqSalt,
            String minecraftName,
            PasswordMode mode,
            Status status,
            long createdAt,
            long decidedAt,
            String decidedBy,
            boolean notified
    ) {
    }

    private final L2111pageloginverify plugin;
    private final Map<UUID, ResetRequest> requests = new HashMap<>();
    private YamlConfiguration config;

    public ResetRequestStore(L2111pageloginverify plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = plugin.getResetFile();
        if (!file.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                file.createNewFile();
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create resets.yml", ex);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        requests.clear();

        ConfigurationSection section = config.getConfigurationSection("requests");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String account = section.getString(key + ".account", "");
            String qq = section.getString(key + ".qq", "");
            String qqPlain = section.getString(key + ".qq_plain", "");
            String qqSalt = section.getString(key + ".qq_salt", "");
            String mcName = section.getString(key + ".minecraft_name", "");
            PasswordMode mode = PasswordMode.fromInput(section.getString(key + ".mode"));
            if (mode == null) {
                mode = plugin.getDefaultPasswordMode();
            }
            if ((qqPlain == null || qqPlain.isBlank())
                    && qq != null && !qq.isBlank()
                    && (qqSalt == null || qqSalt.isBlank())) {
                qqPlain = qq;
            }
            Status status = parseStatus(section.getString(key + ".status"));
            long createdAt = section.getLong(key + ".created_at", 0L);
            long decidedAt = section.getLong(key + ".decided_at", 0L);
            String decidedBy = section.getString(key + ".decided_by", "");
            boolean notified = section.getBoolean(key + ".notified", false);
            requests.put(uuid, new ResetRequest(uuid, account, qq, qqPlain, qqSalt, mcName, mode, status,
                    createdAt, decidedAt, decidedBy, notified));
        }
    }

    public void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set("requests", null);
        for (ResetRequest req : requests.values()) {
            String path = "requests." + req.uuid();
            config.set(path + ".account", req.account());
            config.set(path + ".qq", req.qq());
            config.set(path + ".qq_plain", req.qqPlain());
            config.set(path + ".qq_salt", req.qqSalt());
            config.set(path + ".minecraft_name", req.minecraftName());
            config.set(path + ".mode", req.mode().name());
            config.set(path + ".status", req.status().name());
            config.set(path + ".created_at", req.createdAt());
            config.set(path + ".decided_at", req.decidedAt());
            config.set(path + ".decided_by", req.decidedBy());
            config.set(path + ".notified", req.notified());
        }
        try {
            config.save(plugin.getResetFile());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save resets.yml", ex);
        }
    }

    public boolean submit(UUID uuid, String account, String qq, String minecraftName, PasswordMode mode) {
        if (uuid == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        String qqPlain = qq == null ? "" : qq.trim();
        String storedQq = qqPlain;
        String qqSalt = "";
        PasswordMode useMode = mode == null ? PasswordMode.PLAINTEXT : mode;
        ResetRequest req = new ResetRequest(uuid, account == null ? "" : account, storedQq, qqPlain, qqSalt,
                minecraftName == null ? "" : minecraftName, useMode, Status.PENDING,
                now, 0L, "", false);
        requests.put(uuid, req);
        save();
        return true;
    }

    public boolean approve(UUID uuid, String decidedBy) {
        return updateStatus(uuid, Status.APPROVED, decidedBy);
    }

    public boolean reject(UUID uuid, String decidedBy) {
        return updateStatus(uuid, Status.REJECTED, decidedBy);
    }

    private boolean updateStatus(UUID uuid, Status status, String decidedBy) {
        ResetRequest current = requests.get(uuid);
        if (current == null) {
            return false;
        }
        ResetRequest updated = new ResetRequest(
                current.uuid(),
                current.account(),
                current.qq(),
                current.qqPlain(),
                current.qqSalt(),
                current.minecraftName(),
                current.mode(),
                status,
                current.createdAt(),
                System.currentTimeMillis(),
                decidedBy == null ? "" : decidedBy,
                false
        );
        requests.put(uuid, updated);
        save();
        return true;
    }

    public boolean isApproved(UUID uuid) {
        ResetRequest req = requests.get(uuid);
        return req != null && req.status() == Status.APPROVED;
    }

    public ResetRequest get(UUID uuid) {
        return requests.get(uuid);
    }

    public Map<UUID, ResetRequest> getSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(requests));
    }

    public void clear(UUID uuid) {
        requests.remove(uuid);
        save();
    }

    public boolean markNotified(UUID uuid) {
        ResetRequest req = requests.get(uuid);
        if (req == null) {
            return false;
        }
        ResetRequest updated = new ResetRequest(
                req.uuid(),
                req.account(),
                req.qq(),
                req.qqPlain(),
                req.qqSalt(),
                req.minecraftName(),
                req.mode(),
                req.status(),
                req.createdAt(),
                req.decidedAt(),
                req.decidedBy(),
                true
        );
        requests.put(uuid, updated);
        save();
        return true;
    }

    private Status parseStatus(String raw) {
        if (raw == null) {
            return Status.PENDING;
        }
        try {
            return Status.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Status.PENDING;
        }
    }

}
