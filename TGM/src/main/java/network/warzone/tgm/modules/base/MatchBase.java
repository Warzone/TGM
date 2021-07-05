package network.warzone.tgm.modules.base;

import network.warzone.tgm.TGM;
import network.warzone.tgm.match.Match;
import network.warzone.tgm.match.MatchStatus;
import network.warzone.tgm.modules.region.Region;
import network.warzone.tgm.modules.respawn.RespawnModule;
import network.warzone.tgm.modules.team.MatchTeam;
import network.warzone.tgm.modules.team.TeamManagerModule;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A base represents an area where a Redeemable can be redeemed
 * Created by yikes on 12/15/2019
 */
public class MatchBase implements Listener {
    private Region baseRegion;
    private MatchTeam team;
    private Match match;
    private TeamManagerModule teamManagerModule;
    private List<PlayerRedeemable> playerRedeemables = new ArrayList<>();
    private List<ItemRedeemable> itemRedeemables = new ArrayList<>();

    private RespawnModule respawnModule;

    public MatchBase(Region baseRegion, MatchTeam team, List<? extends Redeemable> redeemables) {
        this.baseRegion = baseRegion;
        this.team = team;
        this.match = TGM.get().getMatchManager().getMatch();
        this.teamManagerModule = TGM.get().getModule(TeamManagerModule.class);
        for (Redeemable redeemable : redeemables) {
            if (redeemable instanceof PlayerRedeemable) {
                playerRedeemables.add((PlayerRedeemable) redeemable);
            } else if (redeemable instanceof ItemRedeemable) {
                itemRedeemables.add((ItemRedeemable) redeemable);
            }
        }
        this.respawnModule = TGM.get().getModule(RespawnModule.class);
        TGM.registerEvents(this);
    }

    // Filters are wack. Do not allow build or break in the base region
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!baseRegion.contains(event.getBlock())) return;
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot break near a capture base!");
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!baseRegion.contains(event.getBlock())) return;
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot build near a capture base!");
        event.setCancelled(true);
    }


    private boolean isOnBaseTeam(Player player) {
        return teamManagerModule.getTeam(player).equals(team);
    }

    private List<PlayerRedeemable> hasPlayerRedeemables(Player player) {
        List<PlayerRedeemable> eligibleRedeemables = new ArrayList<>();
        for (PlayerRedeemable playerRedeemable : playerRedeemables) {
            if (playerRedeemable.hasRedeemable(player)) eligibleRedeemables.add(playerRedeemable);
        }
        return eligibleRedeemables;
    }

    private List<ItemRedeemable> applicableItemRedeemables(ItemStack item) {
        List<ItemRedeemable> eligibleRedeemables = new ArrayList<>();
        for (ItemRedeemable itemRedeemable : itemRedeemables) {
            if (itemRedeemable.matchesRedeemable(item)) eligibleRedeemables.add(itemRedeemable);
        }
        return eligibleRedeemables;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (match.getMatchStatus() != MatchStatus.MID ||
            (playerRedeemables == null && itemRedeemables == null) ||
            (playerRedeemables.size() == 0 && itemRedeemables.size() == 0) ||
            respawnModule.isDead(event.getPlayer()) ||
            !isOnBaseTeam(event.getPlayer()) ||
            !baseRegion.contains(event.getFrom())) return;
        else {
            List<PlayerRedeemable> eligiblePlayerRedeemables = hasPlayerRedeemables(event.getPlayer());
            List<ItemRedeemable> eligibleItemRedeemables = applicableItemRedeemables(event.getPlayer().getActiveItem());
            if (eligiblePlayerRedeemables.size() == 0 && eligibleItemRedeemables.size() == 0) return;
            redeemPlayerRedeemables(eligiblePlayerRedeemables, event.getPlayer());
            redeemItemRedeemables(eligibleItemRedeemables, event.getPlayer());
        }
    }

    private void redeemPlayerRedeemables(List<PlayerRedeemable> filteredPlayerRedeemables, Player player) {
        for (PlayerRedeemable playerRedeemable : filteredPlayerRedeemables) playerRedeemable.redeem(player);
    }

    private void redeemItemRedeemables(List<ItemRedeemable> filteredItemRedeemables, Player player) {
        for (ItemRedeemable itemRedeemable : filteredItemRedeemables) itemRedeemable.redeem(player);
    }

    public void unload() {
        playerRedeemables = null;
        itemRedeemables = null;
        TGM.unregisterEvents(this);
    }
}
