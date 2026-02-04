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
            String json = buildUsersJson();
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
        boolean encryption = plugin.isEncryptionEnabled();
        String sound = plugin.getConfig().getString("sound.success", "ENTITY_PLAYER_LEVELUP");
        double volume = plugin.getConfig().getDouble("sound.volume", 1.0);
        double pitch = plugin.getConfig().getDouble("sound.pitch", 1.0);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"enabled\":").append(enabled).append(',');
        sb.append("\"chat\":").append(chat).append(',');
        sb.append("\"hide\":").append(hide).append(',');
        sb.append("\"encryption\":").append(encryption).append(',');
        sb.append("\"sound\":\"").append(escapeJson(sound)).append("\",");
        sb.append("\"volume\":").append(volume).append(',');
        sb.append("\"pitch\":").append(pitch);
        sb.append("}");
        return sb.toString();
    }

    private String buildUsersJson() {
        boolean encryption = plugin.isEncryptionEnabled();
        List<String> entries = new ArrayList<>();
        for (Map.Entry<UUID, UserRecord> entry : userStore.getUsersSnapshot().entrySet()) {
            UserRecord record = entry.getValue();
            UserStore.PendingItem pending = userStore.getPendingItem(entry.getKey());
            UserStore.PendingLog log = userStore.getPendingLog(entry.getKey());
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"uuid\":\"").append(record.uuid()).append("\",");
            sb.append("\"mcName\":\"").append(escapeJson(nullToEmpty(record.minecraftName()))).append("\",");
            sb.append("\"account\":\"").append(escapeJson(nullToEmpty(record.account()))).append("\",");
            sb.append("\"password\":\"").append(escapeJson(nullToEmpty(record.password()))).append("\",");
            sb.append("\"salt\":\"").append(escapeJson(nullToEmpty(record.salt()))).append("\",");
            sb.append("\"registerAt\":").append(record.registeredAt()).append(',');
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
            if (log != null && log.stored() != null) {
                sb.append("\"pendingStoredAt\":").append(log.stored().time()).append(',');
            } else {
                sb.append("\"pendingStoredAt\":0,");
            }
            if (log != null && log.restored() != null) {
                sb.append("\"pendingRestoredAt\":").append(log.restored().time());
            } else {
                sb.append("\"pendingRestoredAt\":0");
            }
            sb.append("}");
            entries.add(sb.toString());
        }
        StringBuilder root = new StringBuilder();
        root.append("{");
        root.append("\"mode\":\"").append(encryption ? "HASHED" : "PLAINTEXT").append("\",");
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
        String colPendingLog = text("column-pending-log", "Pending Log");
        String settingEnabled = text("setting-enabled", "Verification");
        String settingEncryption = text("setting-encryption", "Encryption");
        String settingChat = text("setting-chat", "Chat Before Verify");
        String settingHide = text("setting-hide", "Hide Unverified");
        String settingSound = text("setting-sound", "Sound");
        String settingVolume = text("setting-volume", "volume / pitch");
        String settingSave = text("setting-save", "Save");
        String statusOn = text("status-on", "ON");
        String statusOff = text("status-off", "OFF");
        String pendingStoredLabel = text("pending-stored-label", "stored");
        String pendingRestoredLabel = text("pending-restored-label", "restored");
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
        sb.append("    <button class=\"minecraft-button\" id=\"save\">").append(escapeHtml(settingSave)).append("</button>\n");
        sb.append("  </div>\n\n");
        sb.append("  <div class=\"panel\">\n");
        sb.append("    <h3>").append(escapeHtml(sectionUsers)).append("</h3>\n");
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
        sb.append("          <th>").append(escapeHtml(colPendingLog)).append("</th>\n");
        sb.append("        </tr>\n");
        sb.append("      </thead>\n");
        sb.append("      <tbody></tbody>\n");
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");
        sb.append("<script>\n");
        sb.append("async function loadConfig(){\n");
        sb.append("  const res = await fetch('/api/config');\n");
        sb.append("  const cfg = await res.json();\n");
        sb.append("  document.getElementById('enabled').value = String(cfg.enabled);\n");
        sb.append("  document.getElementById('chat').value = String(cfg.chat);\n");
        sb.append("  document.getElementById('hide').value = String(cfg.hide);\n");
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
        sb.append("  data.set('sound', document.getElementById('sound').value);\n");
        sb.append("  data.set('volume', document.getElementById('volume').value);\n");
        sb.append("  data.set('pitch', document.getElementById('pitch').value);\n");
        sb.append("  await fetch('/api/config', {method:'POST', body:data});\n");
        sb.append("  await loadUsers();\n");
        sb.append("}\n\n");
        sb.append("function fmtTime(ts){\n");
        sb.append("  if(!ts || ts<=0) return '-';\n");
        sb.append("  const d = new Date(ts);\n");
        sb.append("  return d.toLocaleString();\n");
        sb.append("}\n\n");
        sb.append("async function loadUsers(){\n");
        sb.append("  const res = await fetch('/api/users');\n");
        sb.append("  const data = await res.json();\n");
        sb.append("  const tbody = document.querySelector('#users tbody');\n");
        sb.append("  tbody.innerHTML = '';\n");
        sb.append("  for(const u of data.users){\n");
        sb.append("    const tr = document.createElement('tr');\n");
        sb.append("    const avatar = 'https://crafatar.com/avatars/' + u.uuid + '?size=32&overlay';\n");
        sb.append("    tr.innerHTML =\n");
        sb.append("      '<td>' + u.uuid + '</td>' +\n");
        sb.append("      '<td><img class=\\\"avatar\\\" src=\\\"' + avatar + '\\\"/></td>' +\n");
        sb.append("      '<td>' + (u.mcName || '-') + '</td>' +\n");
        sb.append("      '<td>' + (u.account || '-') + '</td>' +\n");
        sb.append("      '<td>' + (u.password || '-') + '</td>' +\n");
        sb.append("      '<td>' + (u.salt || '-') + '</td>' +\n");
        sb.append("      '<td>' + fmtTime(u.registerAt) + '</td>' +\n");
        sb.append("      '<td>' + (u.lastLoginIp || '-') + '<br>' + fmtTime(u.lastLoginAt) + '<br>' + (u.lastLoginSalt || '-') + '</td>' +\n");
        sb.append("      '<td>' + (u.pendingType ? (u.pendingType + ' x' + u.pendingAmount + ' @' + u.pendingSlot) : '-') + '</td>' +\n");
        sb.append("      '<td>" + escapeHtml(pendingStoredLabel) + ": ' + fmtTime(u.pendingStoredAt) + '<br>" + escapeHtml(pendingRestoredLabel) + ": ' + fmtTime(u.pendingRestoredAt) + '</td>';\n");
        sb.append("    tbody.appendChild(tr);\n");
        sb.append("  }\n");
        sb.append("}\n\n");
        sb.append("document.getElementById('save').addEventListener('click', saveConfig);\n");
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
