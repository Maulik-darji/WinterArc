package com.app.winterarc.data.cloud;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Minimal Supabase Realtime client (Phoenix protocol over a WebSocket). It joins one channel that
 * listens for {@code postgres_changes} on the shared social tables and calls {@code onChange} for
 * every insert, update or delete, from this app, other phones or the website. Reconnects with
 * backoff while started. Only change notifications are used; data is then pulled through the API.
 */
public final class RealtimeClient {
    static final String TOPIC = "realtime:winterarc-social";
    private static final String[] TABLES = {"feed_posts", "feed_post_likes", "post_comments", "profiles", "follows"};
    private static final long HEARTBEAT_SECONDS = 25;
    private static final long MAX_BACKOFF_SECONDS = 30;

    public interface TokenSource {
        /** The signed-in user's access token, or null to connect as a visitor. Called off the main thread. */
        @Nullable String token();
    }

    private final String socketUrl;
    private final TokenSource tokens;
    private final Runnable onChange;
    private final OkHttpClient http;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "winterarc-realtime");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger ref = new AtomicInteger();

    @Nullable private WebSocket socket;
    @Nullable private ScheduledFuture<?> heartbeat;
    private boolean started;
    private int attempt;

    public RealtimeClient(String projectUrl, String publishableKey, TokenSource tokens, Runnable onChange) {
        this.socketUrl = socketUrl(projectUrl, publishableKey);
        this.tokens = tokens;
        this.onChange = onChange;
        this.http = new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build();
    }

    /** "https://ref.supabase.co/" -> "wss://ref.supabase.co/realtime/v1/websocket?apikey=...&vsn=1.0.0" */
    @VisibleForTesting
    static String socketUrl(String projectUrl, String key) {
        String base = projectUrl.endsWith("/") ? projectUrl.substring(0, projectUrl.length() - 1) : projectUrl;
        base = base.replaceFirst("^https://", "wss://").replaceFirst("^http://", "ws://");
        return base + "/realtime/v1/websocket?apikey=" + key + "&vsn=1.0.0";
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        attempt = 0;
        scheduler.execute(this::connect);
    }

    public synchronized void stop() {
        started = false;
        if (heartbeat != null) heartbeat.cancel(false);
        heartbeat = null;
        if (socket != null) socket.close(1000, "stopped");
        socket = null;
    }

    private void connect() {
        synchronized (this) {
            if (!started) return;
        }
        String token = tokens.token();
        WebSocket ws = http.newWebSocket(new Request.Builder().url(socketUrl).build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                attempt = 0;
                webSocket.send(joinMessage(token, nextRef()));
                synchronized (RealtimeClient.this) {
                    if (heartbeat != null) heartbeat.cancel(false);
                    heartbeat = scheduler.scheduleWithFixedDelay(() -> webSocket.send(heartbeatMessage(nextRef())),
                            HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
                }
                onChange.run(); // catch up on anything missed while disconnected
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (isChange(text)) onChange.run();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                retry(webSocket);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
                retry(webSocket);
            }
        });
        synchronized (this) {
            socket = ws;
        }
    }

    private void retry(WebSocket closed) {
        synchronized (this) {
            if (socket != closed) return; // an older socket
            if (heartbeat != null) heartbeat.cancel(false);
            heartbeat = null;
            socket = null;
            if (!started) return;
        }
        long delay = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(attempt++, 5));
        scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    private String nextRef() {
        return String.valueOf(ref.incrementAndGet());
    }

    @VisibleForTesting
    static String joinMessage(@Nullable String accessToken, String ref) {
        JsonArray changes = new JsonArray();
        for (String table : TABLES) {
            JsonObject c = new JsonObject();
            c.addProperty("event", "*");
            c.addProperty("schema", "public");
            c.addProperty("table", table);
            changes.add(c);
        }
        JsonObject config = new JsonObject();
        config.add("postgres_changes", changes);
        JsonObject payload = new JsonObject();
        payload.add("config", config);
        if (accessToken != null) payload.addProperty("access_token", accessToken);
        JsonObject message = new JsonObject();
        message.addProperty("topic", TOPIC);
        message.addProperty("event", "phx_join");
        message.add("payload", payload);
        message.addProperty("ref", ref);
        message.addProperty("join_ref", ref);
        return message.toString();
    }

    private static String heartbeatMessage(String ref) {
        JsonObject message = new JsonObject();
        message.addProperty("topic", "phoenix");
        message.addProperty("event", "heartbeat");
        message.add("payload", new JsonObject());
        message.addProperty("ref", ref);
        return message.toString();
    }

    /** True for a database change notification on our channel. */
    @VisibleForTesting
    static boolean isChange(String text) {
        try {
            JsonObject m = JsonParser.parseString(text).getAsJsonObject();
            return m.has("event") && "postgres_changes".equals(m.get("event").getAsString())
                    && m.has("topic") && TOPIC.equals(m.get("topic").getAsString());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
