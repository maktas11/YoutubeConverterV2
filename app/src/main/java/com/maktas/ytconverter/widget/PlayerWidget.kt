package com.maktas.ytconverter.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.layout.Column
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
import androidx.glance.Image
import androidx.glance.ColorFilter
import com.maktas.ytconverter.MainActivity
import com.maktas.ytconverter.R
import com.maktas.ytconverter.music.PlaybackQueueState
import com.maktas.ytconverter.music.PlaybackService
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PlayerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val state by PlaybackQueueState.state.collectAsState()
        val current = state.items.getOrNull(state.currentPosition)
        val isPlaying = state.isPlaying

        GlanceTheme {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
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
                            color = GlanceTheme.colors.onSurface,
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
                                color = GlanceTheme.colors.secondary,
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
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
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
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier
                        .size(42.dp)
                        .clickable(actionRunCallback<PlayPauseAction>()),
                )
                Spacer(GlanceModifier.width(4.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_skip_next),
                    contentDescription = "Next",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier
                        .size(36.dp)
                        .clickable(actionRunCallback<NextAction>()),
                )
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
