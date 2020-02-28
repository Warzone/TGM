package network.warzone.tgm.modules.knockback;

import net.minecraft.server.v1_15_R1.EntityPlayer;
import network.warzone.tgm.match.MatchModule;
import org.bukkit.craftbukkit.v1_15_R1.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class KnockbackModule extends MatchModule implements Listener {

    @EventHandler(priority= EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity livingEntity = (LivingEntity) event.getEntity();
        if (event.getDamager() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getDamager();
            event.setDamage(event.getDamage() / 1.75);
            applyBowKnockback(arrow, livingEntity);
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getDamager() instanceof LivingEntity) {
            applyMeleeKnockback((LivingEntity) event.getDamager(), livingEntity);
        }
    }

    @EventHandler
    public void onArrowsShoot(ProjectileLaunchEvent e) {
        Projectile projectile = e.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof Player) {
            Player player = (Player) shooter;
            projectile.setVelocity(player.getLocation().getDirection().normalize().multiply(projectile.getVelocity().length()));
        }
    }

    private void applyBowKnockback(Arrow a, Entity e) {
        EntityPlayer nmsHuman = ((CraftPlayer) e).getHandle();
        Vector arrowVelocity = a.getVelocity();
        float f3 = (float) Math.sqrt(arrowVelocity.getX() * arrowVelocity.getX() + arrowVelocity.getZ() * arrowVelocity.getZ());
        nmsHuman.h(arrowVelocity.getX() * (double) a.getKnockbackStrength() * 0.6000000238418579D / (double) f3, 0.1D, arrowVelocity.getZ() * (double) a.getKnockbackStrength() * 0.6000000238418579D / (double) f3);
//        Vector normalVelocity = a.getVelocity();
//        if (e.isOnGround()) normalVelocity.setY(0.3);
//        else normalVelocity.setY(0);
//        e.setVelocity(normalVelocity.normalize().multiply(0.2f));
    }

    private void applyMeleeKnockback(LivingEntity attacker, LivingEntity victim) {
        Vector kb = attacker.getLocation().getDirection().setY(victim.isOnGround() ? 0.4 : 0).normalize().multiply(0.25f);
        victim.setVelocity(kb);
    }

}
