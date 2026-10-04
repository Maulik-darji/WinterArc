package com.app.winterarc.data.content;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.data.db.Mappers;
import com.app.winterarc.data.db.dao.MotivationalContentDao;
import com.app.winterarc.data.db.entity.MotivationalContentEntity;
import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.ContentType;
import com.app.winterarc.domain.model.MotivationalContent;
import com.app.winterarc.domain.repository.ContentRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Offline content set shipped in assets/content/motivation.json and copied into Room. A future
 * backend can deliver a newer JSON payload in the same format and call {@link #replace}.
 */
@Singleton
public class BundledContentRepository implements ContentRepository {
    static final String ASSET_PATH = "content/motivation.json";

    private final Context context;
    private final MotivationalContentDao dao;
    private final CrashReporter crashReporter;

    @Inject
    public BundledContentRepository(@ApplicationContext Context context, MotivationalContentDao dao,
                                    CrashReporter crashReporter) {
        this.context = context;
        this.dao = dao;
        this.crashReporter = crashReporter;
    }

    @Override
    public void ensureSeeded() {
        try {
            String json = readAsset();
            int bundledVersion = new JSONObject(json).getInt("version");
            Integer stored = dao.storedVersion();
            if (stored == null || stored < bundledVersion) replace(json);
        } catch (IOException | JSONException e) {
            crashReporter.recordNonFatal(e, "content seed");
        }
    }

    /** Replaces the stored content with a validated payload. Invalid items are dropped. */
    public void replace(String json) throws JSONException {
        dao.replaceAll(parse(json));
    }

    @Override
    public List<MotivationalContent> getAll() {
        return Mappers.mapList(dao.getAll(), Mappers::toDomain);
    }

    @Nullable
    @Override
    public MotivationalContent pick(ActivityType activity, @Nullable Amount targetKm, Set<ContentType> types, int seed) {
        List<MotivationalContent> candidates = new ArrayList<>();
        for (MotivationalContent c : getAll()) {
            if (!types.contains(c.contentType()) || !c.isShowable()) continue;
            if (c.matches(activity, targetKm)) candidates.add(c);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(Math.floorMod(seed, candidates.size()));
    }

    @VisibleForTesting
    static List<MotivationalContentEntity> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int version = root.getInt("version");
        String locale = root.optString("locale", "en");
        JSONArray items = root.getJSONArray("items");
        List<MotivationalContentEntity> out = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            MotivationalContentEntity e = new MotivationalContentEntity();
            e.id = o.getString("id");
            e.contentType = o.getString("type");
            e.activityType = optString(o, "activity");
            e.minTargetMilli = optAmount(o, "min_km");
            e.maxTargetMilli = optAmount(o, "max_km");
            e.message = o.getString("message");
            e.attribution = optString(o, "attribution");
            e.sourceName = optString(o, "source_name");
            e.sourceUrl = optString(o, "source_url");
            e.locale = locale;
            e.contentVersion = version;
            boolean needsSource = ContentType.FACT.key().equals(e.contentType) || ContentType.QUOTE.key().equals(e.contentType);
            boolean hasSource = e.sourceName != null && e.sourceUrl != null && e.sourceUrl.startsWith("https://");
            // Unsourced facts or quotes are never stored, so they can never be displayed.
            if (needsSource && !hasSource) continue;
            out.add(e);
        }
        return out;
    }

    @Nullable
    private static String optString(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String v = o.optString(key, "").trim();
        return v.isEmpty() ? null : v;
    }

    @Nullable
    private static Long optAmount(JSONObject o, String key) {
        Amount a = Amount.parse(optString(o, key));
        return a == null ? null : a.milli();
    }

    private String readAsset() throws IOException {
        try (InputStream in = context.getAssets().open(ASSET_PATH);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
