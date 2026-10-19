package com.liskovsoft.youtubeapi.search;

import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Expected values: the params YouTube's WEB "Search filters" dialog sent on 2026-10-19. */
@RunWith(RobolectricTestRunner.class)
public class SearchFilterHelperTest {
    @Test
    public void matchesYouTubeDialog() {
        assertNull(SearchFilterHelper.toParams(-1));
        assertNull(SearchFilterHelper.toParams(0));
        assertEquals("EgIQAQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.TYPE_VIDEO));
        assertEquals("EgIQBA%3D%3D", SearchFilterHelper.toParams(SearchOptions.TYPE_MOVIE));
        assertEquals("EgIQCQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.TYPE_SHORTS));
        assertEquals("EgIYBA%3D%3D", SearchFilterHelper.toParams(SearchOptions.DURATION_UNDER_3));
        assertEquals("EgIYBQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.DURATION_BETWEEN_3_20));
        assertEquals("EgIYAg%3D%3D", SearchFilterHelper.toParams(SearchOptions.DURATION_OVER_20));
        assertEquals("EgIIAw%3D%3D", SearchFilterHelper.toParams(SearchOptions.UPLOAD_DATE_THIS_WEEK));
        assertEquals("EgJAAQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_LIVE));
        assertEquals("EgIgAQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_HD));
        assertEquals("EgIoAQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_SUBTITLES));
        assertEquals("EgIwAQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_CREATIVE_COMMONS));
        assertEquals("EgJ4AQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_360));
        assertEquals("EgPQAQE%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_VR180));
        assertEquals("EgI4AQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_3D));
        assertEquals("EgPIAQE%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_HDR));
        assertEquals("EgO4AQE%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_LOCATION));
        assertEquals("EgJIAQ%3D%3D", SearchFilterHelper.toParams(SearchOptions.FEATURE_PURCHASED));
        assertEquals("CAM%3D", SearchFilterHelper.toParams(SearchOptions.SORT_BY_VIEW_COUNT));
        // Combinations, as the old table had them.
        assertEquals("EgdAAXAByAEB", SearchFilterHelper.toParams(
                SearchOptions.FEATURE_LIVE | SearchOptions.FEATURE_4K | SearchOptions.FEATURE_HDR));
        assertEquals("CAMSBAgDGAI%3D", SearchFilterHelper.toParams(
                SearchOptions.UPLOAD_DATE_THIS_WEEK | SearchOptions.DURATION_OVER_20 | SearchOptions.SORT_BY_VIEW_COUNT));
    }
}
