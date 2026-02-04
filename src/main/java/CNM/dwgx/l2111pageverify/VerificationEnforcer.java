package CNM.dwgx.l2111pageverify;

import CNM.dwgx.l2111pageverify.NoticeType;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class VerificationEnforcer implements Runnable {

    private final L2111pageloginverify plugin;
    private final UserStore userStore;
    private final VerificationManager verificationManager;
    private final VerificationBookService bookService;

    public VerificationEnforcer(L2111pageloginverify plugin,
                                UserStore userStore,
                                VerificationManager verificationManager,
                                VerificationBookService bookService) {
        this.plugin = plugin;
        this.userStore = userStore;
        this.verificationManager = verificationManager;
        this.bookService = bookService;
    }

    @Override
    public void run() {
        if (!plugin.isInitialized() || !plugin.isVerificationEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (verificationManager.isVerified(uuid)) {
                userStore.tryRestorePendingItem(player, false);
                continue;
            }
            VerificationManager.SessionType session = verificationManager.getSession(uuid);
            if (session == null) {
                session = userStore.hasUser(uuid)
                        ? VerificationManager.SessionType.LOGIN
                        : VerificationManager.SessionType.REGISTER;
                verificationManager.setSession(uuid, session);
            }

            if (!bookService.hasValidBook(player)) {
                if (verificationManager.getNotice(uuid) == null) {
                    verificationManager.setNotice(uuid,
                            session == VerificationManager.SessionType.LOGIN
                                    ? plugin.message("open-login-book")
                                    : plugin.message("open-register-book"),
                            NoticeType.INFO);
                }
                bookService.giveBook(player, session, verificationManager.getNotice(uuid),
                        verificationManager.getNoticeType(uuid));
                continue;
            }

            Integer slot = verificationManager.getBookSlot(uuid);
            if (slot != null && player.getInventory().getHeldItemSlot() != slot) {
                player.getInventory().setHeldItemSlot(slot);
            }

            boolean hard = plugin.getConfig().getBoolean("hard-force-open", false);
            if (hard) {
                long lastOpen = verificationManager.getLastOpenTime(uuid);
                if (System.currentTimeMillis() - lastOpen >= 1200L) {
                    bookService.forceOpen(player);
                }
            }
        }
    }
}
