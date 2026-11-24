package com.armanmaurya.internetradio.core.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.armanmaurya.internetradio.custom.artwork.StationAvatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Serves generated station avatars as PNG through a `content://` URI.
 *
 * Some consumers only accept a URI (Media3 artwork, notification, Glance widget), so the
 * deterministic Compose painter is not enough for them.
 *
 * The provider is deliberately **not exported**: only the app itself (and Media3, which runs
 * in-process) can read it. Unlike [SvgProxyProvider] it never performs network requests, so it
 * cannot be abused as a proxy.
 */
class StationAvatarProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val payload = uri.lastPathSegment ?: return null
        val decoded = try {
            String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val separator = decoded.indexOf('\n')
        if (separator <= 0) return null

        val name = decoded.substring(0, separator).take(MAX_LENGTH).trim()
        val key = decoded.substring(separator + 1).take(MAX_LENGTH)
        if (name.isBlank()) return null

        val pipe = ParcelFileDescriptor.createReliablePipe()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = StationAvatar.bitmap(name, key, BITMAP_SIZE_PX)
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
            } catch (e: Exception) {
                runCatching { pipe[1].closeWithError(e.message) }
            }
        }
        return pipe[0]
    }

    override fun getType(uri: Uri): String = "image/png"

    // Unused: read-only provider.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val AUTHORITY_SUFFIX = ".stationavatar"
        private const val BITMAP_SIZE_PX = 512
        private const val MAX_LENGTH = 120

        fun createUri(context: Context, name: String, key: String): String {
            val safeName = name.replace('\n', ' ').take(MAX_LENGTH)
            val safeKey = key.replace('\n', ' ').take(MAX_LENGTH)
            val payload = "$safeName\n$safeKey"
            val encoded = Base64.encodeToString(
                payload.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            return "content://${context.packageName}$AUTHORITY_SUFFIX/$encoded"
        }
    }
}
