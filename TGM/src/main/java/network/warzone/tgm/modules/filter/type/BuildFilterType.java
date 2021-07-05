package network.warzone.tgm.modules.filter.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import network.warzone.tgm.match.Match;
import network.warzone.tgm.modules.filter.FilterManagerModule;
import network.warzone.tgm.modules.filter.FilterResult;
import network.warzone.tgm.modules.filter.evaluate.FilterEvaluator;
import network.warzone.tgm.modules.region.Region;
import network.warzone.tgm.modules.region.RegionManagerModule;
import network.warzone.tgm.modules.team.MatchTeam;
import network.warzone.tgm.modules.team.TeamManagerModule;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor @Getter
public class BuildFilterType implements FilterType, Listener {

    private final List<MatchTeam> teams;
    private final List<Region> regions;
    private final FilterEvaluator evaluator;
    private final String message;
    private final boolean inverted;

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        for (Region region : regions) {
            if (contains(region, event.getBlockPlaced())) {
                for (MatchTeam matchTeam : teams) {
                    if (matchTeam.containsPlayer(event.getPlayer())) {
                        FilterResult filterResult = evaluator.evaluate(event.getPlayer());
                        if (filterResult == FilterResult.DENY) {
                            event.setCancelled(true);
                            if (message != null) event.getPlayer().sendMessage(message);
                        } else if (filterResult == FilterResult.ALLOW) {
                            event.setCancelled(false);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        for (Region region : regions) {
            if (contains(region, event.getBlock())) {
                for (MatchTeam matchTeam : teams) {
                    if (matchTeam.containsPlayer(event.getPlayer())) {
                        FilterResult filterResult = evaluator.evaluate(event.getPlayer());
                        if (filterResult == FilterResult.DENY) {
                            event.setCancelled(true);
                            event.getPlayer().sendMessage(message);
                        } else if (filterResult == FilterResult.ALLOW) {
                            event.setCancelled(false);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerClickItemFram(PlayerInteractEntityEvent event) {
        if (!event.isCancelled() && event.getRightClicked() instanceof ItemFrame) {
            for (Region region : regions) {
                if (contains(region, event.getRightClicked().getLocation())) {
                    for (MatchTeam matchTeam : teams) {
                        if (matchTeam.containsPlayer(event.getPlayer())) {
                            FilterResult filterResult = evaluator.evaluate(event.getPlayer());
                            if (filterResult == FilterResult.DENY) {
                                event.setCancelled(true);
                                event.getPlayer().sendMessage(message);
                            } else if (filterResult == FilterResult.ALLOW) {
                                event.setCancelled(false);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onHangingDamage(EntityDamageByEntityEvent event) {
        if (!event.isCancelled() && event.getEntity() != null && event.getEntity() instanceof ItemFrame) {
            if (event.getDamager() instanceof Player) {
                Player player = ((Player) event.getDamager()).getPlayer();

                for (Region region : regions) {
                    if (contains(region, player.getLocation())) {
                        for (MatchTeam matchTeam : teams) {
                            if (matchTeam.containsPlayer(player)) {
                                FilterResult filterResult = evaluator.evaluate(player);
                                if (filterResult == FilterResult.DENY) {
                                    event.setCancelled(true);
                                    player.sendMessage(message);
                                } else if (filterResult == FilterResult.ALLOW) {
                                    event.setCancelled(false);
                                }
                            }
                        }
                    }
                }
            } else {
                for (Region region : regions) {
                    if (contains(region, event.getEntity().getLocation())) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        if (!event.isCancelled()) {
            for (Region region : regions) {
                if (contains(region, event.getEntity().getLocation())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!event.isCancelled()) {
            FilterResult filterResult = evaluator.evaluate();
            if (filterResult == FilterResult.DENY) {
                for (Region region : regions) {
                    if (contains(region, event.getBlock().getLocation().clone().add(event.getDirection().getModX(), event.getDirection().getModY(), event.getDirection().getModZ()))) {
                        event.setCancelled(true);
                        return;
                    } else {
                        for (Block block : event.getBlocks()) {
                            if (contains(region, event.getBlock().getLocation().clone().add(event.getDirection().getModX(), event.getDirection().getModY(), event.getDirection().getModZ())) || contains(region, block.getLocation().clone().add(event.getDirection().getModX(), event.getDirection().getModY(), event.getDirection().getModZ()))) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isCancelled()) {
            FilterResult filterResult = evaluator.evaluate();
            if (filterResult == FilterResult.DENY) {
                for (Region region : regions) {
                    if (contains(region, event.getBlock().getLocation().clone().add(event.getDirection().getOppositeFace().getModX(), event.getDirection().getOppositeFace().getModY(), event.getDirection().getOppositeFace().getModZ()))) {
                        event.setCancelled(true);
                        return;
                    } else {
                        for (Block block : event.getBlocks()) {
                            if (contains(region, block.getLocation().clone().add(event.getDirection().getOppositeFace().getModX(), event.getDirection().getOppositeFace().getModY(), event.getDirection().getOppositeFace().getModZ()))) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean contains(Region region, Block block) {
        return (!inverted && region.contains(block)) || (inverted && !region.contains(block));
    }

    private boolean contains(Region region, Location location) {
        return (!inverted && region.contains(location)) || (inverted && !region.contains(location));
    }

    public static BuildFilterType parse(Match match, JsonObject jsonObject) {
        List<MatchTeam> matchTeams = match.getModule(TeamManagerModule.class).getTeams(jsonObject.get("teams").getAsJsonArray());
        List<Region> regions = new ArrayList<>();

        for (JsonElement regionElement : jsonObject.getAsJsonArray("regions")) {
            Region region = match.getModule(RegionManagerModule.class).getRegion(match, regionElement);
            if (region != null) {
                regions.add(region);
            }
        }

        FilterEvaluator filterEvaluator = FilterManagerModule.initEvaluator(match, jsonObject);
        String message = jsonObject.has("message") ? ChatColor.translateAlternateColorCodes('&', jsonObject.get("message").getAsString()) : null;
        boolean inverted = jsonObject.has("inverted") && jsonObject.get("inverted").getAsBoolean();
        return new BuildFilterType(matchTeams, regions, filterEvaluator, message, inverted);
    }
}
