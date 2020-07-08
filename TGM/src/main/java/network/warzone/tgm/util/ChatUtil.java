package network.warzone.tgm.util;

import java.util.Collection;

import org.bukkit.craftbukkit.v1_16_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.minecraft.server.v1_16_R1.ChatMessageType;
import net.minecraft.server.v1_16_R1.IChatBaseComponent;
import net.minecraft.server.v1_16_R1.PacketPlayOutChat;
import net.minecraft.server.v1_16_R1.SystemUtils;

public class ChatUtil {
    public static void sendChatComponents(Collection<Player> players, IChatBaseComponent[] components) {
        for (Player player : players) sendChatComponents(player, components);
    }

    public static void sendChatComponents(Player player, IChatBaseComponent[] components) {
        CraftPlayer obcPlayer = (CraftPlayer) player;
        for (IChatBaseComponent component : components) {
            obcPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutChat(
                component,
                ChatMessageType.CHAT,
                SystemUtils.b
            ));
        }
    }

}
