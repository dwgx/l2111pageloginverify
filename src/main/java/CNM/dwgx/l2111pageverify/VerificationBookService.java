package CNM.dwgx.l2111pageverify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import CNM.dwgx.l2111pageverify.NoticeType;

public final class VerificationBookService {

    private final L2111pageloginverify plugin;
    private final VerificationManager verificationManager;
    private final UserStore userStore;
    private final NamespacedKey tokenKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey typeKey;

    public VerificationBookService(L2111pageloginverify plugin,
                                   VerificationManager verificationManager,
                                   UserStore userStore) {
        this.plugin = plugin;
        this.verificationManager = verificationManager;
        this.userStore = userStore;
        this.tokenKey = new NamespacedKey(plugin, "verify_token");
        this.ownerKey = new NamespacedKey(plugin, "verify_owner");
        this.typeKey = new NamespacedKey(plugin, "verify_type");
    }

    public void giveBook(Player player, VerificationManager.SessionType type, String notice, NoticeType noticeType) {
        removeOwnedVerificationBooks(player);
        String token = verificationManager.issueToken(player.getUniqueId());
        ItemStack book = createBook(player.getUniqueId(), token, type, notice, noticeType);
        int slot = placeBook(player, book);
        verificationManager.setBookSlot(player.getUniqueId(), slot);
    }

    public boolean isVerificationBook(BookMeta meta, UUID uuid) {
        if (meta == null) {
            return false;
        }
        String expectedToken = verificationManager.getToken(uuid);
        if (expectedToken == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String token = pdc.get(tokenKey, PersistentDataType.STRING);
        String owner = pdc.get(ownerKey, PersistentDataType.STRING);
        return expectedToken.equals(token) && uuid.toString().equals(owner);
    }

    public void tagVerificationMeta(BookMeta meta, UUID uuid, VerificationManager.SessionType type) {
        if (meta == null || uuid == null || type == null) {
            return;
        }
        String token = verificationManager.getToken(uuid);
        if (token == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(tokenKey, PersistentDataType.STRING, token);
        pdc.set(ownerKey, PersistentDataType.STRING, uuid.toString());
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
    }


    public boolean isVerificationBook(ItemStack item, UUID uuid) {
        if (item == null || item.getType() != Material.WRITABLE_BOOK) {
            return false;
        }
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return false;
        }
        return isVerificationBook(meta, uuid);
    }

    public boolean hasValidBook(Player player) {
        UUID uuid = player.getUniqueId();
        String token = verificationManager.getToken(uuid);
        if (token == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) {
                continue;
            }
            Material type = item.getType();
            if (type != Material.WRITABLE_BOOK && type != Material.WRITTEN_BOOK) {
                continue;
            }
            if (!(item.getItemMeta() instanceof BookMeta meta)) {
                continue;
            }
            if (isVerificationBook(meta, uuid)) {
                return true;
            }
        }
        return false;
    }

