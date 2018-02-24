package network.warzone.tgm.join;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import network.warzone.tgm.TGM;
import network.warzone.tgm.match.MatchPostLoadEvent;
import network.warzone.tgm.user.PlayerContext;
import network.warzone.tgm.util.Ranks;
import network.warzone.warzoneapi.models.PlayerLogin;
import network.warzone.warzoneapi.models.Punishment;
import network.warzone.warzoneapi.models.UserProfile;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Created by luke on 4/27/17.
 */
@Getter
public class JoinManager implements Listener {

    private List<QueuedJoin> queuedJoins = new ArrayList<>();
    private List<LoginService> loginServices = new ArrayList<>();
 
    public JoinManager() {
        TGM.registerEvents(this);

        //empty queued joins when the connection didn't follow through for an unknown reason.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TGM.get(), () -> {
            List<QueuedJoin> toRemove = new ArrayList<>();
            for (QueuedJoin queuedJoin : queuedJoins) {
                if (System.currentTimeMillis() - queuedJoin.getTime() > 10 * 1000) {
                    toRemove.add(queuedJoin);
                }
            }
            queuedJoins.removeAll(toRemove);
        }, 20 * 10L, 20 * 10L);
    }

    /**
     * Allow custom services to hook into the login event.
     * This can be used with an external tournament plugin.
     */
    public void addLoginService(LoginService loginService) {
        getLoginServices().add(loginService);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED)) return;
        UserProfile userProfile = TGM.get().getTeamClient().login(new PlayerLogin(event.getName(), event.getUniqueId().toString(), event.getAddress().getHostAddress()));

        Bukkit.getLogger().info(userProfile.getName() + " " + userProfile.getId().toString() + " | ranks: " + userProfile.getRanksLoaded().size() + "/" + userProfile.getRanks().size() + " (loaded/total)");

        Punishment punishment;
        if ((punishment = userProfile.getLatestBan()) != null) {
            event.setKickMessage(ChatColor.RED + "You have been banned from the server. Reason:\n"
                    + ChatColor.RESET + ChatColor.translateAlternateColorCodes('&', punishment.getReason()) + "\n\n"
                    + ChatColor.RED + "Ban expires: " + ChatColor.RESET + (punishment.getExpires() >= 0 ? new Date(punishment.getExpires()).toString() : "Never") + "\n"
                    + ChatColor.AQUA + "Appeal at https://discord.io/WarzoneMC\n"
                    + ChatColor.GRAY + "ID: " + punishment.getId().toString()
            );
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
            return;
        }
        Bukkit.getLogger().info(userProfile.getName() + " " + userProfile.getId().toString());

        queuedJoins.add(new QueuedJoin(event.getUniqueId(), userProfile, System.currentTimeMillis()));
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        QueuedJoin queuedJoin = getQueuedUserProfile(event.getPlayer());
        if (queuedJoin == null) {
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage(ChatColor.RED + "Unable to load user profile. Please try again.");
            return;
        }

        PlayerContext playerContext = new PlayerContext(event.getPlayer(), queuedJoin.getUserProfile());
        TGM.get().getPlayerManager().addPlayer(playerContext);

        Ranks.createAttachment(event.getPlayer());
        playerContext.getUserProfile().getRanksLoaded().stream().forEach(rank -> Ranks.addPermissions(event.getPlayer(), rank.getPermissions()));

        for (LoginService loginService : loginServices) {
            loginService.login(playerContext);
        }

        queuedJoins.remove(queuedJoin);
    }

    private QueuedJoin getQueuedUserProfile(Player player) {
        for (QueuedJoin queuedJoin : queuedJoins) {
            if (player.getUniqueId().equals(queuedJoin.getUuid())) {
                return queuedJoin;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerContext playerContext = TGM.get().getPlayerManager().getPlayerContext(event.getPlayer());
        Bukkit.getPluginManager().callEvent(new MatchJoinEvent(playerContext));
        if (event.getPlayer().hasPermission("tgm.donator.joinmsg") && !playerContext.getUserProfile().isStaff()){
            String prefix = playerContext.getUserProfile().getPrefix() != null ? ChatColor.translateAlternateColorCodes('&', playerContext.getUserProfile().getPrefix().trim()) + " " : "";
            event.setJoinMessage(ChatColor.GOLD + prefix + event.getPlayer().getName() + ChatColor.GOLD + " joined.");
            Bukkit.getOnlinePlayers().forEach(player -> player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f));
        }
        else event.setJoinMessage(ChatColor.GRAY + event.getPlayer().getName() + " joined.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCycle(MatchPostLoadEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerContext playerContext = TGM.get().getPlayerManager().getPlayerContext(player);
            Bukkit.getPluginManager().callEvent(new MatchJoinEvent(playerContext));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(ChatColor.GRAY + event.getPlayer().getName() + " left.");
        handleQuit(event.getPlayer());
    }

    public void handleQuit(Player player) {
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
