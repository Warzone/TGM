package network.warzone.tgm.modules.kit.legacy_kits.abilities;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by yikes on 09/27/19
 */
public class AbilityManager {
    public enum AbilityStore {
        PHOENIX(PhoenixAbility.class),
        NINJA(NinjaAbility.class);

        @Getter private Class hostAbility;
        AbilityStore(Class hostAbility) {
            this.hostAbility = hostAbility;
        }
    }
    
    private Set<Ability> abilities = new HashSet<>();

    public AbilityManager() {
        for(AbilityStore abilityStore : AbilityStore.values()) {
            try {
                abilities.add((Ability) abilityStore.getHostAbility().getConstructors()[0].newInstance());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void destroyAbilities() {
        for(Ability ability : abilities) {
            ability.terminate();
        }
        abilities = null;
    }

    public void removePlayerFromAbilityCache(Ability ability, Player player) {
        ability.getRegisteredPlayers().remove(player.getUniqueId());
    }

    @SuppressWarnings("unchecked")
    public <T extends Ability> T getAbility(Class<T> clazz) {
        for(Ability ability : abilities) {
            if (clazz.isInstance(ability)) return ((T) ability);
        }
        return null;
    }
}
