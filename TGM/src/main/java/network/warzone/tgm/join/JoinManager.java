package network.warzone.tgm.join;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import network.warzone.tgm.TGM;
import network.warzone.tgm.match.MatchPostLoadEvent;
import network.warzone.tgm.nickname.NickManager;
import network.warzone.tgm.user.PlayerContext;
import network.warzone.tgm.util.HashMaps;
import network.warzone.tgm.util.Ranks;
import network.warzone.warzoneapi.models.PlayerLogin;
import network.warzone.warzoneapi.models.Punishment;
import network.warzone.warzoneapi.models.UserProfile;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldInitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Created by luke on 4/27/17.
 */
@Getter
public class JoinManager implements Listener {

    private Collection<QueuedJoin> queuedJoins = new ConcurrentLinkedQueue<>();
    private Set<LoginService> loginServices = new HashSet<>();
 
    public JoinManager() {
        TGM.registerEvents(this);

        //empty queued joins when the connection didn't follow through for an unknown reason.
        Bukkit.getScheduler().runTaskTimerAsynchronously(TGM.get(), () -> {
            queuedJoins.removeIf(queuedJoin -> System.currentTimeMillis() - queuedJoin.getTime() > 10 * 1000);
        }, 20 * 10L, 20 * 10L);
    }

    /**
     * Allow custom services to hook into the login event.
     * This can be used with an external tournament plugin.
     */
    public void addLoginService(LoginService loginService) {
        getLoginServices().add(loginService);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        UserProfile userProfile = TGM.get().getTeamClient().login(new PlayerLogin(event.getName(), uuid.toString(), event.getAddress().getHostAddress()));

        Bukkit.getLogger().info(userProfile.getName() + " " + userProfile.getId().toString() + " | ranks: " + userProfile.getRanksLoaded().size() + "/" + userProfile.getRanks().size() + " (loaded/total)");

        //TODO Custom ban messages
        Punishment punishment = userProfile.getLatestBan();
        if (punishment != null) {
            event.setKickMessage(ChatColor.RED + "You have been banned from the server. Reason:\n"
                    + ChatColor.RESET + ChatColor.translateAlternateColorCodes('&', punishment.getReason()) + "\n\n"
                    + ChatColor.RED + "Ban expires: " + ChatColor.RESET + (punishment.getExpires() >= 0 ? new Date(punishment.getExpires()).toString() : "Never") + "\n"
                    + ChatColor.AQUA + "Appeal at https://discord.io/WarzoneMC\n"
                    + ChatColor.GRAY + "ID: " + punishment.getId().toString()
            );
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            return;
        }
        //Bukkit.getLogger().info(userProfile.getName() + " " + userProfile.getId().toString()); //Already logged above

        queuedJoins.add(new QueuedJoin(uuid, userProfile, System.currentTimeMillis()));
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        QueuedJoin queuedJoin = getQueuedUserProfile(uuid);
        if (queuedJoin == null) {
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage(ChatColor.RED + "Unable to load user profile. Please try again.");
            return;
        }

        PlayerContext playerContext = new PlayerContext(event.getPlayer(), queuedJoin.getUserProfile());
        TGM.get().getPlayerManager().addPlayer(playerContext);

        Ranks.createAttachment(event.getPlayer());
        playerContext.getUserProfile(true).getRanksLoaded().forEach(rank -> Ranks.addPermissions(event.getPlayer(), rank.getPermissions()));

        loginServices.forEach(loginService -> loginService.login(playerContext));
        queuedJoins.remove(queuedJoin);
    }

    private QueuedJoin getQueuedUserProfile(UUID uuid) {
        return queuedJoins.stream().filter(queuedJoin -> uuid.equals(queuedJoin.getUuid())).findFirst().orElse(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerContext playerContext = TGM.get().getPlayerManager().getPlayerContext(event.getPlayer());
        Bukkit.getPluginManager().callEvent(new MatchJoinEvent(playerContext));
        String joinMsg;
        if (event.getPlayer().hasPermission("tgm.donator.joinmsg") && !playerContext.getUserProfile().isStaff() && playerContext.getUserProfile().getPrefix() != null){
            String prefix = playerContext.getUserProfile().getPrefix() != null ? ChatColor.translateAlternateColorCodes('&', playerContext.getUserProfile().getPrefix().trim()) + " " : "";
            joinMsg = ChatColor.GOLD + prefix + event.getPlayer().getDisplayName() + ChatColor.GOLD + " joined.";
            Bukkit.getOnlinePlayers().forEach(player -> player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f));
        }
        else joinMsg = ChatColor.GRAY + playerContext.getDisplayName() + " joined.";

        if (playerContext.getUserProfile().isNew()) joinMsg += ChatColor.LIGHT_PURPLE + " [NEW]";
        event.setJoinMessage(joinMsg);

        Player p = playerContext.getPlayer();
        if (TGM.get().getNickManager().nickNames.containsValue(p.getName())) {
            UUID uuid = HashMaps.reverseGetFirst(p.getName(), TGM.get().getNickManager().nickNames);
            if (Bukkit.getOnlinePlayers().stream().map((Function<Player, UUID>) Entity::getUniqueId).anyMatch(uuid1 -> uuid1.equals(uuid))) {
                Player onlinePlayer = Bukkit.getPlayer(uuid);
                if (onlinePlayer != null) {
                    onlinePlayer.sendMessage(ChatColor.YELLOW + p.getName() + ChatColor.RED + " has joined with the same name as you nick, so it must be reset.");
                    TGM.get().getNickManager().reset(onlinePlayer);
                }
            }
        }

        if (playerContext.isNicked()) {
            String nick = TGM.get().getNickManager().nickNames.get(event.getPlayer().getUniqueId());
            if (Bukkit.getOnlinePlayers().stream().map((Player player) -> TGM.get().getPlayerManager().getPlayerContext(player).getOriginalName()).anyMatch(name -> name.equals(nick)) ||
            Bukkit.getOnlinePlayers().stream().filter(player -> !player.getUniqueId().equals(playerContext.getPlayer().getUniqueId())).map((Player player) -> TGM.get().getPlayerManager().getPlayerContext(player).getDisplayName()).anyMatch(name -> name.equals(nick))) {
                playerContext.getPlayer().sendMessage(ChatColor.YELLOW + nick + ChatColor.RED + " is already on, so your nickname must be reset.");
                TGM.get().getNickManager().reset(playerContext.getPlayer());
            }
            else {
                NickManager.Skin skin = TGM.get().getNickManager().skins.getOrDefault(event.getPlayer().getUniqueId(), null);
                if (skin != null) {
                    TGM.get().getNickManager().setSkin(event.getPlayer(), skin);
                }
                TGM.get().getNickManager().setName(event.getPlayer(), nick);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCycle(MatchPostLoadEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerContext playerContext = TGM.get().getPlayerManager().getPlayerContext(player);
            Bukkit.getPluginManager().callEvent(new MatchJoinEvent(playerContext));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldInit(WorldInitEvent e) {
        e.getWorld().setKeepSpawnInMemory(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(ChatColor.GRAY + event.getPlayer().getName() + " left.");
        handleQuit(event.getPlayer());
    }

    private void handleQuit(Player player) {
        TGM.get().getPlayerManager().removePlayer(TGM.get().getPlayerManager().getPlayerContext(player));
        Ranks.removeAttachment(player);
    }

    @AllArgsConstructor @Getter
    private class QueuedJoin {
        private UUID uuid;
        private UserProfile userProfile;
        private long time;
    }

}
