package network.warzone.tgm.modules.death;

import lombok.NoArgsConstructor;
import network.warzone.tgm.TGM;
import network.warzone.tgm.config.TGMConfigReloadEvent;
import network.warzone.tgm.match.Match;
import network.warzone.tgm.match.MatchModule;
import network.warzone.tgm.match.MatchStatus;
import network.warzone.tgm.modules.team.MatchTeam;
import network.warzone.tgm.modules.team.event.TeamChangeEvent;
import network.warzone.tgm.modules.team.TeamManagerModule;
import network.warzone.tgm.player.event.TGMPlayerDeathEvent;
import network.warzone.tgm.player.event.TGMPlayerRespawnEvent;
import network.warzone.tgm.util.Players;
import network.warzone.tgm.util.itemstack.ItemFactory;
import network.warzone.tgm.util.itemstack.ItemFilter;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor
public class DeathModule extends MatchModule implements Listener {

    private WeakReference<Match> match;

    private HashMap<UUID, DeathInfo> players = new HashMap<>();
    private Set<UUID> dead = new HashSet<>();
    private TeamManagerModule teamManagerModule;

    private long combatCooldown = 0; // Millis

    public void load(Match match) {
        this.match = new WeakReference<Match>(match);
        teamManagerModule = match.getModule(TeamManagerModule.class);
        loadConfig();
    }

    public void loadConfig() {
        this.combatCooldown = TGM.get().getConfig().getLong("combat.cooldown");
    }

    public void unload() {
        players = null;
    }

    @EventHandler
    public void onConfigReload(TGMConfigReloadEvent event) {
        loadConfig();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitLowest(PlayerQuitEvent event) {
        handleCombatLog(event.getPlayer().getPlayer(), teamManagerModule.getTeam(event.getPlayer()), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        resetPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeamChangeLowest(TeamChangeEvent event) {
        handleCombatLog(event.getPlayerContext().getPlayer(), event.getOldTeam(), event.isForced());
    }

    @EventHandler
    public void onTeamChange(TeamChangeEvent event) {
        resetPlayer(event.getPlayerContext().getPlayer());
    }

    public void handleCombatLog(Player player, MatchTeam team, boolean forced) {
        Match match = this.match.get();
        if (match == null || match.getMatchStatus() != MatchStatus.MID) return;
        if (isDead(player) || team == null || team.isSpectator()) return;
        DeathInfo info = getPlayer(player);
        info.playerTeam = team;
        if (!forced && Players.isFallingIntoVoid(player)) {
            info.cause = EntityDamageEvent.DamageCause.VOID;
            onDeath(player, info);
        } else if (!forced && isCombatLogging(info)) {
            info.playerLocation = player.getLocation();
            onDeath(player, info);
        } else { // No death but drop items
            List<ItemStack> drops = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
            match.getModules().stream()
                    .filter(module -> module instanceof ItemFilter)
                    .forEach(module -> ((ItemFilter) module).filter(drops));
            dropItems(player.getLocation(), drops);
        }
    }

    private void resetPlayer(Player player) {
        notDead(player);
        players.remove(player.getUniqueId());
    }

    private boolean isCombatLogging(DeathInfo info) {
        return info.stampKill > -1 && System.currentTimeMillis() - info.stampKill < combatCooldown;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (this.match.get().getMatchStatus() == MatchStatus.POST) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player) {
            if (this.dead.contains(event.getEntity().getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            Player p = (Player) event.getEntity();
            DeathInfo deathInfo = getPlayer((Player) event.getEntity());

            deathInfo.playerLocation = deathInfo.player.getLocation();
            deathInfo.cause = event.getCause();

            if (event instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent byEntityEvent = (EntityDamageByEntityEvent) event;
                Player damager = null;

                if (byEntityEvent.getDamager() instanceof Player) damager = (Player) byEntityEvent.getDamager();
                else if (byEntityEvent.getDamager() instanceof Projectile) {
                    Projectile projectile = (Projectile) byEntityEvent.getDamager();
                    if (projectile.getShooter() instanceof Player) damager = (Player) projectile.getShooter();
                }

                if (damager != null && (damager == p || teamManagerModule.getTeam(damager).isSpectator())) return;

                deathInfo.killer = damager;
                deathInfo.item = determineItemFromDamager(byEntityEvent.getDamager(), damager);

                deathInfo.killerName = damager == null ? null : damager.getName();
                deathInfo.stampKill = damager == null ? -1 : System.currentTimeMillis();
                deathInfo.killerTeam = damager == null ? null : teamManagerModule.getTeam(damager);
                deathInfo.killerLocation = damager == null ? null : damager.getLocation();
            }

            if (p.getHealth() - event.getFinalDamage() <= 0 || event.getCause().equals(EntityDamageEvent.DamageCause.VOID)) {
                event.setDamage(0);
                onDeath(p, deathInfo);
            }
        }
    }

    private static ItemStack determineItemFromDamager(Entity e, Player p) {
        if (e instanceof Arrow) {
            return ItemFactory.createItem(Material.BOW);
        } else if (e instanceof Trident) {
            return ItemFactory.createItem(Material.TRIDENT);
        } else if (p == null) {
            return ItemFactory.createItem(Material.AIR);
        } else return p.getInventory().getItemInMainHand();
    }

    public DeathInfo getPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (!players.containsKey(playerUUID)) {
            DeathInfo deathInfo = new DeathInfo(player);
            deathInfo.playerName = deathInfo.player.getName();
            deathInfo.playerTeam = teamManagerModule.getTeam(deathInfo.player);
            deathInfo.playerLocation = deathInfo.player.getLocation();
            deathInfo.cause = EntityDamageEvent.DamageCause.CUSTOM;
            players.put(playerUUID, deathInfo);
        }

        return players.get(playerUUID);
    }

    private void onDeath(Player player, DeathInfo deathInfo) {
        if (deathInfo.stampKill > 0 && System.currentTimeMillis() - deathInfo.stampKill >= 1000 * 15) deathInfo.killer = null;
        setDead(player);
        Bukkit.getPluginManager().callEvent(new TGMPlayerDeathEvent(
                deathInfo.player,
                deathInfo.playerLocation,
                deathInfo.killer,
                deathInfo.cause,
                deathInfo.item,
                Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).collect(Collectors.toList()),
                deathInfo
        ));
        deathInfo.killer = null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onPlayerDeath(TGMPlayerDeathEvent event) {
        if (match.get().getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY)) return;
        dropItems(event.getDeathInfo().playerLocation, event.getDrops());
    }

    private void dropItems(Location location, List<ItemStack> drops) {
        drops.stream()
                .filter(Objects::nonNull)
                .forEach(drop -> location.getWorld().dropItemNaturally(location, drop));
    }

    @EventHandler
    private void onRespawn(TGMPlayerRespawnEvent event) {
        resetPlayer(event.getPlayer());
    }

    private void setDead(Player player) {
        this.dead.add(player.getUniqueId());
    }

    private void notDead(Player player) {
        this.dead.remove(player.getUniqueId());
    }

    public boolean isDead(Player player) {
        return this.dead.contains(player.getUniqueId());
    }

}
