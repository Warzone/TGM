package network.warzone.tgm.modules.kit.legacy_kits;

import network.warzone.tgm.modules.kit.legacy_kits.abilities.Ability;
import network.warzone.tgm.util.SlotType;
import network.warzone.tgm.util.itemstack.ItemFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class PhoenixKit extends LegacyKit {
    PhoenixKit(Ability... abilities) {
        super(abilities);
        super.setItem(0, ItemFactory.createItem(Material.WOODEN_SWORD));
        super.setItem(1, ItemFactory.createItem(Material.BOW));

        super.setItem(2, abilities[0].getAbilityItem());

        super.setItem(3, ItemFactory.createItem(Material.GOLDEN_APPLE));

        super.setItem(5, ItemFactory.createItem(Material.OAK_PLANKS, 64));
        super.setItem(6, ItemFactory.createItem(Material.WOODEN_AXE));

        super.setItem(8,  ItemFactory.createItem(Material.ARROW, 64));

        super.setItem(SlotType.HELMET.slot, ItemFactory.createItem(Material.CHAINMAIL_HELMET));
        super.setItem(SlotType.CHESTPLATE.slot, ItemFactory.createItem(Material.LEATHER_CHESTPLATE));
    }
}
