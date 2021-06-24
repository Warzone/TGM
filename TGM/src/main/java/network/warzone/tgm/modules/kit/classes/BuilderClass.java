package network.warzone.tgm.modules.kit.classes;

import network.warzone.tgm.modules.kit.classes.abilities.Ability;
import network.warzone.tgm.util.SlotType;
import network.warzone.tgm.util.itemstack.ItemFactory;
import org.bukkit.Material;

public class BuilderClass extends GameClass {
    public BuilderClass(Ability... abilities) {
        super(abilities);

        super.setItem(0, ItemFactory.createItem(Material.IRON_PICKAXE));
        super.setItem(1, ItemFactory.createItem(Material.STONE_AXE));
        super.setItem(2, ItemFactory.createItem(Material.BOW));

        super.setItem(3, abilities[0].getAbilityItem());
        super.setItem(4, ItemFactory.createItem(Material.GOLDEN_APPLE));
        super.setItem(5, ItemFactory.createItem(Material.COOKED_BEEF, 64));

        super.setItem(6, ItemFactory.createItem(Material.OAK_PLANKS, 64));
        super.setItem(7, ItemFactory.createItem(Material.OAK_STAIRS, 64));
        super.setItem(8, ItemFactory.createItem(Material.LADDER, 16));

        super.setItem(9, ItemFactory.createItem(Material.GLASS, 64));
        super.setItem(10, ItemFactory.createItem(Material.OAK_LOG, 64));
        super.setItem(11, ItemFactory.createItem(Material.OAK_LOG, 64));

        super.setItem(21, ItemFactory.createItem(Material.ARROW, 64));


        super.setItem(SlotType.HELMET.getSlot(), ItemFactory.createItem(Material.LEATHER_HELMET));
        super.setItem(SlotType.CHESTPLATE.getSlot(), ItemFactory.createItem(Material.LEATHER_CHESTPLATE));
        super.setItem(SlotType.BOOTS.getSlot(), ItemFactory.createItem(Material.IRON_BOOTS));
    }
}
