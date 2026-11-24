package com.armanmaurya.internetradio.data.remote.artwork

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationArtworkEnrichersTest {

    private val base = "https://radio.example.com/".toHttpUrl()

    @Test
    fun `normalize removes radio prefixes, accents and punctuation`() {
        assertEquals("italia", StationNameMatcher.normalize("Radio Italia"))
        assertEquals("deejay", StationNameMatcher.normalize("RADIO DEEJAY!"))
        assertEquals("citta", StationNameMatcher.normalize("Radio Città"))
        assertEquals("", StationNameMatcher.normalize("Radio FM"))
    }

    @Test
    fun `similarity accepts the same brand and rejects unrelated names`() {
        assertTrue(StationNameMatcher.isMatch("Radio Italia", "Italia"))
        assertTrue(StationNameMatcher.isMatch("Radio Deejay", "Deejay"))
        assertFalse(StationNameMatcher.isMatch("Radio Italia", "Radio Rock"))
        assertFalse(StationNameMatcher.isMatch("Radio", "Random Album"))
    }

    @Test
    fun `html parser prefers open graph and resolves relative urls`() {
        val html = """
            <html><head>
              <meta content="/img/cover.jpg" property="og:image">
              <link rel="apple-touch-icon" href="/apple.png">
            </head></html>
        """.trimIndent()

        assertEquals("https://radio.example.com/img/cover.jpg", parseArtworkFromHtml(html, base))
    }

    @Test
    fun `html parser falls back to apple touch icon`() {
        val html = """<link rel="apple-touch-icon" sizes="180x180" href="https://cdn.example.com/icon.png">"""
        assertEquals("https://cdn.example.com/icon.png", parseArtworkFromHtml(html, base))
    }

    @Test
    fun `html parser ignores javascript and data urls`() {
        val html = """<meta property="og:image" content="javascript:alert(1)">"""
        assertNull(parseArtworkFromHtml(html, base))
    }

    @Test
    fun `itunes artwork is upscaled to 600px`() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/x/600x600bb.jpg",
            upscaleItunesArtwork("https://is1-ssl.mzstatic.com/image/thumb/x/100x100bb.jpg")
        )
    }

    @Test
    fun `ssrf guard rejects loopback and local hosts`() {
        assertNull(NetworkUrlGuard.parse("http://127.0.0.1:8080/admin"))
        assertNull(NetworkUrlGuard.parse("http://localhost/logo.png"))
        assertNull(NetworkUrlGuard.parse("file:///etc/passwd"))
        assertNull(NetworkUrlGuard.parse("ftp://example.com/logo.png"))
    }
}
