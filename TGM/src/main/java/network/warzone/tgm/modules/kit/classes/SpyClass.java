package network.warzone.tgm.modules.kit.classes;

import network.warzone.tgm.modules.kit.classes.abilities.Ability;
import network.warzone.tgm.util.SlotType;
import network.warzone.tgm.util.itemstack.ItemFactory;
import org.bukkit.Material;

/**
 * Created by lucas on 4/28/16.
 */
public class SpyClass extends GameClass {

    public SpyClass(Ability... abilities) {
        super(abilities);

        super.setItem(0, ItemFactory.createItem(Material.WOODEN_SWORD));
        super.setItem(1, ItemFactory.createItem(Material.BOW));
        super.setItem(2, ItemFactory.createItem(Material.GOLDEN_APPLE));

        super.setItem(3, abilities[0].getAbilityItem());
        super.setItem(5, ItemFactory.createItem(Material.OAK_PLANKS, 64));
        super.setItem(6, ItemFactory.createItem(Material.WOODEN_AXE));
        super.setItem(7, ItemFactory.createItem(Material.COOKED_BEEF, 64));

        super.setItem(8, ItemFactory.createItem(Material.ARROW, 64));

        super.setItem(SlotType.HELMET.getSlot(), ItemFactory.createItem(Material.LEATHER_HELMET));
        super.setItem(SlotType.CHESTPLATE.getSlot(), ItemFactory.createItem(Material.LEATHER_CHESTPLATE));
        super.setItem(SlotType.LEGGINGS.getSlot(), ItemFactory.createItem(Material.LEATHER_LEGGINGS));
        super.setItem(SlotType.BOOTS.getSlot(), ItemFactory.createItem(Material.LEATHER_BOOTS));

    }

}
