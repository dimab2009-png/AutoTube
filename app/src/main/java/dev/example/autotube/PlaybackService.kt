package dev.example.autotube

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Аудио-библиотека для Android Auto: именно её показывает машина (видео на экране AA недоступно). */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaLibrarySession.Builder(this, player, Callback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(Library.root, params))

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(Library.items), params))

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = Library.items.find { it.mediaId == mediaId }
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { req -> Library.items.find { it.mediaId == req.mediaId } ?: req }.toMutableList()
            )
    }
}

/** Источники звука. Замените на свои (подкасты, ваши файлы, лицензированные потоки). */
object Library {
    val root: MediaItem = MediaItem.Builder()
        .setMediaId("root")
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle("AutoTube").setIsBrowsable(true).setIsPlayable(false).build()
        ).build()

    val items: List<MediaItem> = listOf(
        track("demo1", "Demo track 1", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
        track("demo2", "Demo track 2", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
    )

    private fun track(id: String, title: String, url: String) = MediaItem.Builder()
        .setMediaId(id).setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(false).setIsPlayable(true)
                .setExtras(Bundle.EMPTY).build()
        ).build()
}
