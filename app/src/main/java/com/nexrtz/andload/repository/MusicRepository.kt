package com.nexrtz.andload.repository

import android.app.DownloadManager
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.nexrtz.andload.model.RemoteMusicTrack
import com.nexrtz.andload.model.Song
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MusicRepository(private val context: Context) {

    companion object {
        private const val SERVER_BASE_URL = "http://172.16.19.200:5000"
    }

    fun fetchLocalSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val query = context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                songs.add(Song(id, title, artist, album, duration, contentUri, artUri))
            }
        }
        return songs
    }

    fun deleteSong(song: Song): Boolean {
        return context.contentResolver.delete(song.contentUri, null, null) > 0
    }

    fun searchRemoteTracks(query: String): List<RemoteMusicTrack> {
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val connection = URL(
            "$SERVER_BASE_URL/api/v1/music/search?q=$encodedQuery"
        ).openConnection() as HttpURLConnection

        return connection.useConnection {
            if (responseCode !in 200..299) {
                error("Music search failed with HTTP $responseCode")
            }
            val root = JSONObject(inputStream.bufferedReader().use { it.readText() })
            val results = root.optJSONArray("results") ?: return@useConnection emptyList()
            buildList {
                for (resultIndex in 0 until results.length()) {
                    val result = results.getJSONObject(resultIndex)
                    val files = result.optJSONArray("files") ?: continue
                    for (fileIndex in 0 until files.length()) {
                        val file = files.getJSONObject(fileIndex)
                        add(
                            RemoteMusicTrack(
                                identifier = result.optString("identifier"),
                                title = result.optString("title", "Untitled"),
                                artist = result.optString("artist", "Unknown artist"),
                                year = result.optString("year").takeIf { it.isNotBlank() },
                                licenseUrl = result.optString("license_url").takeIf { it.isNotBlank() },
                                filename = file.optString("name"),
                                format = file.optString("format"),
                                downloadUrl = file.optString("download_url")
                            )
                        )
                    }
                }
            }
        }
    }

    fun enqueueDownload(track: RemoteMusicTrack): Long {
        val request = DownloadManager.Request(Uri.parse(track.downloadUrl))
            .setTitle(track.title)
            .setDescription("Downloading " + track.artist)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MUSIC,
                "NeuralPlayer/${track.filename}"
            )

        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }

    private fun <T> HttpURLConnection.useConnection(block: HttpURLConnection.() -> T): T {
        connectTimeout = 10_000
        readTimeout = 20_000
        requestMethod = "GET"
        return try {
            block()
        } finally {
            disconnect()
        }
    }
}
