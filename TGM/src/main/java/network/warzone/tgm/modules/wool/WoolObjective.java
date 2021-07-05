package network.warzone.tgm.modules.wool;

import lombok.Getter;
import lombok.Setter;
import network.warzone.tgm.TGM;
import network.warzone.tgm.match.Match;
import network.warzone.tgm.match.MatchStatus;
import network.warzone.tgm.modules.region.Region;
import network.warzone.tgm.modules.team.MatchTeam;
import network.warzone.tgm.modules.team.event.TeamChangeEvent;
import network.warzone.tgm.modules.team.TeamManagerModule;
import network.warzone.tgm.modules.time.TimeModule;
import network.warzone.tgm.player.event.TGMPlayerDeathEvent;
import network.warzone.tgm.util.itemstack.ItemUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Where the players need to place the wool.
 *
 * Should be used as the actual objective.
 */
@Getter
public class WoolObjective implements Listener {

    private final String name;

    private final Material block;
    private final Match match;
    private final MatchTeam owner;
    private final Region podium; //where players place wool to complete objective.
    private final ChatColor color;

    private final HashMap<UUID, Double> touches = new HashMap<>(); //saves match time
    private final List<WoolObjectiveService> services = new ArrayList<>();

    @Setter private boolean completed = false;

    public WoolObjective(String name, Material block, MatchTeam owner, Region podium, ChatColor color) {
        this.name = name;
        this.block = block;
        this.match = TGM.get().getMatchManager().getMatch();
        this.owner = owner;
        this.podium = podium;
        this.color = color;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == block) {
            if (!completed && podium.contains(event.getBlockPlaced()) && owner.containsPlayer(event.getPlayer()))
                event.setCancelled(true);
        } else {
            if (podium.contains(event.getBlockPlaced())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "You may only place " + ChatColor.YELLOW + ItemUtils.materialToString(block) + ChatColor.RED + " in the podium!");
            }
        }
    }

    /*
    Prevents filter messages
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlaceHighest(BlockPlaceEvent event) {
        if (!completed && event.getBlockPlaced().getType() == block) {
            if (!podium.contains(event.getBlockPlaced())) {
                return;
            }
            if (!owner.containsPlayer(event.getPlayer())) {
                return;
            }
            event.setCancelled(false); //override filter
            setCompleted(true);

            TeamManagerModule teamManagerModule = TGM.get().getModule(TeamManagerModule.class);
            MatchTeam matchTeam = teamManagerModule.getTeam(event.getPlayer());

            for (WoolObjectiveService woolObjectiveService : services) {
                woolObjectiveService.place(event.getPlayer(), matchTeam, event.getBlock());
            }
        }
    }


    @EventHandler
    public void onWoolCraft(CraftItemEvent event) {
        if (event.getRecipe().getResult().getType().name().contains("WOOL")) { //TODO 1.13 Temp fix
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.RED + "You are not allowed to craft wool.");
        }
    }

    public void load() {
        TGM.registerEvents(this);
    }

    public void unload() {
        HandlerList.unregisterAll(this);
    }

    private void handleWoolPickup(Player player) {
        if (match.getMatchStatus() != MatchStatus.MID || completed) return;

        if (!owner.containsPlayer(player)) {
            return;
        }

        if (touches.containsKey(player.getUniqueId())) {
            return;
        }
        boolean firstTouch = touches.isEmpty();
        touches.put(player.getUniqueId(), TGM.get().getModule(TimeModule.class).getTimeElapsed());

        TeamManagerModule teamManagerModule = TGM.get().getModule(TeamManagerModule.class);
        MatchTeam matchTeam = teamManagerModule.getTeam(player);

        for (WoolObjectiveService woolObjectiveService : services) {
            woolObjectiveService.pickup(player, matchTeam, firstTouch);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && event.getItem().getItemStack().getType() == block) {
            handleWoolPickup(((Player) event.getEntity()).getPlayer());
        }
    }

    @EventHandler
    public void onCollect(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() == block) {
            handleWoolPickup((Player) event.getWhoClicked());
        }
    }

    private void handleWoolDrop(Player player) {
        handleWoolDrop(player, touches.isEmpty());
    }

    private void handleWoolDrop(Player player, boolean broadcast) {
        if (completed) return;

        if (!owner.containsPlayer(player)) {
            return;
        }

        if (!touches.containsKey(player.getUniqueId())) {
            return;
        }
        touches.remove(player.getUniqueId());

        TeamManagerModule teamManagerModule = TGM.get().getModule(TeamManagerModule.class);
        MatchTeam matchTeam = teamManagerModule.getTeam(player);

        for (WoolObjectiveService woolObjectiveService : services) {
            woolObjectiveService.drop(player, matchTeam, broadcast);
        }
    }

    @EventHandler
    public void onDeath(TGMPlayerDeathEvent event) {
        handleWoolDrop(event.getVictim());
    }

    @EventHandler
    public void onTeamChange(TeamChangeEvent event) {
        if (completed || event.isCancelled()) return;
        //handleWoolDrop(event.getPlayerContext().getPlayer());
        if (!touches.containsKey(event.getPlayerContext().getPlayer().getUniqueId())) return;

        touches.remove(event.getPlayerContext().getPlayer().getUniqueId());

        for (WoolObjectiveService woolObjectiveService : services) {
            woolObjectiveService.drop(event.getPlayerContext().getPlayer(), event.getOldTeam(), false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (TGM.get().getMatchManager().getMatch().getMatchStatus() == MatchStatus.POST) {
            handleWoolDrop(event.getPlayer(), false);
        } else {
            handleWoolDrop(event.getPlayer());
        }
    }

    public WoolStatus getStatus() {
        if (touches.isEmpty()) {
            return WoolStatus.UNTOUCHED;
        }

        if (completed) {
            return WoolStatus.COMPLETED;
        } else {
            return WoolStatus.TOUCHED;
        }
    }

    public void addService(WoolObjectiveService woolObjectiveService) {
        this.services.add(woolObjectiveService);
    }
}
