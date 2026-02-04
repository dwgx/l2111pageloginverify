package CNM.dwgx.l2111pageverify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;

public final class NmsBookOpener {

    private static final AtomicBoolean WARNED = new AtomicBoolean(false);
    private static Method sendMethod;
    private static Field connectionField;

    private NmsBookOpener() {
    }

    public static boolean openBook(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = getConnection(handle);
            Object packet = createOpenBookPacket();
            Method send = getSendMethod(connection.getClass());
            send.invoke(connection, packet);
            return true;
        } catch (Exception ex) {
            if (WARNED.compareAndSet(false, true)) {
                player.getServer().getLogger().warning("Failed to open book via packet: " + ex.getMessage());
            }
            return false;
        }
    }

    private static Object getConnection(Object handle) throws Exception {
        if (connectionField == null) {
            try {
                connectionField = handle.getClass().getField("connection");
            } catch (NoSuchFieldException ex) {
                for (Field field : handle.getClass().getDeclaredFields()) {
                    if (field.getType().getName().endsWith("ServerGamePacketListenerImpl")) {
                        connectionField = field;
                        connectionField.setAccessible(true);
                        break;
                    }
                }
            }
            if (connectionField == null) {
                throw new NoSuchFieldException("connection field not found");
            }
        }
        return connectionField.get(handle);
    }

    private static Object createOpenBookPacket() throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundOpenBookPacket");
        Class<?> handClass = Class.forName("net.minecraft.world.InteractionHand");
        Object hand = Enum.valueOf(handClass.asSubclass(Enum.class), "MAIN_HAND");
        return packetClass.getConstructor(handClass).newInstance(hand);
    }

    private static Method getSendMethod(Class<?> connectionClass) throws Exception {
        if (sendMethod != null) {
            return sendMethod;
        }
        Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
        for (Method method : connectionClass.getMethods()) {
            if (!method.getName().equals("send")) {
                continue;
            }
            if (method.getParameterCount() == 1 && packetInterface.isAssignableFrom(method.getParameterTypes()[0])) {
                sendMethod = method;
                return sendMethod;
            }
        }
        throw new NoSuchMethodException("send(Packet) not found");
    }
}
