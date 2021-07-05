package network.warzone.tgm.modules.flag;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import network.warzone.tgm.TGM;
import network.warzone.tgm.match.Match;
import network.warzone.tgm.match.MatchStatus;
import network.warzone.tgm.modules.base.PlayerRedeemable;
import network.warzone.tgm.modules.region.CuboidRegion;
import network.warzone.tgm.modules.region.Region;
import network.warzone.tgm.modules.respawn.RespawnModule;
import network.warzone.tgm.modules.team.MatchTeam;
import network.warzone.tgm.modules.team.event.TeamChangeEvent;
import network.warzone.tgm.modules.team.TeamManagerModule;
import network.warzone.tgm.parser.banner.BannerPatternsDeserializer;
import network.warzone.tgm.parser.effect.EffectDeserializer;
import network.warzone.tgm.player.event.TGMPlayerDeathEvent;
import network.warzone.tgm.user.PlayerContext;
import network.warzone.tgm.util.Parser;
import network.warzone.tgm.util.Strings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

@Getter
public class MatchFlag extends PlayerRedeemable implements Listener {
    // banner details
    private List<Pattern> bannerPatterns;
    private String bannerType;
    private String rotation;
    private Location location;
    private String name;
    private long respawnTime;
    private boolean respawnBlock; // Whether this flag is capable of blocking a team from respawning (based on the json), never changes during a match.
    private List<PotionEffect> effects;

    private BukkitTask task;
    private boolean willRespawn;
    private long timeDropped;
    private boolean blockingRespawns; // Whether this flag is currently blocking a team from respawning (based on the match state).
    private List<Location> respawnLocations;
    private long secondsUntilRespawn;

    private FlagSubscriber flagSubscriber;
    private MatchTeam team; // Team that owns the flag (defends it)
    private MatchTeam capturer; // Team that has to capture this flag
    private Player flagHolder; // Player currently holding the flag
    private MatchTeam teamHolder; // Team currently holding the flag
    private Random rng;

    private Region protectiveRegion;

    private WeakReference<Match> match;
    private TeamManagerModule teamManagerModule;
    private RespawnModule respawnModule;

    public MatchFlag(List<Pattern> bannerPatterns, String bannerType, String rotation, Location location, FlagSubscriber flagSubscriber, MatchTeam team, String name, long respawnTime, boolean respawnBlock, List<Location> respawnLocations, List<PotionEffect> effects) {

        this.bannerPatterns = bannerPatterns;
        this.bannerType = bannerType;
        this.rotation = rotation;
        this.location = location;
        this.flagSubscriber = flagSubscriber;
        this.team = team;
        this.name = name;
        this.respawnTime = respawnTime;
        this.respawnBlock = respawnBlock;
        this.respawnLocations = respawnLocations;
        this.effects = effects;

        this.willRespawn = false;
        this.blockingRespawns = false;
        this.capturer = null;
        this.teamHolder = null;
        this.rng = respawnLocations == null ? null : new Random();
        this.secondsUntilRespawn = 0;

        this.match = new WeakReference<Match>(TGM.get().getMatchManager().getMatch());
        this.teamManagerModule = TGM.get().getModule(TeamManagerModule.class);
        this.respawnModule = TGM.get().getModule(RespawnModule.class);

        List<MatchTeam> teams = teamManagerModule.getTeams();
        // If there are only 2 playing teams, determine the capturer
        if(team != null && teams.size() == 3){
            for(MatchTeam loadedTeam : teams){
                if(!loadedTeam.equals(team) && !loadedTeam.isSpectator()){
                    capturer = loadedTeam;
                    break;
                }
            }
        }

        // No task necessary if json specifies instant flag respawns
        if(this.respawnTime > 0){
            task = Bukkit.getScheduler().runTaskTimer(TGM.get(), () -> {
                if (match.get().getMatchStatus() != MatchStatus.MID) return;
                secondsUntilRespawn = ((this.timeDropped + this.respawnTime) - now()) / (long)1000;
                if (this.willRespawn && secondsUntilRespawn <= 0) {
                    this.willRespawn = false;
                    placeFlag();
                    playRespawnSound();
                    if(this.team == null){
                        Bukkit.broadcastMessage(ChatColor.BOLD + this.name + ChatColor.GREEN +" has respawned.");
                    } else {
                        Bukkit.broadcastMessage(this.team.getColor() + "" + ChatColor.BOLD + this.team.getAlias() + ChatColor.WHITE + "'s " + ChatColor.BOLD + this.name + ChatColor.GREEN + " has respawned.");
                    }
                }
            }, 10L, 10L);
        }

        TGM.registerEvents(this);

        if (bannerType.contains("WALL")) { // TODO: Adjust this region when switching from block positions to coordinates
            this.protectiveRegion = new CuboidRegion(
                    location.clone().subtract(1, 2, 1),
                    location.clone().add(1, 1, 1)
            );
        } else {
            this.protectiveRegion = new CuboidRegion(
                    location.clone().subtract(1, 1, 1),
                    location.clone().add(1, 2, 1)
            );
        }

        placeFlag();
    }

