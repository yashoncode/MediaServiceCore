package com.liskovsoft.youtubeapi.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebSessionTest {
    private val origin = "https://www.youtube.com"

    @Test fun hashesAllThreeCookiesWithOneTimestamp() {
        // Fixed SHA-1 vectors over UTF-8 "1700000000 <secret> https://www.youtube.com".
        // Expectations are independent of the production hash/formatting code.
        assertEquals(
            "SAPISIDHASH 1700000000_8826f35e8fadc232ceb4c889bdb7a3a586eb7379" +
                " SAPISID1PHASH 1700000000_01ca8712a198105a3f0999f890c24137388b7fff" +
                " SAPISID3PHASH 1700000000_7f5cf0ef7325a890a89cc0dc432ce24c7bae42dc",
            WebSession.authorization("OTHER=ignored; SAPISID=secret; __Secure-1PAPISID=one; __Secure-3PAPISID=three",
                1700000000L, origin))
    }

    @Test fun fallsBackToThirdPartyCookie() {
        assertEquals(
            "SAPISIDHASH 1700000000_7f5cf0ef7325a890a89cc0dc432ce24c7bae42dc" +
                " SAPISID3PHASH 1700000000_7f5cf0ef7325a890a89cc0dc432ce24c7bae42dc",
            WebSession.authorization("__Secure-3PAPISID=three", 1700000000L, origin))
    }

    @Test fun needsSapisidOrThirdPartyCookie() {
        assertNull(WebSession.authorization(null, 1700000000L, origin))
        assertNull(WebSession.authorization("PREF=hl=en; SAPISID=; __Secure-1PAPISID=one", 1700000000L, origin))
    }
}
