package com.minehut.tgm.modules.koth;

import com.minehut.tgm.TGM;
import com.minehut.tgm.match.Match;
import com.minehut.tgm.modules.controlpoint.ControlPoint;
import com.minehut.tgm.modules.controlpoint.ControlPointDefinition;
import com.minehut.tgm.modules.controlpoint.ControlPointService;
import com.minehut.tgm.modules.team.MatchTeam;
import com.minehut.tgm.modules.team.TeamManagerModule;
import com.minehut.tgm.user.PlayerContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;

@AllArgsConstructor
public class KOTHControlPointService implements ControlPointService {
    @Getter private KOTHModule kothModule;
    @Getter private Match match;
    @Getter private ControlPointDefinition definition;

    @Override
    public void holding(MatchTeam matchTeam) {
        kothModule.incrementPoints(matchTeam, definition.getPointsPerTick());
    }

    @Override
    public void capturing(MatchTeam matchTeam, int progress, int maxProgress, boolean upward) {
        kothModule.updateScoreboardControlPointLine(definition);
    }

    @Override
    public void captured(MatchTeam matchTeam) {
        Bukkit.broadcastMessage(matchTeam.getColor() + ChatColor.BOLD.toString() + matchTeam.getAlias() + ChatColor.WHITE
                + " took control of " + ChatColor.AQUA + ChatColor.BOLD.toString() + definition.getName());


        kothModule.incrementPoints(matchTeam, definition.getPointsPerTick());
        kothModule.updateScoreboardControlPointLine(definition);


        for (MatchTeam team : match.getModule(TeamManagerModule.class).getTeams()) {
            for (PlayerContext playerContext : team.getMembers()) {
                if (team == matchTeam || team.isSpectator()) {
                    playerContext.getPlayer().playSound(playerContext.getPlayer().getLocation(), Sound.PORTAL_TRAVEL, 0.7f, 2f);
                } else {
                    playerContext.getPlayer().playSound(playerContext.getPlayer().getLocation(), Sound.BLAZE_DEATH, 0.8f, 0.8f);
                }
            }
        }
    }

    @Override
    public void lost(MatchTeam matchTeam) {
        kothModule.updateScoreboardControlPointLine(definition);
        Bukkit.broadcastMessage(matchTeam.getColor() + ChatColor.BOLD.toString() + matchTeam.getAlias() + ChatColor.WHITE
                + " lost control of " + ChatColor.AQUA + ChatColor.BOLD.toString() + definition.getName());
    }
}
