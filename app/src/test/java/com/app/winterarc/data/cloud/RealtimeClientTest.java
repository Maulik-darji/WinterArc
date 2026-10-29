package com.app.winterarc.data.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

public class RealtimeClientTest {
    @Test
    public void buildsTheSupabaseRealtimeSocketUrl() {
        assertEquals("wss://abc.supabase.co/realtime/v1/websocket?apikey=key&vsn=1.0.0",
                RealtimeClient.socketUrl("https://abc.supabase.co/", "key"));
    }

    @Test
    public void joinsOneChannelForAllSocialTablesWithTheUsersToken() {
        JsonObject join = JsonParser.parseString(RealtimeClient.joinMessage("jwt-1", "1")).getAsJsonObject();
        assertEquals("phx_join", join.get("event").getAsString());
        assertEquals(RealtimeClient.TOPIC, join.get("topic").getAsString());
        JsonObject payload = join.getAsJsonObject("payload");
        assertEquals("jwt-1", payload.get("access_token").getAsString());
        String tables = payload.getAsJsonObject("config").getAsJsonArray("postgres_changes").toString();
        for (String t : new String[]{"feed_posts", "feed_post_likes", "post_comments", "profiles", "follows"}) {
            assertTrue(tables, tables.contains("\"table\":\"" + t + "\""));
        }
        assertFalse(JsonParser.parseString(RealtimeClient.joinMessage(null, "2")).getAsJsonObject()
                .getAsJsonObject("payload").has("access_token"));
    }

    @Test
    public void reactsOnlyToDatabaseChangesOnItsChannel() {
        assertTrue(RealtimeClient.isChange("{\"topic\":\"" + RealtimeClient.TOPIC + "\",\"event\":\"postgres_changes\",\"payload\":{}}"));
        assertFalse(RealtimeClient.isChange("{\"topic\":\"phoenix\",\"event\":\"phx_reply\",\"payload\":{}}"));
        assertFalse(RealtimeClient.isChange("{\"topic\":\"" + RealtimeClient.TOPIC + "\",\"event\":\"phx_reply\"}"));
        assertFalse(RealtimeClient.isChange("not json"));
    }
}
