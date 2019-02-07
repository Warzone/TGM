package network.warzone.tgm.api;

import lombok.Getter;
import network.warzone.tgm.TGM;
import network.warzone.tgm.map.MapInfo;
import network.warzone.tgm.map.ParsedTeam;
import network.warzone.tgm.match.MatchLoadEvent;
import network.warzone.tgm.match.MatchResultEvent;
import network.warzone.tgm.modules.ChatModule;
import network.warzone.tgm.modules.DeathModule;
import network.warzone.tgm.modules.StatsModule;
import network.warzone.tgm.modules.team.MatchTeam;
import network.warzone.tgm.modules.team.TeamManagerModule;
import network.warzone.tgm.player.event.PlayerXPEvent;
import network.warzone.tgm.player.event.TGMPlayerDeathEvent;
import network.warzone.tgm.user.PlayerContext;
import network.warzone.warzoneapi.client.http.HttpClient;
import network.warzone.warzoneapi.models.*;
import org.bson.types.ObjectId;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static network.warzone.warzoneapi.models.UserProfile.*;

@Getter
public class ApiManager implements Listener {

    private ObjectId serverId;
    private MatchInProgress matchInProgress;

    private DeathModule deathModule;

    public ApiManager() {
        this.serverId = new ObjectId();
        TGM.registerEvents(this);

        Set<String> players = new HashSet<>();
        Set<String> playerNames = new HashSet<>();

        if (TGM.get().getTeamClient() instanceof HttpClient) Bukkit.getScheduler().runTaskTimerAsynchronously(TGM.get(), () -> {
            players.clear();
            playerNames.clear();

            for (PlayerContext playerContext : TGM.get().getPlayerManager().getPlayers()) {
                try {
                    players.add(playerContext.getUserProfile().getId().toString());
                    playerNames.add(playerContext.getUserProfile().getName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            int maxPlayers = Bukkit.getMaxPlayers();
            int spectatorCount = 0;
            int playerCount = Bukkit.getOnlinePlayers().size();
            for (MatchTeam matchTeam : TGM.get().getModule(TeamManagerModule.class).getTeams()) {
                if (matchTeam.isSpectator()) {
                    spectatorCount += matchTeam.getMembers().size();
                }
            }
            Heartbeat heartbeat = new Heartbeat(serverId,
                    TGM.get().getConfig().getString("server.name"),
                    TGM.get().getConfig().getString("server.id"),
                    players,
                    playerNames,
                    playerCount,
                    spectatorCount,
                    maxPlayers,
                    TGM.get().getMatchManager().getMatch().getMapContainer().getMapInfo().getName(),
                    TGM.get().getMatchManager().getMatch().getMapContainer().getMapInfo().getGametype().getName()
            );
            TGM.get().getTeamClient().heartbeat(heartbeat);
        }, 40L, 20L);
    }

    @EventHandler
    public void onMatchResult(MatchResultEvent event, Player player) {
        if (isStatsDisabled()) return;

        List<String> winners = new ArrayList<>();
        if (event.getWinningTeam() != null) {
            for (PlayerContext playerContext : event.getWinningTeam().getMembers()) {
                winners.add(playerContext.getUserProfile().getId().toString());
                playerContext.getUserProfile().addWin();
                Bukkit.getPluginManager().callEvent(new PlayerXPEvent(playerContext, XP_PER_WIN, playerContext.getUserProfile().getXP() - XP_PER_WIN, playerContext.getUserProfile().getXP()));
                player.getPlayer().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1000, 2);

            }
        }

        List<String> losers = new ArrayList<>();
        for (MatchTeam matchTeam : event.getLosingTeams()) {
            for (PlayerContext playerContext : matchTeam.getMembers()) {
                losers.add(playerContext.getUserProfile().getId().toString());
                playerContext.getUserProfile().addLoss();
                Bukkit.getPluginManager().callEvent(new PlayerXPEvent(playerContext, XP_PER_LOSS, playerContext.getUserProfile().getXP() - XP_PER_LOSS, playerContext.getUserProfile().getXP()));
                player.getPlayer().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1000, 2);
            }
        }

        TeamManagerModule teamManagerModule = TGM.get().getModule(TeamManagerModule.class);
        List<TeamMapping> teamMappings = new ArrayList<>();
        for (MatchTeam matchTeam : teamManagerModule.getTeams()) {
            if (matchTeam.isSpectator()) continue;

            for (PlayerContext playerContext : matchTeam.getMembers()) {
                teamMappings.add(new TeamMapping(matchTeam.getId(), playerContext.getUserProfile().getId().toString()));
            }
        }

        MatchFinishPacket matchFinishPacket = new MatchFinishPacket(
                matchInProgress.getId(),
                matchInProgress.getMap(),
                event.getMatch().getStartedTime(),
                event.getMatch().getFinishedTime(),
                TGM.get().getModule(ChatModule.class).getChatLog(),
                winners,
                losers,
                event.getWinningTeam() != null ? event.getWinningTeam().getId() : null,
                teamMappings);
        TGM.get().getTeamClient().finishMatch(matchFinishPacket);

        winners.clear();
        losers.clear();
        teamMappings.clear();
    }

    @EventHandler
    public void onMatchLoad(MatchLoadEvent event) {
        if (isStatsDisabled()) return;
        deathModule = event.getMatch().getModule(DeathModule.class);

        MapInfo mapInfo = event.getMatch().getMapContainer().getMapInfo();
        List<Team> teams = new ArrayList<>();
        for (ParsedTeam parsedTeam : mapInfo.getTeams()) {
            teams.add(new Team(parsedTeam.getId(), parsedTeam.getAlias(), parsedTeam.getTeamColor().name(), parsedTeam.getMin(), parsedTeam.getMax()));
        }

        Bukkit.getScheduler().runTaskAsynchronously(TGM.get(), () -> {
            MapLoadResponse mapLoadResponse = TGM.get().getTeamClient().loadmap(new Map(mapInfo.getName(), mapInfo.getVersion(), mapInfo.getAuthors(), mapInfo.getGametype().toString(), teams));
            Bukkit.getLogger().info("Received load map response. Id: " + mapLoadResponse.getMap() + " [" + mapLoadResponse.isInserted() + "]");
            matchInProgress = TGM.get().getTeamClient().loadMatch(new MatchLoadRequest(mapLoadResponse.getMap()));
            Bukkit.getLogger().info("Match successfully loaded [" + matchInProgress.getMap() + "]");
        });

        teams.clear();
    }

    @EventHandler
    public void onKill(TGMPlayerDeathEvent event, Player player) {
        if (isStatsDisabled()) return;
        DeathModule module = deathModule.getPlayer(event.getVictim());

        PlayerContext killed = TGM.get().getPlayerManager().getPlayerContext(module.getPlayer());

        killed.getUserProfile().addDeath();

        String playerItem = module.getPlayer().getInventory().getItemInMainHand() == null ? "" : module.getPlayer().getInventory().getItemInMainHand().getType().toString();
        String killerItem = module.getItem() == null ? "" : module.getItem().getType().toString();
        String killerId = null;

        if (module.getKiller() != null) {
            PlayerContext context = TGM.get().getPlayerManager().getPlayerContext(module.getKiller());
            if (context == null) return;
            context.getUserProfile().addKill();
            Bukkit.getPluginManager().callEvent(new PlayerXPEvent(context, XP_PER_KILL, context.getUserProfile().getXP() - XP_PER_KILL, context.getUserProfile().getXP()));

            player.getPlayer().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1000, 2);

            killerId = context.getUserProfile().getId().toString();
        }

        Death death = new Death(killed.getUserProfile().getId().toString(), killerId, playerItem,
                killerItem, matchInProgress.getMap(), matchInProgress.getId());

        Bukkit.getScheduler().runTaskAsynchronously(TGM.get(), () -> TGM.get().getTeamClient().addKill(death));
    }

    public boolean isStatsDisabled() {
        return !TGM.get().getConfig().getBoolean("api.stats.enabled") || TGM.get().getModule(StatsModule.class).isStatsDisabled();
    }
}
