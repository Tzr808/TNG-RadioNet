package com.armanmaurya.internetradio.custom.artwork

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.provider.StationAvatarProvider
import com.armanmaurya.internetradio.domain.model.RadioStation

/** `content://` URI of the generated avatar (usable by Compose, Coil, Glance, Media3). */
fun stationAvatarUri(context: Context, name: String, key: String): String =
    StationAvatarProvider.createUri(context, name, key)

/**
 * Single source of truth for station artwork outside Compose:
 * the user-provided favicon when present, the generated avatar otherwise.
 */
fun stationArtworkUrl(context: Context, station: RadioStation): String =
    station.favicon.takeIf { it.isNotBlank() }
        ?: stationAvatarUri(context, station.name, station.stationUuid)

/**
 * Station thumbnail used by every list/grid.
 *
 * - favicon present -> remote image, avatar shown while loading and on error
 * - favicon missing -> generated avatar immediately (never the app logo)
 * - thumbnails disabled in settings -> plain launcher icon, as before
 */
@Composable
fun StationArtworkImage(
    station: RadioStation,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    showThumbnails: Boolean = true,
) {
    StationArtworkImage(
        name = station.name,
        favicon = station.favicon,
        artworkKey = station.stationUuid,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        showThumbnails = showThumbnails
    )
}

/** Same as above for callers that only have a station name and favicon (e.g. schedules). */
@Composable
fun StationArtworkImage(
    name: String,
    favicon: String,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    showThumbnails: Boolean = true,
    artworkKey: String = name,
) {
    val context = LocalContext.current
    val avatar = remember(artworkKey, name) { StationAvatarPainter(name, artworkKey) }
    val background = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)

    if (!showThumbnails) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            contentScale = contentScale,
            modifier = modifier.then(background)
        )
        return
    }

    if (favicon.isBlank()) {
        Image(
            painter = avatar,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.then(background)
        )
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context).data(favicon).build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        placeholder = avatar,
        error = avatar,
        fallback = avatar,
        modifier = modifier.then(background)
    )
}