    // Filters are wack. Do not allow build or break in the base region
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protectiveRegion.contains(event.getBlock())) return;
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot break near a flag area!");
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!protectiveRegion.contains(event.getBlock())) return;
        event.getPlayer().sendMessage(ChatColor.RED + "You cannot build near a flag area!");
        event.setCancelled(true);
    }

    public Material generateMaterial() {
        return Material.valueOf(Strings.getTechnicalName(bannerType) + "_BANNER");
    }

    public ItemStack generateBannerItem() {
        ItemStack bannerItem = new ItemStack(generateMaterial());
        ItemMeta itemMeta = bannerItem.getItemMeta();
        if (itemMeta instanceof BannerMeta) {
            BannerMeta bannerMeta = (BannerMeta) itemMeta;
            bannerMeta.setPatterns(bannerPatterns);
            bannerItem.setItemMeta(bannerMeta);
        }
        bannerItem.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        return bannerItem;
    }

    private long now() {
        return new Date().getTime();
    }

    private void refreshFlag() {
        if (match.get().getMatchStatus() != MatchStatus.MID) return;
        if (this.respawnTime <= 0) {
            placeFlag();
            playRespawnSound();
            if(this.team == null){
                Bukkit.broadcastMessage(ChatColor.BOLD + this.name + ChatColor.GREEN +" has respawned.");
            } else {
                Bukkit.broadcastMessage(this.team.getColor() + "" + ChatColor.BOLD + this.team.getAlias() + ChatColor.WHITE + "'s " + ChatColor.BOLD + this.name + ChatColor.GREEN +" has respawned.");
            }
        } else {
            this.timeDropped = now();
            this.willRespawn = true;
            if(this.team == null){
                Bukkit.broadcastMessage(ChatColor.BOLD + this.name + ChatColor.GREEN +" will respawn in "+ ChatColor.BOLD + "" + ChatColor.WHITE + this.respawnTime/1000 + ChatColor.GREEN + " seconds.");
            } else {
                Bukkit.broadcastMessage(this.team.getColor() + "" + ChatColor.BOLD + this.team.getAlias() + ChatColor.WHITE + "'s " + ChatColor.BOLD + this.name + ChatColor.GREEN +" will respawn in "+ ChatColor.BOLD + "" + ChatColor.WHITE + this.respawnTime/1000 + ChatColor.GREEN + " seconds.");
            }
        }
    }

    private void playRespawnSound() {
        for (MatchTeam team : teamManagerModule.getTeams()) {
            for (PlayerContext playerContext : team.getMembers()) {
                playerContext.getPlayer().playSound(playerContext.getPlayer().getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 1f, 1f);
            }
        }
    }

    private void placeFlag() {
        if (respawnLocations != null && match.get().getMatchStatus() == MatchStatus.MID) {
            location = respawnLocations.get(rng.nextInt(respawnLocations.size()));
        }
        Block block = location.getBlock();
        block.setType(generateMaterial(), false);

        final BlockState state = block.getState();
        if(state instanceof Banner) {
            Banner banner = (Banner) block.getState();
            banner.setPatterns(bannerPatterns);

            BlockData bannerData = banner.getBlockData();
            if (bannerData instanceof Rotatable) {
                Rotatable bannerRotatable = (Rotatable) bannerData;
                bannerRotatable.setRotation(BlockFace.valueOf(Strings.getTechnicalName(rotation)));
                banner.setBlockData(bannerRotatable);
            }

            banner.update(true);
        }
    }

    private boolean passesGeneralConditions(MatchTeam team) {
        return match.get().getMatchStatus() == MatchStatus.MID && !team.isSpectator();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (willRespawn || flagHolder != null) return;
        MatchTeam playerTeam = teamManagerModule.getTeam(event.getPlayer());
        if (respawnModule.isDead(event.getPlayer()) ||
            !passesGeneralConditions(playerTeam) ||
            playerTeam.equals(team)) return;
        else if (event.getFrom().distanceSquared(location) > 1) return;

        this.flagHolder = event.getPlayer();
        this.teamHolder = teamManagerModule.getTeam(this.flagHolder);
        location.getBlock().setType(Material.AIR);

        if(this.respawnBlock && !this.blockingRespawns){
            this.blockingRespawns = true;
            FlagRespawnBlockEvent newEvent = new FlagRespawnBlockEvent(this.teamHolder,false);
            Bukkit.getPluginManager().callEvent(newEvent);
        }
        flagSubscriber.pickup(this, event.getPlayer(), effects);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (match.get().getMatchStatus() != MatchStatus.MID || event.getPlayer() != flagHolder) return;
        if(this.respawnBlock && this.blockingRespawns){
            this.blockingRespawns = false;
            FlagRespawnBlockEvent newEvent = new FlagRespawnBlockEvent(this.teamHolder,true);
            Bukkit.getPluginManager().callEvent(newEvent);
        }
        this.flagHolder = null;
        flagSubscriber.drop(this, event.getPlayer(), null, effects);
        this.teamHolder = null;
        this.refreshFlag();
    }

    @EventHandler
    public void onTeamChange(TeamChangeEvent event) {
        if (match.get().getMatchStatus() != MatchStatus.MID || event.getPlayerContext().getPlayer() != flagHolder || event.getTeam().equals(event.getOldTeam())) return;
        if(this.respawnBlock && this.blockingRespawns){
            this.blockingRespawns = false;
            FlagRespawnBlockEvent newEvent = new FlagRespawnBlockEvent(this.teamHolder,true);
            Bukkit.getPluginManager().callEvent(newEvent);
        }
        this.flagHolder = null;
        flagSubscriber.drop(this, event.getPlayerContext().getPlayer(), null, effects);
        this.teamHolder = null;
        this.refreshFlag();
    }

    @EventHandler
    public void onTGMDeath(TGMPlayerDeathEvent event) {
        if (match.get().getMatchStatus() != MatchStatus.MID || event.getVictim() != flagHolder) return;
        if(this.respawnBlock && this.blockingRespawns){
            this.blockingRespawns = false;
            FlagRespawnBlockEvent newEvent = new FlagRespawnBlockEvent(this.teamHolder,true);
            Bukkit.getPluginManager().callEvent(newEvent);
        }
        this.flagHolder = null;
        flagSubscriber.drop(this, event.getVictim(), event.getKiller(), effects);
        this.teamHolder = null;
        this.refreshFlag();
    }

    public void unload() {
        flagHolder = null;
        teamHolder = null;

        // No task was scheduled if json specifies instant flag respawns
        if(this.respawnTime > 0){
            task.cancel();
        }
        TGM.unregisterEvents(this);
    }

    @Override
    public void redeem(Player player) {
        if(this.respawnBlock && this.blockingRespawns){
            this.blockingRespawns = false;
            FlagRespawnBlockEvent newEvent = new FlagRespawnBlockEvent(this.teamHolder,true);
            Bukkit.getPluginManager().callEvent(newEvent);
        }
        this.flagHolder = null;
        flagSubscriber.capture(this, player, effects);
        this.teamHolder = null;
        this.refreshFlag();
    }

    @Override
    public boolean hasRedeemable(Player player) {
        return flagHolder != null && flagHolder.equals(player);
    }

    /**
     * Returns a MatchFlag instance from JSON
     * @param flagJson Flag JSON
     * @param flagSubscriber Subscriber for event handling
     * @param world World for location parsing
     * @return Deserialized MatchFlag instance
     */
    public static MatchFlag deserialize(JsonObject flagJson, FlagSubscriber flagSubscriber, World world) {
        List<Pattern> bannerPatterns = flagJson.has("patterns") ? BannerPatternsDeserializer.parse(flagJson.get("patterns")) : new ArrayList<>();
        String bannerType = flagJson.get("type").getAsString();
        String bannerRotation = flagJson.has("rotation") ? flagJson.get("rotation").getAsString() : "EAST";
        Location location = Parser.convertLocation(world, flagJson.get("location"));

        TeamManagerModule teamManagerModule = TGM.get().getModule(TeamManagerModule.class);
        MatchTeam team = flagJson.has("team") ? teamManagerModule.getTeamById(flagJson.get("team").getAsString()) : null;

        String name = flagJson.has("name") ? flagJson.get("name").getAsString() : "Flag";

        // Defined in json in seconds, TGM converts to ms for internal use
        long respawnTime = flagJson.has("respawn-time") ? flagJson.get("respawn-time").getAsLong() * 1000 : 0;

        boolean respawnBlock = flagJson.has("respawn-block") ? flagJson.get("respawn-block").getAsBoolean() : false;

        List<Location> respawnLocations = null;
        if (flagJson.has("respawn-locations")) {
            respawnLocations = new ArrayList<>();
            for (JsonElement post : flagJson.get("respawn-locations").getAsJsonArray()) {
                respawnLocations.add(Parser.convertLocation(world, post));
            }
        }

        List<PotionEffect> effects = new ArrayList<>();
        if (flagJson.has("effects")) {
            for (JsonElement effect : flagJson.get("effects").getAsJsonArray()) {
                effects.add(EffectDeserializer.parse(effect.getAsJsonObject()));
            }
        }

        return new MatchFlag(bannerPatterns, bannerType, bannerRotation, location, flagSubscriber, team, name, respawnTime, respawnBlock, respawnLocations, effects);
    }

}
