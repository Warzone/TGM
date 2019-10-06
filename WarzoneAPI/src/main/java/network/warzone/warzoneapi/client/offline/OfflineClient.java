package network.warzone.warzoneapi.client.offline;

import com.google.gson.Gson;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import network.warzone.warzoneapi.client.TeamClient;
import network.warzone.warzoneapi.models.Map;
import network.warzone.warzoneapi.models.*;
import org.bson.types.ObjectId;

import java.util.*;

/**
 * Created by luke on 4/27/17.
 */
public class OfflineClient implements TeamClient {

    public OfflineClient() {
        Gson gson = new Gson();
        Unirest.setObjectMapper(new ObjectMapper() {

            public <T> T readValue(String s, Class<T> aClass) {
                try{
                    return gson.fromJson(s, aClass);
                } catch(Exception e){
                    throw new RuntimeException(e);
                }
            }

            public String writeValue(Object o) {
                try{
                    return gson.toJson(o);
                } catch(Exception e){
                    throw new RuntimeException(e);
                }
            }
        });

    }

    @Override
    public void heartbeat(Heartbeat heartbeat) {

    }

    @Override
    public GetPlayerByNameResponse player(String name) {
        return null;
    }

    @Override
    public UserProfile login(PlayerLogin playerLogin) {
        List<String> ranks = new ArrayList<String>();
        return new UserProfile(new ObjectId(), playerLogin.getName(), playerLogin.getName().toLowerCase(),
                playerLogin.getUuid(), new Date().getTime(), new Date().getTime(), Collections.singletonList(playerLogin.getIp()), ranks, new ArrayList<Rank>(), 0, 0, 0, 0, 0, new ArrayList<Punishment>(), false);
    }

    @Override
    public MapLoadResponse loadmap(Map map) {
        return new MapLoadResponse(false, new ObjectId().toString());
    }

    @Override
    public void addKill(Death death) {

    }

    @Override
    public MatchInProgress loadMatch(MatchLoadRequest matchLoadRequest) {
        return new MatchInProgress(new ObjectId().toString(), matchLoadRequest.getMap());
    }

    @Override
    public void finishMatch(MatchFinishPacket matchFinishPacket) {

    }

    @Override
    public void destroyWool(DestroyWoolRequest destroyWoolRequest) {

    }

    @Override
    public RankList retrieveRanks() {
        return new RankList();
    }

    @Override
    public RankUpdateResponse updateRank(String player, RankUpdateRequest.Action action, RankUpdateRequest rankUpdateRequest){
        return null;
    };

    @Override
    public RankManageResponse manageRank(RankManageRequest.Action action, RankManageRequest rankManageRequest) {
        return null;
    }

    @Override
    public RankManageResponse editRank(RankEditRequest.EditableField field, RankEditRequest rankEditRequest) {
        return null;
    }

    @Override
    public RankManageResponse editPermissions(RankPermissionsUpdateRequest.Action action, RankPermissionsUpdateRequest permissionsUpdateRequest) {
        return null;
    }

    @Override
    public IssuePunishmentResponse issuePunishment(IssuePunishmentRequest issuePunishmentRequest) {
        return null;
    }

    @Override
    public PunishmentsListResponse getPunishments(PunishmentsListRequest punishmentsListRequest) {
        return null;
    }

    @Override
    public KillsLeaderboardResponse getKillsLeaderboard() { return new KillsLeaderboardResponse(); }

    @Override
    public RevertPunishmentResponse revertPunishment(String id) {
        return null;
    }

    @Override
    public PlayerInfoResponse getPlayerInfo(PlayerInfoRequest playerInfoRequest) {
        return null;
    }

    @Override
    public PlayerAltsResponse getAlts(String name) {
        return null;
    }

    @Override
    public MojangProfile getMojangProfile(UUID uuid) {
        return getMojangProfile(uuid.toString());
    }

    @Override
    public MojangProfile getMojangProfile(String username) {
        try {
            HttpResponse<MojangProfile> response = Unirest.get("https://api.ashcon.app/mojang/v2/user/" + username)
                    .asObject(MojangProfile.class);
            return response.getBody();
        } catch (UnirestException e) {
            e.printStackTrace();
            return null;
        }
    }
}
