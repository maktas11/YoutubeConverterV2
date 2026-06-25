package com.maktas.ytconverter.download

/**
 * Turns raw yt-dlp stderr into short, human-readable messages (build plan §8).
 * The app never blocks content — it just explains predictable failure modes.
 */
object ErrorMapper {

    private const val GENERIC = "Download failed — try updating yt-dlp in Settings."

    fun friendly(raw: String?): String {
        val m = raw?.lowercase() ?: return GENERIC
        return when {
            m.containsAny("confirm your age", "age-restricted", "age restricted", "inappropriate for some users") ->
                "Age-restricted — requires login (not supported in this version)."

            m.containsAny("private video", "members-only", "members only", "this channel's members", "join this channel") ->
                "You don't have access to this video."

            m.containsAny("in your country", "in your location", "geo restrict", "geo-restrict", "not available in your") ->
                "This video is blocked in your region."

            m.containsAny("live event will begin", "this live event", "is live and", "premieres in", "currently live") ->
                "Can't download an in-progress livestream."

            m.containsAny("sign in to confirm you", "confirm you're not a bot", "not a bot") ->
                "YouTube is asking for a sign-in / bot check — try updating yt-dlp in Settings."

            m.containsAny(
                "getaddrinfo", "name resolution", "network is unreachable", "no address associated",
                "timed out", "timeout", "connection refused", "connection reset",
                "temporary failure", "unable to connect"
            ) -> "Network error — check your connection and try again."

            m.containsAny("unable to extract", "unsupported url", "unable to download webpage", "nsig", "failed to extract") ->
                "Download failed — try updating yt-dlp in Settings."

            else -> GENERIC
        }
    }

    private fun String.containsAny(vararg keys: String) = keys.any { it in this }
}
