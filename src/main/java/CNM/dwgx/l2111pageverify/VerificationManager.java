package CNM.dwgx.l2111pageverify;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;

public final class VerificationManager {

    public enum SessionType {
        LOGIN,
        REGISTER
    }

    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SessionType> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> tokens = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bookSlots = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> storedItems = new ConcurrentHashMap<>();
    private final Map<UUID, String> notices = new ConcurrentHashMap<>();
    private final Map<UUID, NoticeType> noticeTypes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOpenTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> prevAllowFlight = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> prevFlying = new ConcurrentHashMap<>();

    public boolean isVerified(UUID uuid) {
        return verified.contains(uuid);
    }

    public void markVerified(UUID uuid) {
        verified.add(uuid);
        sessions.remove(uuid);
    }

    public void markUnverified(UUID uuid) {
        verified.remove(uuid);
    }

    public void clear(UUID uuid) {
        verified.remove(uuid);
        sessions.remove(uuid);
        tokens.remove(uuid);
        bookSlots.remove(uuid);
        storedItems.remove(uuid);
        notices.remove(uuid);
        noticeTypes.remove(uuid);
        lastOpenTimes.remove(uuid);
        prevAllowFlight.remove(uuid);
        prevFlying.remove(uuid);
    }

    public void setSession(UUID uuid, SessionType type) {
        sessions.put(uuid, type);
    }

    public SessionType getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public String issueToken(UUID uuid) {
        String token = UUID.randomUUID().toString();
        tokens.put(uuid, token);
        return token;
    }

    public String getToken(UUID uuid) {
        return tokens.get(uuid);
    }

    public void setBookSlot(UUID uuid, int slot) {
        bookSlots.put(uuid, slot);
    }

    public Integer getBookSlot(UUID uuid) {
        return bookSlots.get(uuid);
    }

    public void storeReplacedItem(UUID uuid, ItemStack item) {
        if (item == null) {
            return;
        }
        storedItems.put(uuid, item.clone());
    }

    public ItemStack popReplacedItem(UUID uuid) {
        return storedItems.remove(uuid);
    }

    public void setNotice(UUID uuid, String notice, NoticeType type) {
        if (notice == null) {
            notices.remove(uuid);
            noticeTypes.remove(uuid);
            return;
        }
        notices.put(uuid, notice);
        noticeTypes.put(uuid, type == null ? NoticeType.INFO : type);
    }

    public String getNotice(UUID uuid) {
        return notices.get(uuid);
    }

    public NoticeType getNoticeType(UUID uuid) {
        return noticeTypes.getOrDefault(uuid, NoticeType.INFO);
    }

    public void clearNotice(UUID uuid) {
        notices.remove(uuid);
        noticeTypes.remove(uuid);
    }

    public long getLastOpenTime(UUID uuid) {
        return lastOpenTimes.getOrDefault(uuid, 0L);
    }

    public void setLastOpenTime(UUID uuid, long timestamp) {
        lastOpenTimes.put(uuid, timestamp);
    }

    public void snapshotFlight(UUID uuid, boolean allowFlight, boolean isFlying) {
        prevAllowFlight.put(uuid, allowFlight);
        prevFlying.put(uuid, isFlying);
    }

    public Boolean getPrevAllowFlight(UUID uuid) {
        return prevAllowFlight.get(uuid);
    }

    public Boolean getPrevFlying(UUID uuid) {
        return prevFlying.get(uuid);
    }
}

enum NoticeType {
    INFO,
    ERROR
}
