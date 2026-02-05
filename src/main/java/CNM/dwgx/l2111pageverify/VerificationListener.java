package CNM.dwgx.l2111pageverify;

import CNM.dwgx.l2111pageverify.NoticeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

public final class VerificationListener implements Listener {

    private final L2111pageloginverify plugin;
    private final UserStore userStore;
    private final VerificationManager verificationManager;
    private final VerificationBookService bookService;

    public VerificationListener(L2111pageloginverify plugin, UserStore userStore, VerificationManager verificationManager,
                                VerificationBookService bookService) {
        this.plugin = plugin;
        this.userStore = userStore;
        this.verificationManager = verificationManager;
        this.bookService = bookService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.getConfig().getBoolean("require-online-mode", true)) {
            return;
        }
        if (!Bukkit.getOnlineMode()) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    net.kyori.adventure.text.Component.text(plugin.message("online-mode-required"))
            );
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isInitialized() && !player.hasPermission("dwgxverify.admin")) {
            player.kick(net.kyori.adventure.text.Component.text(plugin.message("not-initialized")));
            return;
        }
        UUID uuid = player.getUniqueId();
        verificationManager.clear(uuid);
        prepareFlight(player);

        if (!plugin.isInitialized()) {
            if (player.hasPermission("dwgxverify.admin")) {
                player.sendMessage(plugin.message("not-initialized"));
            }
            return;
        }
        if (!plugin.isVerificationEnabled()) {
            player.sendMessage(plugin.message("verification-disabled"));
            return;
        }

        if (userStore.hasUser(uuid)) {
            verificationManager.setSession(uuid, VerificationManager.SessionType.LOGIN);
            verificationManager.setNotice(uuid, plugin.message("open-login-book"), NoticeType.INFO);
            scheduleGiveBook(player, VerificationManager.SessionType.LOGIN);
            player.sendMessage(plugin.message("open-login-book"));
        } else {
            verificationManager.setSession(uuid, VerificationManager.SessionType.REGISTER);
            verificationManager.setNotice(uuid, plugin.message("open-register-book"), NoticeType.INFO);
            scheduleGiveBook(player, VerificationManager.SessionType.REGISTER);
            player.sendMessage(plugin.message("open-register-book"));
        }
        plugin.refreshVisibility();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        restoreFlight(player);
        verificationManager.clear(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!plugin.isVerificationEnabled()) {
            return;
        }
        if (verificationManager.isVerified(uuid)) {
            return;
        }
        if (!plugin.isInitialized()) {
            player.sendMessage(plugin.message("not-initialized"));
            return;
        }

        VerificationManager.SessionType session = verificationManager.getSession(uuid);
        if (session == null) {
            session = userStore.hasUser(uuid)
                    ? VerificationManager.SessionType.LOGIN
                    : VerificationManager.SessionType.REGISTER;
            verificationManager.setSession(uuid, session);
        }

        if (!bookService.isVerificationBook(event.getPreviousBookMeta(), uuid)) {
            event.setCancelled(true);
            verificationManager.setNotice(uuid, plugin.message("invalid-book"), NoticeType.ERROR);
            player.sendMessage(plugin.message("invalid-book"));
            scheduleGiveBook(player, session);
            return;
        }
        if (!event.isSigning()) {
            event.setCancelled(true);
            verificationManager.setNotice(uuid, plugin.message("must-sign"), NoticeType.ERROR);
            player.sendMessage(plugin.message("must-sign"));
            scheduleGiveBook(player, session);
            return;
        }

        var newMeta = event.getNewBookMeta();
        bookService.tagVerificationMeta(newMeta, uuid, session);
        event.setNewBookMeta(newMeta);
        InputData input = parseInput(getPageStrings(newMeta));
        if (session == VerificationManager.SessionType.LOGIN) {
            handleLogin(player, input);
        } else {
            handleRegister(player, input);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!shouldBlock(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        boolean moved = from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
        if (moved) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!shouldBlock(event.getPlayer())) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getItem() != null && bookService.isVerificationBook(event.getItem(), player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        showBlockTitle(player);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (shouldBlock(player)) {
            event.setCancelled(true);
            showBlockTitle(player);
            return;
        }
        bookService.purgeVerificationBooks(event.getInventory(), null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (shouldBlock(player)) {
            event.setCancelled(true);
            showBlockTitle(player);
            return;
        }
        if (bookService.isAnyVerificationBook(event.getCurrentItem())
                || bookService.isAnyVerificationBook(event.getCursor())) {
            event.setCancelled(true);
            if (bookService.isAnyVerificationBook(event.getCurrentItem())) {
                event.setCurrentItem(null);
            }
            if (bookService.isAnyVerificationBook(event.getCursor())) {
                event.getWhoClicked().setItemOnCursor(null);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (shouldBlock(player)) {
            event.setCancelled(true);
            showBlockTitle(player);
            return;
        }
        if (bookService.isAnyVerificationBook(event.getOldCursor())) {
            event.setCancelled(true);
            event.getWhoClicked().setItemOnCursor(null);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (shouldBlock(event.getPlayer()) && !plugin.isChatAllowedBeforeVerify()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!shouldBlock(player)) {
            return;
        }
        String message = event.getMessage().toLowerCase(Locale.ROOT);
        if (message.startsWith("/dwgxverify")) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(plugin.message("not-verified"));
        showBlockTitle(player);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (bookService.isAnyVerificationBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            return;
        }
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && shouldBlock(player)) {
            event.setCancelled(true);
            showBlockTitle(player);
            return;
        }
        if (bookService.isAnyVerificationBook(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (bookService.isAnyVerificationBook(event.getEntity().getItemStack())) {
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent event) {
        if (bookService.isAnyVerificationBook(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(true);
            showBlockTitle(event.getPlayer());
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (shouldBlock(event.getPlayer())) {
            event.setCancelled(false); // allow temporary flight to avoid kicks
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && shouldBlock(player)) {
            event.setCancelled(true);
            showBlockTitle(player);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player && shouldBlock(player)) {
            event.setCancelled(true);
            showBlockTitle(player);
            return;
        }
        if (event.getEntity() instanceof Player player && shouldBlock(player)) {
            event.setCancelled(true);
            showBlockTitle(player);
        }
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!shouldBlock(player)) {
            return;
        }
        showBlockTitle(player);
    }

    private boolean shouldBlock(Player player) {
        if (!plugin.isVerificationEnabled()) {
            return false;
        }
        return !verificationManager.isVerified(player.getUniqueId());
    }

    private void showBlockTitle(Player player) {
        if (!plugin.isVerificationEnabled()) {
            return;
        }
        if (verificationManager.isVerified(player.getUniqueId())) {
            return;
        }
        if (!plugin.getConfig().getBoolean("show-block-title", true)) {
            return;
        }
        String title = plugin.message("blocked-title");
        String sub = plugin.message("blocked-subtitle");
        net.kyori.adventure.text.Component titleComp = net.kyori.adventure.text.Component.text(title);
        net.kyori.adventure.text.Component subComp = net.kyori.adventure.text.Component.text(sub);
        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(250),
                java.time.Duration.ofMillis(1500),
                java.time.Duration.ofMillis(500)
        );
        player.showTitle(net.kyori.adventure.title.Title.title(titleComp, subComp, times));
    }

    private void playSuccessEffects(Player player) {
        String soundName = plugin.getConfig().getString("sound.success", "ENTITY_PLAYER_LEVELUP");
        float vol = (float) plugin.getConfig().getDouble("sound.volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("sound.pitch", 1.0);
        org.bukkit.Sound sound = resolveSound(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, vol, pitch);
        } else {
            plugin.getLogger().warning("Invalid sound in config: " + soundName);
        }

        String action = plugin.message("success-actionbar").replace("%player%", player.getName());
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        player.sendActionBar(legacy.deserialize(action));
    }

    private org.bukkit.Sound resolveSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        org.bukkit.NamespacedKey key;
        if (value.contains(":")) {
            int idx = value.indexOf(':');
            String ns = value.substring(0, idx);
            String path = value.substring(idx + 1).replace('_', '.').replace(' ', '.');
            key = new org.bukkit.NamespacedKey(ns, path);
        } else {
            String path = value.replace('_', '.').replace(' ', '.');
            key = org.bukkit.NamespacedKey.minecraft(path);
        }
        return org.bukkit.Registry.SOUNDS.get(key);
    }

    private void handleLogin(Player player, InputData input) {
        if (input.account() == null || input.password() == null) {
            verificationManager.setNotice(player.getUniqueId(), plugin.message("format-login"), NoticeType.ERROR);
            player.sendMessage(plugin.message("format-login"));
            scheduleGiveBook(player, VerificationManager.SessionType.LOGIN);
            return;
        }
        if (!userStore.verify(player.getUniqueId(), input.account(), input.password())) {
            verificationManager.setNotice(player.getUniqueId(), plugin.message("login-failed"), NoticeType.ERROR);
            player.sendMessage(plugin.message("login-failed"));
            scheduleGiveBook(player, VerificationManager.SessionType.LOGIN);
            return;
        }

        if (plugin.isAdminVerifyEnabled() && !userStore.isApproved(player.getUniqueId())) {
            verificationManager.setNotice(player.getUniqueId(), plugin.message("admin-verify-required"), NoticeType.ERROR);
            player.sendMessage(plugin.message("admin-verify-required"));
            scheduleGiveBook(player, VerificationManager.SessionType.LOGIN);
            return;
        }

        verificationManager.markVerified(player.getUniqueId());
        verificationManager.clearNotice(player.getUniqueId());
        player.sendMessage(plugin.message("login-success"));
        playSuccessEffects(player);
        userStore.updateLoginInfo(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            bookService.removeVerificationBook(player);
            bookService.purgeVerificationBooks(player.getInventory(), player.getUniqueId());
            bookService.purgeVerificationBooksLater(player);
            userStore.tryRestorePendingItem(player, true);
        });
        restoreFlight(player);
        plugin.refreshVisibility();
    }

    private void handleRegister(Player player, InputData input) {
        if (input.account() == null || input.password() == null || input.confirm() == null) {
            verificationManager.setNotice(player.getUniqueId(), plugin.message("format-register"), NoticeType.ERROR);
            player.sendMessage(plugin.message("format-register"));
            scheduleGiveBook(player, VerificationManager.SessionType.REGISTER);
            return;
        }
        String account = input.account();
        String password = input.password();
        String confirm = input.confirm();
        if (account.isEmpty() || password.isEmpty()) {
            verificationManager.setNotice(player.getUniqueId(), plugin.message("format-register"), NoticeType.ERROR);
            player.sendMessage(plugin.message("format-register"));
            scheduleGiveBook(player, VerificationManager.SessionType.REGISTER);
            return;
        }
        if (!password.equals(confirm)) {
            verificationManager.setNotice(player.getUniqueId(), plugin.message("password-mismatch"), NoticeType.ERROR);
            player.sendMessage(plugin.message("password-mismatch"));
            scheduleGiveBook(player, VerificationManager.SessionType.REGISTER);
            return;
        }
        if (userStore.isAccountTaken(account)) {
            verificationManager.setNotice(player.getUniqueId(), plugin.message("account-taken"), NoticeType.ERROR);
            player.sendMessage(plugin.message("account-taken"));
            scheduleGiveBook(player, VerificationManager.SessionType.REGISTER);
            return;
        }

        String registerIp = "";
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            registerIp = player.getAddress().getAddress().getHostAddress();
        }

        boolean registered = userStore.register(
                player.getUniqueId(),
                account,
                password,
                plugin.getDefaultPasswordMode(),
                player.getName(),
                registerIp,
                !plugin.isAdminVerifyEnabled()
        );
        if (!registered) {
            verificationManager.setNotice(player.getUniqueId(),
                    plugin.message("register-failed").replace("%reason%", plugin.message("register-already")),
                    NoticeType.ERROR);
            player.sendMessage(plugin.message("register-failed").replace("%reason%", plugin.message("register-already")));
            scheduleGiveBook(player, VerificationManager.SessionType.REGISTER);
            return;
        }

        if (plugin.isAdminVerifyEnabled()) {
            verificationManager.clearNotice(player.getUniqueId());
            player.sendMessage(plugin.message("admin-verify-wait"));
            Bukkit.getScheduler().runTask(plugin, () -> {
                bookService.removeVerificationBook(player);
                bookService.purgeVerificationBooks(player.getInventory(), player.getUniqueId());
                bookService.purgeVerificationBooksLater(player);
            });
            plugin.refreshVisibility();
            return;
        }

        verificationManager.markVerified(player.getUniqueId());
        verificationManager.clearNotice(player.getUniqueId());
        player.sendMessage(plugin.message("register-success"));
        playSuccessEffects(player);
        userStore.updateLoginInfo(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            bookService.removeVerificationBook(player);
            bookService.purgeVerificationBooks(player.getInventory(), player.getUniqueId());
            bookService.purgeVerificationBooksLater(player);
            userStore.tryRestorePendingItem(player, true);
        });
        restoreFlight(player);
        plugin.refreshVisibility();
    }

    private void prepareFlight(Player player) {
        if (!plugin.getConfig().getBoolean("allow-flight-while-unverified", true)) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        if (!shouldBlock(player)) {
            return;
        }
        verificationManager.snapshotFlight(player.getUniqueId(), player.getAllowFlight(), player.isFlying());
        player.setAllowFlight(true);
        player.setFlying(false);
    }

    private void restoreFlight(Player player) {
        Boolean prevAllow = verificationManager.getPrevAllowFlight(player.getUniqueId());
        Boolean prevFlying = verificationManager.getPrevFlying(player.getUniqueId());
        if (prevAllow == null || prevFlying == null) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        player.setAllowFlight(prevAllow);
        player.setFlying(prevFlying && prevAllow);
    }

    private void scheduleGiveBook(Player player, VerificationManager.SessionType type) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isVerificationEnabled()) {
                return;
            }
            if (player.isOnline() && !verificationManager.isVerified(player.getUniqueId())) {
                String notice = verificationManager.getNotice(player.getUniqueId());
                NoticeType nt = verificationManager.getNoticeType(player.getUniqueId());
                bookService.giveBook(player, type, notice, nt);
                if (plugin.getConfig().getBoolean("auto-open-book", false)) {
                    bookService.forceOpen(player);
                }
            }
        }, 10L);
    }

    private InputData parseInput(List<String> pages) {
        if (pages == null || pages.isEmpty()) {
            return new InputData(null, null, null);
        }
        String page;
        if (pages.size() >= 2) {
            page = pages.get(1);
        } else {
            page = pages.get(0);
        }
        List<String> lines = extractLinesFromPage(page);

        String account = null;
        String password = null;
        String confirm = null;
        InputKey pendingKey = null;
        int filled = 0;

        for (String line : lines) {
            String[] kv = splitKeyValue(line);
            if (kv != null) {
                InputKey key = parseKey(kv[0]);
                if (key == null) {
                    continue;
                }
                String value = kv[1];
                if (value.isEmpty()) {
                    pendingKey = key;
                    continue;
                }
                switch (key) {
                    case ACCOUNT -> account = value;
                    case PASSWORD -> password = value;
                    case CONFIRM -> confirm = value;
                }
                pendingKey = null;
                filled++;
                continue;
            }

            InputKey inlineKey = parseKey(line);
            if (inlineKey != null) {
                pendingKey = inlineKey;
                continue;
            }

            if (pendingKey != null) {
                switch (pendingKey) {
                    case ACCOUNT -> account = line;
                    case PASSWORD -> password = line;
                    case CONFIRM -> confirm = line;
                }
                pendingKey = null;
                filled++;
            }
        }

        if (account != null) account = account.trim();
        if (password != null) password = password.trim();
        if (confirm != null) confirm = confirm.trim();
        return new InputData(account, password, confirm);
    }

    private List<String> extractLinesFromPage(String page) {
        List<String> lines = new ArrayList<>();
        if (page == null) {
            return lines;
        }
        for (String rawLine : page.split("\n")) {
            String line = stripLegacyColors(rawLine).trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private List<String> getPageStrings(org.bukkit.inventory.meta.BookMeta meta) {
        if (meta == null) {
            return List.of();
        }
        List<net.kyori.adventure.text.Component> pages = meta.pages();
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        var serializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        List<String> result = new ArrayList<>(pages.size());
        for (net.kyori.adventure.text.Component page : pages) {
            result.add(serializer.serialize(page));
        }
        return result;
    }

    private String stripLegacyColors(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "");
    }

    private String[] splitKeyValue(String line) {
        int idx = indexOfSeparator(line);
        if (idx < 0) {
            return null;
        }
        String key = line.substring(0, idx).trim();
        String value = line.substring(idx + 1).trim();
        return new String[]{key, value};
    }

    private int indexOfSeparator(String line) {
        int colon = line.indexOf(':');
        int fullColon = line.indexOf('\uFF1A'); // Full-width colon
        int equal = line.indexOf('=');
        int idx = -1;
        if (colon >= 0) {
            idx = colon;
        }
        if (fullColon >= 0 && (idx < 0 || fullColon < idx)) {
            idx = fullColon;
        }
        if (equal >= 0 && (idx < 0 || equal < idx)) {
            idx = equal;
        }
        return idx;
    }

    private InputKey parseKey(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return null;
        }
        if (key.contains("\u8d26\u53f7") || key.contains("account") || key.contains("user")) {
            return InputKey.ACCOUNT;
        }
        if (key.contains("\u786e\u8ba4") || key.contains("confirm") || key.contains("repeat")) {
            return InputKey.CONFIRM;
        }
        if (key.contains("\u5bc6\u7801") || key.contains("pass")) {
            return InputKey.PASSWORD;
        }
        return null;
    }

    private record InputData(String account, String password, String confirm) {
    }

    private enum InputKey {
        ACCOUNT,
        PASSWORD,
        CONFIRM
    }
}
