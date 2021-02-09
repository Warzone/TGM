package network.warzone.tgm.modules.ctf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import network.warzone.tgm.TGM;
import network.warzone.tgm.match.Match;
import network.warzone.tgm.match.MatchModule;
import network.warzone.tgm.modules.ItemRemoveModule;
import network.warzone.tgm.modules.base.MatchBase;
import network.warzone.tgm.modules.ctf.objective.*;
import network.warzone.tgm.modules.flag.MatchFlag;
import network.warzone.tgm.modules.region.Region;
import network.warzone.tgm.modules.region.RegionManagerModule;
import network.warzone.tgm.modules.team.MatchTeam;
import network.warzone.tgm.modules.team.TeamManagerModule;
import network.warzone.tgm.modules.time.TimeLimitService;
import network.warzone.tgm.modules.time.TimeModule;
import network.warzone.tgm.parser.effect.EffectDeserializer;
import network.warzone.tgm.util.Strings;
import network.warzone.tgm.util.itemstack.ItemUtils;
import org.bukkit.potion.PotionEffect;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yikes on 12/15/2019
 */
public class CTFModule extends MatchModule implements CTFControllerSubscriber {
    private CTFController controller;
    private List<MatchBase> matchBases = new ArrayList<>();
    private List<MatchFlag> matchFlags = new ArrayList<>();
    private List<PotionEffect> effects = new ArrayList<>();

    public static final String RIGHT_ARROW = "\u2794"; // ➔

    @Override
    public void load(Match match) {
        // Grab CTF Json
        JsonObject ctfJson = match.getMapContainer().getMapInfo().getJsonObject().get("ctf").getAsJsonObject();

        // Determine controller from objective
        CTFObjective objective = CTFObjective.valueOf(Strings.getTechnicalName(ctfJson.get("objective").getAsString()));

        // Based on Objective, determine controller and apply other effects
        JsonObject optionObject = ctfJson.get("options").getAsJsonObject();

        if (optionObject.has("effects")) {
            for (JsonElement effect : optionObject.get("effects").getAsJsonArray()) {
                this.effects.add(EffectDeserializer.parse(effect.getAsJsonObject()));
            }
        }

        if (objective == CTFObjective.TIME) {
            TimeModule timeModule = TGM.get().getModule(TimeModule.class);
            int timeLimit = optionObject.get("time").getAsInt();
            timeModule.setTimeLimited(true);
            timeModule.setTimeLimit(timeLimit);
            this.controller = new CTFTimeController(this, matchFlags, timeLimit, effects);
            timeModule.setTimeLimitService((TimeLimitService) this.controller);
        } else if (objective == CTFObjective.AMOUNT) {
            int captureAmount = optionObject.get("captures").getAsInt();
            this.controller = new CTFAmountController(this, matchFlags, captureAmount, effects);
        }

        // Deserialize flags json into MatchFlag instances
        World world = match.getWorld();
        for (JsonElement flagElem : ctfJson.get("flags").getAsJsonArray()) {
            this.matchFlags.add(MatchFlag.deserialize(flagElem.getAsJsonObject(), controller, world));
        }

        // Don't allow flags to be dropped
        ItemRemoveModule itemRemoveModule = TGM.get().getModule(ItemRemoveModule.class);
        itemRemoveModule.addAll(ItemUtils.allBannerTypes());


        // If Objective is amount, bases are required for flags to be captured
        if (objective == CTFObjective.AMOUNT) {
            RegionManagerModule regionManagerModule = TGM.get().getModule(RegionManagerModule.class);
            TeamManagerModule teamManagerModule = TGM.get().getModule(TeamManagerModule.class);
            for (JsonElement element : ctfJson.get("bases").getAsJsonArray()) {
                JsonObject baseObject = element.getAsJsonObject();
                Region baseRegion = regionManagerModule.getRegion(match, baseObject.get("region"));
                MatchTeam matchTeam = teamManagerModule.getTeamById(baseObject.get("team").getAsString());
                List<MatchFlag> flags = new ArrayList<>();
                for (MatchFlag flag : matchFlags) {
                    if (flag.getTeam() != null && flag.getTeam().equals(matchTeam)) continue;
                    flags.add(flag);
                }
                matchBases.add(new MatchBase(baseRegion, matchTeam, flags));
            }
        }
    }

    @Override
    public void disable() {
        for (MatchFlag matchFlag : matchFlags) matchFlag.unload();
        for (MatchBase matchBase : matchBases) matchBase.unload();
        controller.unload();
        controller = null;
        matchFlags = null;
        matchBases = null;
    }

    @Override
    public void gameOver(MatchTeam team) {
        TGM.get().getMatchManager().endMatch(team);
    }
}
