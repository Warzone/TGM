package network.warzone.tgm.modules.region;

import lombok.Getter;
import network.warzone.tgm.TGM;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jorge on 09/09/2019
 */
@Getter
public class SphereRegion implements Region {

    private final Location center;
    private final double radius;

    private final Location min;
    private final Location max;

    private final CuboidRegion bound;

    public SphereRegion(Location center, double radius) {
        this.center = center;
        this.radius = radius;

        this.min = new Location(center.getWorld(), center.getX() - radius, center.getY() - radius, center.getZ() - radius);
        this.max = new Location(center.getWorld(), center.getX() + radius, center.getY() + radius, center.getZ() + radius);

        this.bound = new CuboidRegion(this.min, this.max);
    }

    @Override
    public boolean contains(Location location) {
        return center.distanceSquared(location) <= radius * radius;
    }

    @Override
    public boolean contains(Block block) {
        return contains(block.getLocation());
    }


    @Override
    public Location getCenter() {
        return this.center;
    }

    @Override
    public Location getRandomLocation() {
        Location location = bound.getRandomLocation();

        while (!contains(location)) {
            location = bound.getRandomLocation();
        }

        return location;
    }

    @Override
    public List<Block> getBlocks() {
        List<Block> results = new ArrayList<>();
        CuboidRegion bound = new CuboidRegion(getMin(), getMax());
        for (Block block : bound.getBlocks()) {
            if (contains(block)) results.add(block);
        }
        return results;
    }

    @Override
    public Location getMin() {
        return this.min;
    }

    @Override
    public Location getMax() {
        return this.max;
    }
}
