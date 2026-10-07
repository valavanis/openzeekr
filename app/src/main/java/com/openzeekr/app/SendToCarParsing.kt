package com.openzeekr.app

/**
 * Pure string parsing for [SendToCarActivity]'s incoming URI, so it works for OPAQUE URIs.
 *
 * `geo:` and `google.navigation:` are opaque (no `/` after the scheme): android.net.Uri.getQueryParameter
 * throws UnsupportedOperationException on them, and that crashed the app on every geo link. Reading the
 * raw string instead covers both opaque and hierarchical forms, and is unit-testable without Android.
 */
internal object SendToCarParsing {

    /**
     * Text candidates carried by a destination URI, in order: the `q` and `destination` query values,
     * then the decoded scheme-specific part (e.g. `37.77,-122.41?q=…` for a geo: URI). Never throws.
     */
    fun uriCandidates(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val ssp = raw.substringAfter(':', "").substringBefore('#')
        // geo:lat,lng?q=… carries its query after '?'; google.navigation:q=…&mode=d carries it directly.
        val query = when {
            '?' in ssp -> ssp.substringAfter('?')
            '=' in ssp -> ssp
            else -> ""
        }
        val params = query.split('&').mapNotNull { pair ->
            val k = pair.substringBefore('=', "")
            if (k.isEmpty()) null else k to decode(pair.substringAfter('='))
        }
        return buildList {
            params.firstOrNull { it.first == "q" }?.let { add(it.second) }
            params.firstOrNull { it.first == "destination" }?.let { add(it.second) }
            if (ssp.isNotBlank()) add(decode(ssp))
        }
    }

    /** URL-decode, keeping the raw text when it isn't valid percent-encoding. */
    private fun decode(s: String): String =
        runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