    public void removeVerificationBook(Player player) {
        PlayerInventory inventory = player.getInventory();
        UUID uuid = player.getUniqueId();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (isOwnedVerificationBook(item, uuid)) {
                inventory.clear(i);
            }
        }
    }

    private void removeOwnedVerificationBooks(Player player) {
        PlayerInventory inventory = player.getInventory();
        UUID uuid = player.getUniqueId();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (isOwnedVerificationBook(item, uuid)) {
                inventory.clear(i);
            }
        }
    }

    private boolean isOwnedVerificationBook(ItemStack item, UUID uuid) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        if (type != Material.WRITABLE_BOOK && type != Material.WRITTEN_BOOK) {
            return false;
        }
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String owner = pdc.get(ownerKey, PersistentDataType.STRING);
        return uuid.toString().equals(owner);
    }

    public int placeBook(Player player, ItemStack book) {
        PlayerInventory inventory = player.getInventory();
        int emptySlot = inventory.firstEmpty();
        UserStore.PendingItem pending = userStore.getPendingItem(player.getUniqueId());
        int slot;
        if (emptySlot >= 0) {
            slot = emptySlot;
        } else if (pending != null && pending.slot() >= 0) {
            slot = pending.slot();
        } else {
            slot = inventory.getHeldItemSlot();
        }
        if (emptySlot < 0 && pending == null) {
            ItemStack replaced = inventory.getItem(slot);
            if (replaced != null && replaced.getType() != Material.AIR && !isAnyVerificationBook(replaced)) {
                userStore.setPendingItem(player.getUniqueId(), replaced, slot);
                player.sendMessage(plugin.message("pending-stored"));
                if (plugin.getConfig().getBoolean("log-pending", true)) {
                    plugin.getLogger().info("Stored pending item for " + player.getName()
                            + " slot=" + slot + " type=" + replaced.getType() + " x" + replaced.getAmount());
                }
            }
        }
        inventory.setItem(slot, book);
        player.updateInventory();
        return slot;
    }

    public void forceOpen(Player player) {
        verificationManager.setLastOpenTime(player.getUniqueId(), System.currentTimeMillis());
        boolean opened = NmsBookOpener.openBook(player);
        if (!opened) {
            plugin.getLogger().fine("Open book packet failed; player must right-click the book.");
        }
    }

    private ItemStack createBook(UUID uuid, String token, VerificationManager.SessionType type, String notice, NoticeType noticeType) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        if (!(book.getItemMeta() instanceof BookMeta meta)) {
            plugin.getLogger().warning("Failed to create BookMeta for verification book.");
            return book;
        }

        String name = plugin.getConfig().getString("book.item-name", "Verify Book");
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        meta.displayName(legacy.deserialize(name));
        List<String> pages = buildPages(uuid, type, notice, noticeType);
        List<net.kyori.adventure.text.Component> components = new ArrayList<>(pages.size());
        for (String page : pages) {
            components.add(legacy.deserialize(page));
        }
        meta.pages(components);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(tokenKey, PersistentDataType.STRING, token);
        pdc.set(ownerKey, PersistentDataType.STRING, uuid.toString());
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
        book.setItemMeta(meta);
        return book;
    }

    private List<String> buildPages(UUID uuid, VerificationManager.SessionType type, String notice, NoticeType noticeType) {
        List<String> pages = new ArrayList<>(3);
        pages.add(buildIntroPage(type));
        pages.add(buildInputPage(type));
        NoticeType typeToUse = noticeType != null ? noticeType : verificationManager.getNoticeType(uuid);
        pages.add(buildLogPage(notice, typeToUse));
        return pages;
    }

    private String buildIntroPage(VerificationManager.SessionType type) {
        List<String> lines = new ArrayList<>();
        String title = plugin.getConfig().getString("book.title", "Verification");
        if (title != null && !title.isBlank()) {
            lines.add(title);
        }
        List<String> guide = plugin.getConfig().getStringList(
                type == VerificationManager.SessionType.LOGIN ? "book.login-guide" : "book.register-guide");
        lines.addAll(guide);
        List<String> footer = plugin.getConfig().getStringList("book.intro-footer");
        if (!footer.isEmpty()) {
            lines.addAll(footer);
        }
        return String.join("\n", lines);
    }

    private String buildInputPage(VerificationManager.SessionType type) {
        List<String> lines = new ArrayList<>();
        List<String> input = plugin.getConfig().getStringList(
                type == VerificationManager.SessionType.LOGIN ? "book.input-login" : "book.input-register");
        lines.addAll(input);
        List<String> footer = plugin.getConfig().getStringList("book.input-footer");
        if (!footer.isEmpty()) {
            lines.addAll(footer);
        }
        return String.join("\n", lines);
    }

    private String buildLogPage(String notice, NoticeType type) {
        List<String> lines = new ArrayList<>();
        String title = plugin.getConfig().getString("book.log-title", "Log:");
        String empty = plugin.getConfig().getString("book.log-empty", "");
        String msg = notice == null || notice.isBlank() ? empty : notice;
        if (title != null && !title.isBlank()) {
            lines.add(title);
        }
        String color = type == NoticeType.ERROR
                ? plugin.getConfig().getString("book.log-color-error", "\u00A7c")
                : plugin.getConfig().getString("book.log-color-info", "\u00A7b");
        if (msg != null && !msg.isBlank()) {
            lines.add(color + msg);
        }
        return String.join("\n", lines);
    }

    public boolean isAnyVerificationBook(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        if (type != Material.WRITABLE_BOOK && type != Material.WRITTEN_BOOK) {
            return false;
        }
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return false;
        }
        return isAnyVerificationBook(meta);
    }

    public boolean isAnyVerificationBook(BookMeta meta) {
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(tokenKey, PersistentDataType.STRING)
                || pdc.has(ownerKey, PersistentDataType.STRING)
                || pdc.has(typeKey, PersistentDataType.STRING);
    }

    public void purgeVerificationBooks(org.bukkit.inventory.Inventory inventory, UUID allowedOwner) {
        if (inventory == null) {
            return;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (!isAnyVerificationBook(item)) {
                continue;
            }
            // Only remove verification books that belong to the allowed owner.
            if (allowedOwner != null && item != null && item.getItemMeta() instanceof BookMeta meta) {
                if (!isVerificationBook(meta, allowedOwner)) {
                    continue;
                }
            }
            inventory.clear(i);
        }
    }

    public void purgeVerificationBooksLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            purgeVerificationBooks(player.getInventory(), player.getUniqueId());
            Integer slot = verificationManager.getBookSlot(player.getUniqueId());
            if (slot != null) {
                ItemStack item = player.getInventory().getItem(slot);
                if (isAnyVerificationBook(item)) {
                    player.getInventory().clear(slot);
                }
            }
        });
    }
}
