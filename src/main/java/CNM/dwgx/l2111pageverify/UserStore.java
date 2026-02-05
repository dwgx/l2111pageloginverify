package CNM.dwgx.l2111pageverify;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class UserStore {

    private final L2111pageloginverify plugin;
    private final LogsStore logsStore;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<UUID, UserRecord> users = new HashMap<>();
    private final Map<String, UUID> accountIndex = new HashMap<>();
    private final Map<UUID, PendingItem> pendingItems = new HashMap<>();

    private YamlConfiguration config;
    private String activeSectionKey = "data.hashed";

    public UserStore(L2111pageloginverify plugin, LogsStore logsStore) {
        this.plugin = plugin;
        this.logsStore = logsStore;
    }

    public void load() {
        loadForMode(plugin.getDefaultPasswordMode());
    }

    public void loadForMode(PasswordMode mode) {
        File file = plugin.getUsersFile();
        if (!file.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                file.createNewFile();
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create users.yml", ex);
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        users.clear();
        accountIndex.clear();
        pendingItems.clear();

        activeSectionKey = sectionKeyForMode(mode);
        ConfigurationSection section = config.getConfigurationSection(activeSectionKey);
        if (section == null) {
            section = config.getConfigurationSection("users");
        }
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid UUID in users.yml: " + key);
                continue;
            }
            String account = section.getString(key + ".account", "");
            String password = section.getString(key + ".password", "");
            String salt = section.getString(key + ".salt", "");
            PasswordMode recordMode = PasswordMode.fromInput(section.getString(key + ".mode"));
            if (recordMode == null) {
                recordMode = mode != null ? mode : PasswordMode.HASHED;
            }
            String mcName = section.getString(key + ".minecraft_name", "");
            long registeredAt = section.getLong(key + ".register.time", 0L);
            String registerIp = section.getString(key + ".register.ip", "");
            boolean approved = section.getBoolean(key + ".approved", true);
            String lastIp = section.getString(key + ".last_login.ip", "");
            long lastAt = section.getLong(key + ".last_login.time", 0L);
            String lastSalt = section.getString(key + ".last_login.salt", "");
            UserRecord record = new UserRecord(uuid, account, password, salt, recordMode, mcName, registeredAt,
                    registerIp, approved, lastIp, lastAt, lastSalt);
            users.put(uuid, record);
            if (!account.isEmpty()) {
                accountIndex.put(account.toLowerCase(Locale.ROOT), uuid);
            }

            String pendingEncoded = section.getString(key + ".pending.bytes");
            int slot = section.getInt(key + ".pending.slot", -1);
            if (pendingEncoded != null && !pendingEncoded.isBlank()) {
                try {
                    byte[] data = Base64.getDecoder().decode(pendingEncoded);
                    ItemStack pending = ItemStack.deserializeBytes(data);
                    if (pending != null) {
                        pendingItems.put(uuid, new PendingItem(pending, slot));
                    }
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Failed to decode pending item for " + uuid);
                }
            } else {
                ItemStack pending = section.getItemStack(key + ".pending.item");
                if (pending != null) {
                    pendingItems.put(uuid, new PendingItem(pending, slot));
                }
            }

        }

        String pendingOnlyPath = activeSectionKey + ".pending_only";
        ConfigurationSection pendingOnly = config.getConfigurationSection(pendingOnlyPath);
        if (pendingOnly == null) {
            pendingOnly = config.getConfigurationSection("pending_only");
        }
        if (pendingOnly != null) {
            for (String key : pendingOnly.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (pendingItems.containsKey(uuid)) {
                    continue;
                }
                String pendingEncoded = pendingOnly.getString(key + ".bytes");
                int slot = pendingOnly.getInt(key + ".slot", -1);
                if (pendingEncoded != null && !pendingEncoded.isBlank()) {
                    try {
                        byte[] data = Base64.getDecoder().decode(pendingEncoded);
                        ItemStack pending = ItemStack.deserializeBytes(data);
                        if (pending != null) {
                            pendingItems.put(uuid, new PendingItem(pending, slot));
                        }
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Failed to decode pending-only item for " + uuid);
                    }
                }
            }
        }
    }

    public void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        if (activeSectionKey == null || activeSectionKey.isBlank()) {
            activeSectionKey = sectionKeyForMode(plugin.getDefaultPasswordMode());
        }
        config.set(activeSectionKey, null);
        for (UserRecord record : users.values()) {
            String path = activeSectionKey + "." + record.uuid();
            config.set(path + ".account", record.account());
            config.set(path + ".password", record.password());
            if (record.salt() != null && !record.salt().isEmpty()) {
                config.set(path + ".salt", record.salt());
            }
            config.set(path + ".mode", record.mode().name());
            if (record.minecraftName() != null && !record.minecraftName().isEmpty()) {
                config.set(path + ".minecraft_name", record.minecraftName());
            } else {
                config.set(path + ".minecraft_name", null);
            }
            if (record.registeredAt() > 0L) {
                config.set(path + ".register.time", record.registeredAt());
            } else {
                config.set(path + ".register.time", null);
            }
            config.set(path + ".register.ip", record.registerIp());
            config.set(path + ".approved", record.approved());
            if (record.lastLoginIp() != null && !record.lastLoginIp().isEmpty()) {
                config.set(path + ".last_login.ip", record.lastLoginIp());
            } else {
                config.set(path + ".last_login.ip", null);
            }
            if (record.lastLoginAt() > 0L) {
                config.set(path + ".last_login.time", record.lastLoginAt());
            } else {
                config.set(path + ".last_login.time", null);
            }
            if (record.lastLoginSalt() != null && !record.lastLoginSalt().isEmpty()) {
                config.set(path + ".last_login.salt", record.lastLoginSalt());
            } else {
                config.set(path + ".last_login.salt", null);
            }
            PendingItem pending = pendingItems.get(record.uuid());
            if (pending != null) {
                String encoded = Base64.getEncoder().encodeToString(pending.item().serializeAsBytes());
                config.set(path + ".pending.bytes", encoded);
                config.set(path + ".pending.slot", pending.slot());
            } else {
                config.set(path + ".pending", null);
            }
        }

        String pendingOnlyPath = activeSectionKey + ".pending_only";
        config.set(pendingOnlyPath, null);
        for (Map.Entry<UUID, PendingItem> entry : pendingItems.entrySet()) {
            UUID uuid = entry.getKey();
            if (users.containsKey(uuid)) {
                continue;
            }
            PendingItem pending = entry.getValue();
            String path = pendingOnlyPath + "." + uuid;
            String encoded = Base64.getEncoder().encodeToString(pending.item().serializeAsBytes());
            config.set(path + ".bytes", encoded);
            config.set(path + ".slot", pending.slot());
        }

        try {
            config.save(plugin.getUsersFile());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save users.yml", ex);
        }
    }

    public boolean hasUser(UUID uuid) {
        return users.containsKey(uuid);
    }

    public UserRecord getUser(UUID uuid) {
        return users.get(uuid);
    }

    public boolean isApproved(UUID uuid) {
        UserRecord record = users.get(uuid);
        if (record == null) {
            return false;
        }
        return record.approved();
    }

    public boolean setApproved(UUID uuid, boolean approved) {
        UserRecord record = users.get(uuid);
        if (record == null) {
            return false;
        }
        UserRecord updated = new UserRecord(
                record.uuid(),
                record.account(),
                record.password(),
                record.salt(),
                record.mode(),
                record.minecraftName(),
                record.registeredAt(),
                record.registerIp(),
                approved,
                record.lastLoginIp(),
                record.lastLoginAt(),
                record.lastLoginSalt()
        );
        users.put(uuid, updated);
        save();
        return true;
    }

    public boolean isAccountTaken(String account) {
        if (account == null) {
            return false;
        }
        return accountIndex.containsKey(account.trim().toLowerCase(Locale.ROOT));
    }

    public boolean register(UUID uuid, String account, String password, PasswordMode mode, String minecraftName, String registerIp, boolean approved) {
        if (users.containsKey(uuid)) {
            return false;
        }
        String accountKey = account.trim().toLowerCase(Locale.ROOT);
        if (accountIndex.containsKey(accountKey)) {
            return false;
        }

        String storedPassword;
        String salt = "";
        if (mode == PasswordMode.HASHED) {
            byte[] saltBytes = new byte[16];
            secureRandom.nextBytes(saltBytes);
            salt = Base64.getEncoder().encodeToString(saltBytes);
            int iterations = plugin.getConfig().getInt("security.pbkdf2-iterations", 60000);
            storedPassword = "pbkdf2$" + iterations + "$" + hashPasswordPbkdf2(password, saltBytes, iterations);
        } else {
            storedPassword = password;
        }

        long registeredAt = System.currentTimeMillis();
        String mcName = minecraftName != null ? minecraftName : "";
        UserRecord record = new UserRecord(uuid, account.trim(), storedPassword, salt, mode,
                mcName, registeredAt, registerIp == null ? "" : registerIp, approved, "", 0L, "");
        users.put(uuid, record);
        accountIndex.put(accountKey, uuid);
        save();
        return true;
    }

    public PendingItem getPendingItem(UUID uuid) {
        return pendingItems.get(uuid);
    }

    public Map<UUID, UserRecord> getUsersSnapshot() {
        return new HashMap<>(users);
    }

    public void setPendingItem(UUID uuid, ItemStack item, int slot) {
        if (item == null) {
            return;
        }
        pendingItems.put(uuid, new PendingItem(item.clone(), slot));
        if (logsStore != null) {
            logsStore.appendPending(uuid, true, item, slot);
        }
        save();
    }

    public void clearPendingItem(UUID uuid) {
        pendingItems.remove(uuid);
        save();
    }

    public void reloadForMode(PasswordMode mode) {
        loadForMode(mode);
    }

    public void updateLoginInfo(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        UserRecord record = users.get(uuid);
        if (record == null) {
            return;
        }
        String ip = "";
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            ip = player.getAddress().getAddress().getHostAddress();
        }
        long now = System.currentTimeMillis();
        String loginSalt = generateLoginSalt();
        String mcName = player.getName();
        UserRecord updated = new UserRecord(
                record.uuid(),
                record.account(),
                record.password(),
                record.salt(),
                record.mode(),
                mcName,
                record.registeredAt() > 0L ? record.registeredAt() : System.currentTimeMillis(),
                record.registerIp(),
                record.approved(),
                ip,
                now,
                loginSalt
        );
        users.put(uuid, updated);
        save();
        if (logsStore != null) {
            logsStore.appendLogin(uuid, record.account(), mcName, ip, now, loginSalt, record.mode());
        }
        if (plugin.getConfig().getBoolean("log-login-info", true)) {
            plugin.getLogger().info("Login info updated for " + player.getName()
                    + " ip=" + (ip.isEmpty() ? "unknown" : ip));
        }
    }

    public boolean tryRestorePendingItem(Player player, boolean notify) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        PendingItem pending = pendingItems.get(uuid);
        if (pending == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        boolean restored = false;
        int slot = pending.slot();
        ItemStack item = pending.item();
        if (slot >= 0) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, item);
                restored = true;
            }
        }
        if (!restored && inventory.firstEmpty() >= 0) {
            inventory.addItem(item);
            restored = true;
        }
        if (restored) {
            pendingItems.remove(uuid);
            if (logsStore != null) {
                logsStore.appendPending(uuid, false, item, slot);
            }
            save();
            if (notify) {
                player.sendMessage(plugin.message("pending-restored"));
            }
            if (plugin.getConfig().getBoolean("log-pending", true)) {
                plugin.getLogger().info("Restored pending item for " + player.getName()
                        + " slot=" + slot + " type=" + item.getType() + " x" + item.getAmount());
            }
            player.updateInventory();
            return true;
        }
        if (notify) {
            player.sendMessage(plugin.message("pending-full"));
        }
        if (plugin.getConfig().getBoolean("log-pending", true)) {
            plugin.getLogger().warning("Pending item restore failed (inventory full) for " + player.getName()
                    + " slot=" + slot + " type=" + item.getType() + " x" + item.getAmount());
        }
        return false;
    }

    public boolean verify(UUID uuid, String account, String password) {
        UserRecord record = users.get(uuid);
        if (record == null) {
            return false;
        }
        if (account == null || account.trim().isEmpty()) {
            return false;
        }
        if (!record.account().equalsIgnoreCase(account.trim())) {
            return false;
        }

        if (record.mode() == PasswordMode.PLAINTEXT) {
            return MessageDigest.isEqual(
                    record.password().getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8)
            );
        }

        if (record.salt() == null || record.salt().isEmpty()) {
            return false;
        }
        byte[] saltBytes;
        try {
            saltBytes = Base64.getDecoder().decode(record.salt());
        } catch (IllegalArgumentException ex) {
            return false;
        }

        String stored = record.password();
        if (stored.startsWith("pbkdf2$")) {
            String[] parts = stored.split("\\$", 3);
            if (parts.length < 3) {
                return false;
            }
            int iterations;
            try {
                iterations = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                return false;
            }
            String hashed = hashPasswordPbkdf2(password, saltBytes, iterations);
            return MessageDigest.isEqual(
                    parts[2].getBytes(StandardCharsets.UTF_8),
                    hashed.getBytes(StandardCharsets.UTF_8)
            );
        }

        String legacy = hashPasswordLegacy(password, saltBytes);
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                legacy.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hashPasswordLegacy(String password, byte[] saltBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(saltBytes);
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Password hashing failed", ex);
            return "";
        }
    }

    private String hashPasswordPbkdf2(String password, byte[] saltBytes, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, iterations, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "PBKDF2 hashing failed", ex);
            return "";
        }
    }

    private String generateLoginSalt() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String sectionKeyForMode(PasswordMode mode) {
        return mode == PasswordMode.PLAINTEXT ? "data.plain" : "data.hashed";
    }

    public record PendingItem(ItemStack item, int slot) {
    }
}
