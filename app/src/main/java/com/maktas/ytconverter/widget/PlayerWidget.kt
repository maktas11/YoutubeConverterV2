package com.maktas.ytconverter.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Image
import androidx.glance.ColorFilter
import com.maktas.ytconverter.MainActivity
import com.maktas.ytconverter.R
import com.maktas.ytconverter.data.CoverArtRepository
import com.maktas.ytconverter.music.PlaybackQueueState
import com.maktas.ytconverter.music.PlaybackService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }

    @Composable
    private fun Content(context: Context) {
        val state by PlaybackQueueState.state.collectAsState()
        val current = state.items.getOrNull(state.currentPosition)
        val isPlaying = state.isPlaying

        val artwork by produceState<Bitmap?>(
            initialValue = null,
            current?.artworkUri,
            current?.title,
        ) {
            value = withContext(Dispatchers.IO) {
                val title = current?.title
                val customCover = title
                    ?.let { CoverArtRepository.getFile(context, it) }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                customCover ?: current?.artworkUri?.let { uriStr ->
                    runCatching {
                        context.contentResolver.loadThumbnail(Uri.parse(uriStr), Size(256, 256), null)
                    }.getOrNull()
                }
            }
        }
        val art = artwork
        val hasArt = art != null
        val onArtColor = if (hasArt) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onArtSecondaryColor =
            if (hasArt) ColorProvider(Color.White.copy(alpha = 0.85f)) else GlanceTheme.colors.secondary

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground),
            ) {
                if (art != null) {
                    Image(
                        provider = ImageProvider(art),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                    // Scrim so text/controls stay legible over whatever color the art is.
                    Box(modifier = GlanceModifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {}
                }
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Title + artist — takes all remaining space, whole area opens the app
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(actionStartActivity<MainActivity>()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = current?.title ?: "Echo Music",
                            style = TextStyle(
                                color = onArtColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                        if (!current?.artist.isNullOrBlank()) {
                            Text(
                                text = current?.artist ?: "",
                                style = TextStyle(
                                    color = onArtSecondaryColor,
                                    fontSize = 11.sp,
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    // Playback controls
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_skip_previous),
                        contentDescription = "Previous",
                        colorFilter = ColorFilter.tint(onArtColor),
                        modifier = GlanceModifier
                            .size(36.dp)
                            .clickable(actionRunCallback<PreviousAction>()),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Image(
                        provider = ImageProvider(
                            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        colorFilter = ColorFilter.tint(if (hasArt) onArtColor else GlanceTheme.colors.primary),
                        modifier = GlanceModifier
                            .size(42.dp)
                            .clickable(actionRunCallback<PlayPauseAction>()),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_skip_next),
                        contentDescription = "Next",
                        colorFilter = ColorFilter.tint(onArtColor),
                        modifier = GlanceModifier
                            .size(36.dp)
                            .clickable(actionRunCallback<NextAction>()),
                    )
                }
            }
        }
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.startService(
            Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_TOGGLE_PLAY_PAUSE
            }
        )
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.startService(
            Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SKIP_PREVIOUS
            }
        )
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.startService(
            Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SKIP_NEXT
            }
        )
    }
}
