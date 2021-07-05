package network.warzone.tgm.modules.filter.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import network.warzone.tgm.match.Match;
import network.warzone.tgm.modules.filter.FilterManagerModule;
import network.warzone.tgm.modules.filter.FilterResult;
import network.warzone.tgm.modules.filter.evaluate.FilterEvaluator;
import network.warzone.tgm.modules.region.Region;
import network.warzone.tgm.modules.region.RegionManagerModule;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jorge on 9/9/2017.
 */
@AllArgsConstructor @Getter
public class BlockExplodeFilterType implements FilterType, Listener {

    private final List<Region> regions;
    private final FilterEvaluator evaluator;
    private final boolean inverted;

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> cancelledBlocks = new ArrayList<>();
        for (Block block : event.blockList()) {
            for (Region region : regions) {
                FilterResult filterResult = evaluator.evaluate();
                if (filterResult == FilterResult.DENY && contains(region, block) &&
                        !cancelledBlocks.contains(block)) {
                    cancelledBlocks.add(block);
                }
            }
        }

        for (Block block : cancelledBlocks) {
            event.blockList().remove(block);
        }
    }

    private boolean contains(Region region, Block block) {
        return (!inverted && region.contains(block)) || (inverted && !region.contains(block));
    }

    public static BlockExplodeFilterType parse(Match match, JsonObject jsonObject) {
        List<Region> regions = new ArrayList<>();
        for (JsonElement regionElement : jsonObject.getAsJsonArray("regions")) {
            Region region = match.getModule(RegionManagerModule.class).getRegion(match, regionElement);
            if (region != null) {
                regions.add(region);
            }
        }

        FilterEvaluator filterEvaluator = FilterManagerModule.initEvaluator(match, jsonObject);
        boolean inverted = jsonObject.has("inverted") && jsonObject.get("inverted").getAsBoolean();
        return new BlockExplodeFilterType(regions, filterEvaluator, inverted);
    }

}
