package com.minehut.tgm.util;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

public class Players {

    public static void reset(Player player, boolean heal) {
        if (heal) player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR)});
        for (PotionEffect effect : player.getActivePotionEffects()) {
            try {
                player.removePotionEffect(effect.getType());
            } catch (NullPointerException ignored) {
            }
        }
        player.setTotalExperience(0);
        player.setExp(0);
        player.setLevel(0);
        player.setWalkSpeed(0.2F);
        player.setFlySpeed(0.1F);

        player.setCanPickupItems(true);
        player.setFlying(false);
        player.setAllowFlight(false);
    }
}
