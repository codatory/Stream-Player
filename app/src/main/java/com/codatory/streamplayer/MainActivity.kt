package com.codatory.streamplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoopingMediaSource
import androidx.media3.ui.PlayerView
import com.codatory.streamplayer.ui.theme.StreamPlayerTheme
import kotlinx.coroutines.delay
import org.chromium.net.CronetEngine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreamPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                ) {
                    Player("http://192.168.1.251/hls/stream0.m3u8")
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun Player(url: String) {
    val context = LocalContext.current
    val currentView = LocalView.current
    var retryBackoffTime: Long = 2000
    val retryBackoffMultiply: Double = 1.25
    val retryBackoffMax: Long = 60000

    // Configure optimized networking stack (cronet if play services, or default if not)
    val cronetBuilder = CronetEngine.Builder(context)
    val cronetEngine: CronetEngine = cronetBuilder.build()
    val executor: Executor = Executors.newSingleThreadExecutor()
    val cronetDataSourceFactory = CronetDataSource.Factory(cronetEngine, executor)
    val dataSourceFactory =
        DefaultDataSource.Factory(context, /* baseDataSourceFactory= */ cronetDataSourceFactory)
    val exoPlayer = ExoPlayer.Builder(context).setMediaSourceFactory(
                    DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
                ).build()

    // re-start playback when interrupted
    exoPlayer.addListener( object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Thread.sleep(retryBackoffTime)
            val newBackoffTime: Long = (retryBackoffTime * retryBackoffMultiply).roundToLong()
            retryBackoffTime = minOf(newBackoffTime, retryBackoffMax)
            exoPlayer.prepare()
        }
    })

    // Configure media source
    val mediaSource = remember(url) {
        MediaItem.fromUri(url)
    }

    // Setup media player
    LaunchedEffect(mediaSource) {
        // configure media source
        exoPlayer.setMediaItem(mediaSource)
        // start playback when loading complete
        exoPlayer.playWhenReady = true
        // start loading player
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        // Keep screen on when focused
        currentView.keepScreenOn = true
        onDispose {
            // Stop player when app de-focused
            exoPlayer.release()
            // Allow normal screen function when app de-focused
            currentView.keepScreenOn = false
        }
    }

    // Use AndroidView to embed an Android View (PlayerView) into Compose
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
            }
        },
        modifier = Modifier
            .fillMaxSize()
    )
}