package com.minehut.tgm.modules.countdown;

import com.minehut.tgm.TGM;
import com.minehut.tgm.match.Match;
import com.minehut.tgm.modules.team.TeamManagerModule;
import com.sk89q.minecraft.util.commands.ChatColor;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public class StartCountdown extends BossBarCountdown {
    public static final int START_TIME = 20;
    public static final int REQUIRED_PLAYERS = 2;

    @Getter private TeamManagerModule teamManagerModule;

    @Override
    public void load(Match match) {
        teamManagerModule = match.getModule(TeamManagerModule.class);
        start(START_TIME);
    }

    @Override
    public BossBar initBossBar() {
        BossBar bossBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
        bossBar.setVisible(false);
        return bossBar;
    }

    @Override
    protected void onStart() {
        getBossBar().setVisible(true);
    }

    @Override
    protected void onTick() {
        if(isCancelled()) return;

        int amountParticipating = teamManagerModule.getAmountParticipating();
        if (amountParticipating < REQUIRED_PLAYERS) {
            int needed = REQUIRED_PLAYERS - amountParticipating;
            getBossBar().setProgress(1);
            getBossBar().setTitle(ChatColor.RED + "Waiting for " + ChatColor.AQUA + needed +
                    ChatColor.RED + " more player" + (needed == 1 ? "" : "s") + " to join");
            getBossBar().setColor(BarColor.RED);

            setTimeLeft(getTimeMax());
            return;
        }

        getBossBar().setProgress((getTimeMax() - getTimeLeft()) / getTimeMax());

        if (getTimeLeft() % 20 == 0) {
            getBossBar().setColor(BarColor.GREEN);
            getBossBar().setTitle(ChatColor.GREEN + "Match starting in " + ChatColor.DARK_RED + getTimeLeftSeconds() +
                    ChatColor.GREEN + " second" + (getTimeLeftSeconds() > 1 ? "s" : ""));

            if (getTimeLeftSeconds() <= 3) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_PLING, 1, 1);
                    if (!TGM.get().getModule(TeamManagerModule.class).getSpectators().containsPlayer(player)) {
                        player.sendTitle(ChatColor.YELLOW.toString() + getTimeLeftSeconds(), "", 0, 5, 15);
                    }
                }
            }
        }
    }

    @Override
    protected void onFinish() {
        getBossBar().setVisible(false);
        TGM.get().getMatchManager().startMatch();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_PLING, 1, 2);
            if (!TGM.get().getModule(TeamManagerModule.class).getSpectators().containsPlayer(player)) {
                player.sendTitle(ChatColor.GREEN + "GO!", "", 0, 5, 15);
            }
        }
    }

    @Override
    protected void onCancel() {
        getBossBar().setVisible(false);
    }
}
