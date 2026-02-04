package CNM.dwgx.l2111pageverify;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Protocol-level enforcement to keep the verification book focused.
 * - Cancels CLOSE_WINDOW so the book cannot be closed.
 * - Cancels HELD_ITEM_SLOT to prevent hotbar switching.
 * - Reopens the book on any attempted close.
 */
public final class ProtocolBookEnforcer extends PacketAdapter {

    private final L2111pageloginverify plugin;
    private final VerificationManager verificationManager;
    private final VerificationBookService bookService;

    public ProtocolBookEnforcer(L2111pageloginverify plugin,
                                VerificationManager verificationManager,
                                VerificationBookService bookService) {
        super(plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Client.CLOSE_WINDOW,
                PacketType.Play.Client.HELD_ITEM_SLOT);
        this.plugin = plugin;
        this.verificationManager = verificationManager;
        this.bookService = bookService;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (verificationManager.isVerified(uuid) || !plugin.isVerificationEnabled()) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            event.setCancelled(true);
            bookService.forceOpen(player);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_SLOT) {
            Integer slot = verificationManager.getBookSlot(uuid);
            if (slot != null && player.getInventory().getHeldItemSlot() != slot) {
                event.setCancelled(true);
                bookService.forceOpen(player);
            }
        }
    }

    public static ProtocolBookEnforcer register(L2111pageloginverify plugin,
                                                VerificationManager verificationManager,
                                                VerificationBookService bookService) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        ProtocolBookEnforcer listener = new ProtocolBookEnforcer(plugin, verificationManager, bookService);
        manager.addPacketListener(listener);
        return listener;
    }

    public static void unregister(ProtocolBookEnforcer listener) {
        if (listener == null) {
            return;
        }
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.removePacketListener(listener);
    }
}
