package network.warzone.tgm.modules;

import lombok.Getter;
import network.warzone.tgm.TGM;
import network.warzone.tgm.map.SpawnPoint;
import network.warzone.tgm.match.Match;
import network.warzone.tgm.match.MatchModule;
import network.warzone.tgm.match.MatchStatus;
import network.warzone.tgm.modules.team.MatchTeam;
import network.warzone.tgm.modules.team.TeamChangeEvent;
import network.warzone.tgm.modules.team.TeamManagerModule;
import network.warzone.tgm.user.PlayerContext;
import network.warzone.tgm.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class SpawnPointHandlerModule extends MatchModule implements Listener {
    @Getter private TeamManagerModule teamManagerModule;
    @Getter private SpectatorModule spectatorModule;

    @Override
    public void load(Match match) {
        this.teamManagerModule = match.getModule(TeamManagerModule.class);
        this.spectatorModule = match.getModule(SpectatorModule.class);
    }

    @EventHandler
    public void onTeamChange(TeamChangeEvent event) {
        if (TGM.get().getMatchManager().getMatch().getMatchStatus() == MatchStatus.MID) {
            spawnPlayer(event.getPlayerContext(), event.getTeam(), true);
        }
        //player is joining the server
        else if (event.getOldTeam() == null) {
            spawnPlayer(event.getPlayerContext(), event.getTeam(), true);
        }
        //player is swapping teams pre/post match.
        else {
            //we don't need to teleport them in this case. Let them stay in their position.
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerContext playerContext = TGM.get().getPlayerManager().getPlayerContext(event.getPlayer());
        MatchTeam matchTeam = teamManagerModule.getTeam(event.getPlayer());
        event.setRespawnLocation(getTeamSpawn(matchTeam).getLocation());

        Bukkit.getScheduler().runTaskLater(TGM.get(), new Runnable() {
            @Override
            public void run() {
                spawnPlayer(playerContext, matchTeam, false);
            }
        }, 0L);
    }

    public void spawnPlayer(PlayerContext playerContext, MatchTeam matchTeam, boolean teleport) {
        Players.reset(playerContext.getPlayer(), true);

        Bukkit.getScheduler().runTaskLater(TGM.get(), () -> {
            if (matchTeam.isSpectator()) {
                spectatorModule.applySpectatorKit(playerContext);
                if (teleport) {
                    playerContext.getPlayer().teleport(getTeamSpawn(matchTeam).getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            } else {
                matchTeam.getKits().forEach(kit -> kit.apply(playerContext.getPlayer(), matchTeam));
                playerContext.getPlayer().updateInventory();

                if (teleport) {
                    playerContext.getPlayer().teleport(getTeamSpawn(matchTeam).getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    playerContext.getPlayer().setGameMode(GameMode.SURVIVAL);
                }
            }
        }, 1L);
    }

    @Override
    public void enable() {
        for (MatchTeam matchTeam : TGM.get().getModule(TeamManagerModule.class).getTeams()) {
            if (!matchTeam.isSpectator()) {
                for (PlayerContext player : matchTeam.getMembers()) {
                    spawnPlayer(player, matchTeam, true);
                }
            }
        }
    }

    private SpawnPoint getTeamSpawn(MatchTeam matchTeam) {
        //todo: actually randomize spawn points instead of grabbing first one.
        return matchTeam.getSpawnPoints().get(0);
    }
}
