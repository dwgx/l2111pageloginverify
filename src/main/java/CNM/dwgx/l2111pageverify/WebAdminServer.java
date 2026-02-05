package CNM.dwgx.l2111pageverify;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.inventory.ItemStack;

public final class WebAdminServer {

    private final L2111pageloginverify plugin;
    private final UserStore userStore;
    private HttpServer server;
    private ExecutorService executor;
    private String bind;
    private int port;

    public WebAdminServer(L2111pageloginverify plugin, UserStore userStore) {
        this.plugin = plugin;
        this.userStore = userStore;
    }

    public synchronized void start() {
        if (!plugin.getConfig().getBoolean("web.enabled", true)) {
            return;
        }
        String configBind = plugin.getConfig().getString("web.bind", "127.0.0.1");
        int configPort = plugin.getConfig().getInt("web.port", 1337);
        bind = configBind;
        port = configPort;
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to start web admin server: " + ex.getMessage());
            return;
        }
        server.createContext("/", new RootHandler());
        server.createContext("/api/users", new UsersHandler());
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/api/approve", new ApproveHandler());
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        plugin.getLogger().info("Web admin server started at http://" + bind + ":" + port);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private boolean checkAuth(HttpExchange exchange) throws IOException {
        if (!checkLocalOnly(exchange)) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("web.auth.enabled", true)) {
            return true;
        }
        String user = plugin.getConfig().getString("web.auth.username", "admin");
        String pass = plugin.getConfig().getString("web.auth.password", "change-me");
        Headers headers = exchange.getRequestHeaders();
        String auth = headers.getFirst("Authorization");
        if (auth == null || !auth.toLowerCase(Locale.ROOT).startsWith("basic ")) {
            sendAuthRequired(exchange);
            return false;
        }
        String encoded = auth.substring(6).trim();
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            sendAuthRequired(exchange);
            return false;
        }
        String expected = user + ":" + pass;
        if (!expected.equals(decoded)) {
            sendAuthRequired(exchange);
            return false;
        }
        return true;
    }

    private boolean checkLocalOnly(HttpExchange exchange) throws IOException {
        boolean localOnly = plugin.getConfig().getBoolean("web.local-only", true);
        if (!localOnly) {
            return true;
        }
        java.net.InetAddress address = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress()
                : null;
        boolean allowed = address != null && (address.isLoopbackAddress() || address.isAnyLocalAddress());
        if (!allowed) {
            sendText(exchange, 403, "Forbidden", "text/plain");
            return false;
        }
        return true;
    }

    private void sendAuthRequired(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"Verify\"");
        exchange.sendResponseHeaders(401, -1);
        exchange.close();
    }

    private void sendText(HttpExchange exchange, int code, String content, String contentType) throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private String text(String key, String fallback) {
        return plugin.getConfig().getString("web.text." + key, fallback);
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                return;
            }
            String html = buildHtml();
            sendText(exchange, 200, html, "text/html");
        }
    }

    private class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                return;
            }
            Map<String, String> params = readParams(exchange);
            String json = buildUsersJson(params);
            sendText(exchange, 200, json, "application/json");
        }
    }

    private class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = readParams(exchange);
                applyConfig(params);
            }
            String json = buildConfigJson();
            sendText(exchange, 200, json, "application/json");
        }
    }

    private class ApproveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "{\"ok\":false}", "application/json");
                return;
            }
            Map<String, String> params = readParams(exchange);
            String uuidRaw = params.get("uuid");
            boolean approve = parseBool(params.getOrDefault("approved", "true"));
            boolean ok = false;
            if (uuidRaw != null) {
                try {
                    UUID uuid = UUID.fromString(uuidRaw);
                    ok = userStore.setApproved(uuid, approve);
                    if (ok && approve) {
                        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
                        if (player != null) {
                            plugin.unlockPlayerAfterApproval(player);
                            player.sendMessage(plugin.message("admin-verify-approved"));
                        }
                    } else if (ok && !approve) {
                        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
                        if (player != null) {
                            plugin.lockPlayerAfterUnapproval(player);
                            player.sendMessage(plugin.message("admin-verify-required"));
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    ok = false;
                }
            }
            sendText(exchange, 200, "{\"ok\":" + ok + "}", "application/json");
        }
    }

    private Map<String, String> readParams(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String body = "";
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> params = new LinkedHashMap<>();
        parseParams(params, query);
        parseParams(params, body);
        return params;
    }

    private void parseParams(Map<String, String> params, String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
    }

    private void applyConfig(Map<String, String> params) {
        if (params.containsKey("enabled")) {
            plugin.setVerificationEnabled(parseBool(params.get("enabled")));
        }
        if (params.containsKey("chat")) {
            plugin.setChatAllowedBeforeVerify(parseBool(params.get("chat")));
        }
        if (params.containsKey("hide")) {
            boolean hide = parseBool(params.get("hide"));
            plugin.getConfig().set("hide-unverified", hide);
            plugin.saveConfig();
            plugin.refreshVisibility();
        }
        if (params.containsKey("adminverify")) {
            boolean enabled = parseBool(params.get("adminverify"));
            plugin.setAdminVerifyEnabled(enabled);
        }
        if (params.containsKey("localonly")) {
            plugin.getConfig().set("web.local-only", parseBool(params.get("localonly")));
            plugin.saveConfig();
        }
        if (params.containsKey("encryption")) {
            boolean enabled = parseBool(params.get("encryption"));
            plugin.setEncryptionEnabled(enabled);
            userStore.reloadForMode(plugin.getDefaultPasswordMode());
        }
        if (params.containsKey("sound")) {
            plugin.getConfig().set("sound.success", params.get("sound"));
            if (params.containsKey("volume")) {
                plugin.getConfig().set("sound.volume", parseDouble(params.get("volume"), 1.0));
            }
            if (params.containsKey("pitch")) {
                plugin.getConfig().set("sound.pitch", parseDouble(params.get("pitch"), 1.0));
            }
            plugin.saveConfig();
        }
    }

    private boolean parseBool(String raw) {
        if (raw == null) {
            return false;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return "true".equals(v) || "on".equals(v) || "1".equals(v) || "yes".equals(v);
    }

    private int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private double parseDouble(String raw, double def) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private String buildConfigJson() {
        boolean enabled = plugin.isVerificationEnabled();
        boolean chat = plugin.isChatAllowedBeforeVerify();
        boolean hide = plugin.getConfig().getBoolean("hide-unverified", false);
        boolean adminVerify = plugin.isAdminVerifyEnabled();
        boolean localOnly = plugin.getConfig().getBoolean("web.local-only", true);
        boolean encryption = plugin.isEncryptionEnabled();
        String sound = plugin.getConfig().getString("sound.success", "ENTITY_PLAYER_LEVELUP");
        double volume = plugin.getConfig().getDouble("sound.volume", 1.0);
        double pitch = plugin.getConfig().getDouble("sound.pitch", 1.0);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"enabled\":").append(enabled).append(',');
        sb.append("\"chat\":").append(chat).append(',');
        sb.append("\"hide\":").append(hide).append(',');
        sb.append("\"adminverify\":").append(adminVerify).append(',');
        sb.append("\"localonly\":").append(localOnly).append(',');
        sb.append("\"encryption\":").append(encryption).append(',');
        sb.append("\"sound\":\"").append(escapeJson(sound)).append("\",");
        sb.append("\"volume\":").append(volume).append(',');
        sb.append("\"pitch\":").append(pitch);
        sb.append("}");
        return sb.toString();
    }

    private String buildUsersJson(Map<String, String> params) {
        boolean encryption = plugin.isEncryptionEnabled();
        String query = params.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
        int page = parseInt(params.getOrDefault("page", "1"), 1);
        int size = parseInt(params.getOrDefault("size", "50"), 50);
        if (size < 5) {
            size = 5;
        } else if (size > 200) {
            size = 200;
        }
        if (page < 1) {
            page = 1;
        }

        List<Map.Entry<UUID, UserRecord>> all = new ArrayList<>(userStore.getUsersSnapshot().entrySet());
        all.sort((a, b) -> {
            long at = a.getValue().registeredAt();
            long bt = b.getValue().registeredAt();
            int cmp = Long.compare(bt, at);
            if (cmp != 0) {
                return cmp;
            }
            return a.getKey().toString().compareTo(b.getKey().toString());
        });

        List<Map.Entry<UUID, UserRecord>> filtered = new ArrayList<>();
        for (Map.Entry<UUID, UserRecord> entry : all) {
            UserRecord record = entry.getValue();
            if (!query.isEmpty()) {
                String uuid = record.uuid().toString().toLowerCase(Locale.ROOT);
                String name = nullToEmpty(record.minecraftName()).toLowerCase(Locale.ROOT);
                String account = nullToEmpty(record.account()).toLowerCase(Locale.ROOT);
                if (!uuid.contains(query) && !name.contains(query) && !account.contains(query)) {
                    continue;
                }
            }
            filtered.add(entry);
        }

        int total = filtered.size();
        int start = (page - 1) * size;
        if (start >= total && total > 0) {
            page = 1;
            start = 0;
        }
        int end = Math.min(start + size, total);

        List<String> entries = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Map.Entry<UUID, UserRecord> entry = filtered.get(i);
            UserRecord record = entry.getValue();
            UserStore.PendingItem pending = userStore.getPendingItem(entry.getKey());
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"uuid\":\"").append(record.uuid()).append("\",");
            sb.append("\"mcName\":\"").append(escapeJson(nullToEmpty(record.minecraftName()))).append("\",");
            sb.append("\"account\":\"").append(escapeJson(nullToEmpty(record.account()))).append("\",");
            sb.append("\"password\":\"").append(escapeJson(nullToEmpty(record.password()))).append("\",");
            sb.append("\"salt\":\"").append(escapeJson(nullToEmpty(record.salt()))).append("\",");
            sb.append("\"registerAt\":").append(record.registeredAt()).append(',');
            sb.append("\"approved\":").append(record.approved()).append(',');
            sb.append("\"mode\":\"").append(record.mode().name()).append("\",");
            sb.append("\"lastLoginIp\":\"").append(escapeJson(nullToEmpty(record.lastLoginIp()))).append("\",");
            sb.append("\"lastLoginAt\":").append(record.lastLoginAt()).append(',');
            sb.append("\"lastLoginSalt\":\"").append(escapeJson(nullToEmpty(record.lastLoginSalt()))).append("\",");
            if (pending != null && pending.item() != null) {
                ItemStack item = pending.item();
                sb.append("\"pendingType\":\"").append(escapeJson(item.getType().name())).append("\",");
                sb.append("\"pendingSlot\":").append(pending.slot()).append(',');
                sb.append("\"pendingAmount\":").append(item.getAmount()).append(',');
            } else {
                sb.append("\"pendingType\":\"\",");
                sb.append("\"pendingSlot\":-1,");
                sb.append("\"pendingAmount\":0,");
            }
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.setLength(sb.length() - 1);
            }
            sb.append("}");
            entries.add(sb.toString());
        }
        StringBuilder root = new StringBuilder();
        root.append("{");
        root.append("\"mode\":\"").append(encryption ? "HASHED" : "PLAINTEXT").append("\",");
        root.append("\"page\":").append(page).append(',');
        root.append("\"size\":").append(size).append(',');
        root.append("\"total\":").append(total).append(',');
        root.append("\"users\":[").append(String.join(",", entries)).append("]}");
        return root.toString();
    }

    private String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String nullToEmpty(String raw) {
        return raw == null ? "" : raw;
    }

    private String buildHtml() {
        String title = text("title", "Verify Admin");
        String subtitle = text("subtitle", "l2111pageloginverify Web");
        String sectionUsers = text("section-users", "Users");
        String sectionSettings = text("section-settings", "Settings");
        String colUuid = text("column-uuid", "UUID");
        String colName = text("column-name", "Name");
        String colAvatar = text("column-avatar", "Avatar");
        String colAccount = text("column-account", "Account");
        String colPassword = text("column-password", "Password/Hash");
        String colKey = text("column-key", "Key");
        String colRegister = text("column-register", "Register");
        String colLogin = text("column-login", "Last Login");
        String colPending = text("column-pending", "Pending");
        String settingEnabled = text("setting-enabled", "Verification");
        String settingEncryption = text("setting-encryption", "Encryption");
        String settingChat = text("setting-chat", "Chat Before Verify");
        String settingAdminVerify = text("setting-admin-verify", "Admin Verify");
        String settingHide = text("setting-hide", "Hide Unverified");
        String settingLocalOnly = text("setting-local-only", "Local Only");
        String settingSound = text("setting-sound", "Sound");
        String settingVolume = text("setting-volume", "volume / pitch");
        String statusOn = text("status-on", "ON");
        String statusOff = text("status-off", "OFF");
        String actionApprove = text("action-approve", "Approve");
        String actionUnapprove = text("action-unapprove", "Unapprove");
        String statusApproved = text("status-approved", "Approved");
        String statusPending = text("status-pending", "Pending");
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"utf-8\" />\n");
        sb.append("<title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("<link rel=\"stylesheet\" href=\"https://unpkg.com/minecraft-css@1.1.0/dist/minecraft.min.css\">\n");
        sb.append("<style>\n");
        sb.append("body { background: #0f0f12; color: #e7e7e7; font-family: \"Minecraft\", monospace; }\n");
        sb.append(".container { max-width: 1200px; margin: 24px auto; padding: 16px; }\n");
        sb.append(".panel { background: #1b1b1f; border: 2px solid #3a3a44; padding: 16px; margin-bottom: 16px; border-radius: 6px; }\n");
        sb.append(".title { font-size: 24px; margin-bottom: 6px; }\n");
        sb.append(".subtitle { color: #9aa0a6; margin-bottom: 12px; }\n");
        sb.append(".grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }\n");
        sb.append("label { display: block; margin-bottom: 6px; }\n");
        sb.append("select, input[type=text], input[type=number] { width: 100%; padding: 6px; background: #14151a; color: #e7e7e7; border: 1px solid #3a3a44; border-radius: 4px; }\n");
        sb.append("select:focus, input:focus { outline: 2px solid #5fead2; box-shadow: 0 0 0 2px rgba(95,234,210,0.35); }\n");
        sb.append(".toast { position: fixed; right: 20px; bottom: 20px; background: #1f2937; color: #e7e7e7; padding: 10px 14px; border-radius: 6px; border: 1px solid #3a3a44; display: none; }\n");
        sb.append(".toast.ok { border-color: #2c7; }\n");
        sb.append(".toast.err { border-color: #c33; }\n");
        sb.append(".table { width: 100%; border-collapse: collapse; }\n");
        sb.append(".table th, .table td { border-bottom: 1px solid #333; padding: 8px; font-size: 12px; }\n");
        sb.append(".avatar { width: 32px; height: 32px; image-rendering: pixelated; }\n");
        sb.append("</style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<div class=\"container\">\n");
        sb.append("  <div class=\"panel\">\n");
        sb.append("    <div class=\"title\">").append(escapeHtml(title)).append("</div>\n");
        sb.append("    <div class=\"subtitle\">").append(escapeHtml(subtitle)).append("</div>\n");
        sb.append("  </div>\n\n");
        sb.append("  <div class=\"panel\">\n");
        sb.append("    <h3>").append(escapeHtml(sectionSettings)).append("</h3>\n");
        sb.append("    <div class=\"grid\">\n");
        sb.append("      <div>\n");
        sb.append("        <label>").append(escapeHtml(settingEnabled)).append("</label>\n");
        sb.append("        <select id=\"enabled\">\n");
        sb.append("          <option value=\"true\">").append(escapeHtml(statusOn)).append("</option>\n");
        sb.append("          <option value=\"false\">").append(escapeHtml(statusOff)).append("</option>\n");
        sb.append("        </select>\n");
        sb.append("      </div>\n");
        sb.append("      <div>\n");
        sb.append("        <label>").append(escapeHtml(settingEncryption)).append("</label>\n");
        sb.append("        <select id=\"encryption\">\n");
        sb.append("          <option value=\"true\">").append(escapeHtml(statusOn)).append("</option>\n");
        sb.append("          <option value=\"false\">").append(escapeHtml(statusOff)).append("</option>\n");
        sb.append("        </select>\n");
        sb.append("      </div>\n");
        sb.append("      <div>\n");
        sb.append("        <label>").append(escapeHtml(settingChat)).append("</label>\n");
        sb.append("        <select id=\"chat\">\n");
        sb.append("          <option value=\"true\">").append(escapeHtml(statusOn)).append("</option>\n");
        sb.append("          <option value=\"false\">").append(escapeHtml(statusOff)).append("</option>\n");
        sb.append("        </select>\n");
        sb.append("      </div>\n");
        sb.append("      <div>\n");
        sb.append("        <label>").append(escapeHtml(settingLocalOnly)).append("</label>\n");
        sb.append("        <select id=\"localonly\">\n");
        sb.append("          <option value=\"true\">").append(escapeHtml(statusOn)).append("</option>\n");
        sb.append("          <option value=\"false\">").append(escapeHtml(statusOff)).append("</option>\n");
        sb.append("        </select>\n");
        sb.append("      </div>\n");
        sb.append("      <div>\n");
        sb.append("        <label>").append(escapeHtml(settingAdminVerify)).append("</label>\n");
        sb.append("        <select id=\"adminverify\">\n");
        sb.append("          <option value=\"true\">").append(escapeHtml(statusOn)).append("</option>\n");
        sb.append("          <option value=\"false\">").append(escapeHtml(statusOff)).append("</option>\n");
        sb.append("        </select>\n");
        sb.append("      </div>\n");
        sb.append("      <div>\n");
        sb.append("        <label>").append(escapeHtml(settingHide)).append("</label>\n");
        sb.append("        <select id=\"hide\">\n");
        sb.append("          <option value=\"true\">").append(escapeHtml(statusOn)).append("</option>\n");
        sb.append("          <option value=\"false\">").append(escapeHtml(statusOff)).append("</option>\n");
        sb.append("        </select>\n");
        sb.append("      </div>\n");
        sb.append("      <div>\n");
        sb.append("        <label>").append(escapeHtml(settingSound)).append("</label>\n");
        sb.append("        <input type=\"text\" id=\"sound\" />\n");
        sb.append("      </div>\n");
        sb.append("      <div>\n");
        sb.append("        <label>").append(escapeHtml(settingVolume)).append("</label>\n");
        sb.append("        <div style=\"display:flex; gap:6px;\">\n");
        sb.append("          <input type=\"number\" step=\"0.1\" id=\"volume\" />\n");
        sb.append("          <input type=\"number\" step=\"0.1\" id=\"pitch\" />\n");
        sb.append("        </div>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n\n");
        sb.append("  <div class=\"panel\">\n");
        sb.append("    <h3>").append(escapeHtml(sectionUsers)).append("</h3>\n");
        sb.append("    <div style=\\\"display:flex; gap:12px; align-items:center; margin-bottom:10px;\\\">\n");
        sb.append("      <input type=\\\"text\\\" id=\\\"search\\\" placeholder=\\\"Search uuid/name/account\\\" style=\\\"flex:1;\\\" />\n");
        sb.append("      <select id=\\\"size\\\" style=\\\"width:90px;\\\">\n");
        sb.append("        <option value=\\\"25\\\">25</option>\n");
        sb.append("        <option value=\\\"50\\\" selected>50</option>\n");
        sb.append("        <option value=\\\"100\\\">100</option>\n");
        sb.append("      </select>\n");
        sb.append("      <button class=\\\"minecraft-button\\\" id=\\\"prev\\\">Prev</button>\n");
        sb.append("      <span id=\\\"pageinfo\\\">1/1</span>\n");
        sb.append("      <button class=\\\"minecraft-button\\\" id=\\\"next\\\">Next</button>\n");
        sb.append("    </div>\n");
        sb.append("    <table class=\"table\" id=\"users\">\n");
        sb.append("      <thead>\n");
        sb.append("        <tr>\n");
        sb.append("          <th>").append(escapeHtml(colUuid)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(colAvatar)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(colName)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(colAccount)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(colPassword)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(colKey)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(colRegister)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(colLogin)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(colPending)).append("</th>\n");
        sb.append("          <th>").append(escapeHtml(settingAdminVerify)).append("</th>\n");
        sb.append("        </tr>\n");
        sb.append("      </thead>\n");
        sb.append("      <tbody></tbody>\n");
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");
        sb.append("<div id=\\\"toast\\\" class=\\\"toast\\\"></div>\n");
        sb.append("<script>\n");
        sb.append("async function loadConfig(){\n");
        sb.append("  const res = await fetch('/api/config');\n");
        sb.append("  const cfg = await res.json();\n");
        sb.append("  document.getElementById('enabled').value = String(cfg.enabled);\n");
        sb.append("  document.getElementById('chat').value = String(cfg.chat);\n");
        sb.append("  document.getElementById('hide').value = String(cfg.hide);\n");
        sb.append("  document.getElementById('adminverify').value = String(cfg.adminverify);\n");
        sb.append("  document.getElementById('localonly').value = String(cfg.localonly);\n");
        sb.append("  document.getElementById('encryption').value = String(cfg.encryption);\n");
        sb.append("  document.getElementById('sound').value = cfg.sound || '';\n");
        sb.append("  document.getElementById('volume').value = cfg.volume || 1;\n");
        sb.append("  document.getElementById('pitch').value = cfg.pitch || 1;\n");
        sb.append("}\n\n");
        sb.append("async function saveConfig(){\n");
        sb.append("  const data = new URLSearchParams();\n");
        sb.append("  data.set('enabled', document.getElementById('enabled').value);\n");
        sb.append("  data.set('chat', document.getElementById('chat').value);\n");
        sb.append("  data.set('hide', document.getElementById('hide').value);\n");
        sb.append("  data.set('encryption', document.getElementById('encryption').value);\n");
        sb.append("  data.set('adminverify', document.getElementById('adminverify').value);\n");
        sb.append("  data.set('localonly', document.getElementById('localonly').value);\n");
        sb.append("  data.set('sound', document.getElementById('sound').value);\n");
        sb.append("  data.set('volume', document.getElementById('volume').value);\n");
        sb.append("  data.set('pitch', document.getElementById('pitch').value);\n");
        sb.append("  await fetch('/api/config', {method:'POST', body:data});\n");
        sb.append("  toast('Saved', 'ok');\n");
        sb.append("  await loadUsers();\n");
        sb.append("}\n\n");
        sb.append("function toast(msg, type){\n");
        sb.append("  let box = document.getElementById('toast');\n");
        sb.append("  if(!box){\n");
        sb.append("    box = document.createElement('div');\n");
        sb.append("    box.id = 'toast';\n");
        sb.append("    box.className = 'toast';\n");
        sb.append("    document.body.appendChild(box);\n");
        sb.append("  }\n");
        sb.append("  box.className = 'toast ' + (type || 'ok');\n");
        sb.append("  box.textContent = msg;\n");
        sb.append("  box.style.display = 'block';\n");
        sb.append("  clearTimeout(window.__toastTimer);\n");
        sb.append("  window.__toastTimer = setTimeout(()=>{ box.style.display = 'none'; }, 2000);\n");
        sb.append("}\n\n");
        sb.append("function fmtTime(ts){\n");
        sb.append("  if(!ts || ts<=0) return '-';\n");
        sb.append("  const d = new Date(ts);\n");
        sb.append("  return d.toLocaleString();\n");
        sb.append("}\n\n");
        sb.append("let state = { page: 1, size: 50, q: '' };\n");
        sb.append("async function loadUsers(){\n");
        sb.append("  const params = new URLSearchParams();\n");
        sb.append("  params.set('page', String(state.page));\n");
        sb.append("  params.set('size', String(state.size));\n");
        sb.append("  if(state.q){ params.set('q', state.q); }\n");
        sb.append("  const res = await fetch('/api/users?' + params.toString());\n");
        sb.append("  const data = await res.json();\n");
        sb.append("  const tbody = document.querySelector('#users tbody');\n");
        sb.append("  tbody.innerHTML = '';\n");
        sb.append("  const totalPages = Math.max(1, Math.ceil((data.total || 0) / (data.size || state.size)));\n");
        sb.append("  state.page = data.page || state.page;\n");
        sb.append("  document.getElementById('pageinfo').textContent = state.page + '/' + totalPages;\n");
        sb.append("  document.getElementById('prev').disabled = state.page <= 1;\n");
        sb.append("  document.getElementById('next').disabled = state.page >= totalPages;\n");
        sb.append("  for(const u of data.users){\n");
        sb.append("    const tr = document.createElement('tr');\n");
        sb.append("    const avatar = 'https://crafatar.com/avatars/' + u.uuid + '?size=32&overlay';\n");
        sb.append("    const fallback = u.mcName ? ('https://minotar.net/avatar/' + u.mcName + '/32') : '';\n");
        sb.append("    const keySalt = (u.mode === 'HASHED') ? (u.salt || '-') : (u.lastLoginSalt || '-');\n");
        sb.append("    const approveCell = u.approved\n");
        sb.append("      ? '<button class=\\\"minecraft-button\\\" onclick=\\\"setApproval(\\'' + u.uuid + '\\', false)\\\">" + escapeHtml(actionUnapprove) + "</button>'\n");
        sb.append("      : '<button class=\\\"minecraft-button\\\" onclick=\\\"setApproval(\\'' + u.uuid + '\\', true)\\\">" + escapeHtml(actionApprove) + "</button>';\n");
        sb.append("    tr.innerHTML =\n");
        sb.append("      '<td>' + u.uuid + '</td>' +\n");
        sb.append("      '<td><img class=\\\"avatar\\\" src=\\\"' + avatar + '\\\" onerror=\\\"this.onerror=null; if(\\'' + fallback + '\\') { this.src=\\'' + fallback + '\\'; }\\\" /></td>' +\n");
        sb.append("      '<td>' + (u.mcName || '-') + '</td>' +\n");
        sb.append("      '<td>' + (u.account || '-') + '</td>' +\n");
        sb.append("      '<td>' + (u.password || '-') + '</td>' +\n");
        sb.append("      '<td>' + keySalt + '</td>' +\n");
        sb.append("      '<td>' + fmtTime(u.registerAt) + '</td>' +\n");
        sb.append("      '<td>' + (u.lastLoginIp || '-') + '<br>' + fmtTime(u.lastLoginAt) + '<br>' + (u.lastLoginSalt || '-') + '</td>' +\n");
        sb.append("      '<td>' + (u.pendingType ? (u.pendingType + ' x' + u.pendingAmount + ' @' + u.pendingSlot) : '-') + '</td>' +\n");
        sb.append("      '<td>' + approveCell + '</td>';\n");
        sb.append("    tbody.appendChild(tr);\n");
        sb.append("  }\n");
        sb.append("}\n\n");
        sb.append("async function setApproval(uuid, approved){\n");
        sb.append("  const data = new URLSearchParams();\n");
        sb.append("  data.set('uuid', uuid);\n");
        sb.append("  data.set('approved', approved ? 'true' : 'false');\n");
        sb.append("  const res = await fetch('/api/approve', {method:'POST', body:data});\n");
        sb.append("  const json = await res.json();\n");
        sb.append("  if(json.ok){ toast(approved ? 'Approved' : 'Unapproved', 'ok'); } else { toast('Failed', 'err'); }\n");
        sb.append("  await loadUsers();\n");
        sb.append("}\n\n");
        sb.append("function bindAutoSave(id){\n");
        sb.append("  const el = document.getElementById(id);\n");
        sb.append("  el.addEventListener('change', debounce(saveConfig, 200));\n");
        sb.append("}\n");
        sb.append("function debounce(fn, ms){\n");
        sb.append("  let t; return function(){ clearTimeout(t); t = setTimeout(fn, ms); };\n");
        sb.append("}\n");
        sb.append("['enabled','chat','hide','encryption','adminverify','localonly','sound','volume','pitch'].forEach(bindAutoSave);\n");
        sb.append("document.getElementById('search').addEventListener('input', debounce(() => {\n");
        sb.append("  state.q = document.getElementById('search').value.trim();\n");
        sb.append("  state.page = 1;\n");
        sb.append("  loadUsers();\n");
        sb.append("}, 250));\n");
        sb.append("document.getElementById('size').addEventListener('change', () => {\n");
        sb.append("  state.size = Number(document.getElementById('size').value || 50);\n");
        sb.append("  state.page = 1;\n");
        sb.append("  loadUsers();\n");
        sb.append("});\n");
        sb.append("document.getElementById('prev').addEventListener('click', () => {\n");
        sb.append("  if(state.page > 1){ state.page -= 1; loadUsers(); }\n");
        sb.append("});\n");
        sb.append("document.getElementById('next').addEventListener('click', () => {\n");
        sb.append("  state.page += 1; loadUsers();\n");
        sb.append("});\n");
        sb.append("loadConfig().then(loadUsers);\n");
        sb.append("setInterval(loadUsers, 5000);\n");
        sb.append("</script>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    private String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
