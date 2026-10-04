package com.app.winterarc.data.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.data.db.WinterArcDatabase;
import com.app.winterarc.data.db.entity.MotivationalContentEntity;
import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.ContentType;
import com.app.winterarc.domain.model.MotivationalContent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.EnumSet;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@Config(application = Application.class)
public class BundledContentRepositoryTest {

    @Test
    public void unsourcedFactsAndQuotesAreDropped() throws Exception {
        String json = "{\"version\":2,\"items\":["
                + "{\"id\":\"a\",\"type\":\"fact\",\"message\":\"no source\"},"
                + "{\"id\":\"b\",\"type\":\"quote\",\"message\":\"q\",\"source_name\":\"S\",\"source_url\":\"http://insecure\"},"
                + "{\"id\":\"c\",\"type\":\"fact\",\"message\":\"ok\",\"source_name\":\"S\",\"source_url\":\"https://example.org\"},"
                + "{\"id\":\"d\",\"type\":\"encouragement\",\"message\":\"keep going\"}]}";
        List<MotivationalContentEntity> parsed = BundledContentRepository.parse(json);
        assertEquals(2, parsed.size());
        assertEquals("c", parsed.get(0).id);
        assertEquals("d", parsed.get(1).id);
    }

    @Test
    public void bundledAssetSeedsAndEveryFactOrQuoteIsAttributed() {
        WinterArcDatabase db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WinterArcDatabase.class)
                .allowMainThreadQueries().build();
        BundledContentRepository repo = new BundledContentRepository(ApplicationProvider.getApplicationContext(),
                db.contentDao(), CrashReporter.NO_OP);
        repo.ensureSeeded();
        List<MotivationalContent> all = repo.getAll();
        assertFalse(all.isEmpty());
        for (MotivationalContent c : all) {
            if (c.contentType() == ContentType.FACT || c.contentType() == ContentType.QUOTE) {
                assertTrue(c.id(), c.isAttributed());
                assertTrue(c.id(), c.sourceUrl().startsWith("https://"));
            }
        }
        // Marathon fact applies only to longer running targets.
        MotivationalContent pick = repo.pick(ActivityType.RUNNING, Amount.whole(25), EnumSet.of(ContentType.FACT), 2);
        assertTrue(pick != null);
        repo.ensureSeeded(); // idempotent
        assertEquals(all.size(), repo.getAll().size());
        db.close();
    }
}
