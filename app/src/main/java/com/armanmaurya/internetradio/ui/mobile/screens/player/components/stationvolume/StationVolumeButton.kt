package com.armanmaurya.internetradio.ui.mobile.screens.player.components.stationvolume

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.armanmaurya.internetradio.R

/** Chiave per il dialog volume-stazione. */
internal const val STATION_VOLUME_DIALOG_KEY = "station_volume_dialog"

/**
 * Pulsante toggle volume per-stazione: tap = salva il volume, long-press = cancella quello salvato.
 * Quando un volume è salvato mostra l'icona in evidenza e un arco verde di conferma.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StationVolumeButton(
    modifier: Modifier = Modifier,
    hasSavedVolume: Boolean,
    onToggleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val contentColor = if (hasSavedVolume) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val containerColor = if (hasSavedVolume) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .combinedClickable(onClick = onToggleClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasSavedVolume) {
                    Icons.AutoMirrored.Filled.VolumeUp
                } else {
                    Icons.AutoMirrored.Filled.VolumeDown
                },
                contentDescription = stringResource(R.string.player_cd_station_volume),
                tint = contentColor,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { scaleX = -1f }
            )
            if (hasSavedVolume) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    val arcRadius = size.width * 0.35f
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val startX = centerX - arcRadius
                    val startY = centerY - arcRadius
                    val path = Path().apply {
                        moveTo(startX + arcRadius, startY + arcRadius * 0.5f)
                        arcTo(
                            rect = Rect(startX, startY, startX + arcRadius * 2f, startY + arcRadius * 2f),
                            startAngleDegrees = -45f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                    }
                    drawPath(path = path, color = Color(0xFF4CAF50), style = Stroke(width = 2.5f))
                }
            }
        }
    }
}



/** Dispositivo audio scoperto (Cast, Bluetooth, ecc.). */
data class DiscoveredDevice(
    val id: String,
    val name: String,
    val type: DeviceType = DeviceType.UNKNOWN
)

enum class DeviceType {
    CAST,
    BLUETOOTH,
    SPEAKER,
    HEADPHONES,
    USB,
    DEFAULT,
    UNKNOWN
}

/** Dialog di regolazione volume per-stazione con selezione dispositivo. */
@Composable
fun StationVolumeDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeSave: (Float) -> Unit,
    onVolumeClear: () -> Unit,
    hasSavedVolume: Boolean,
    isCastActive: Boolean,
    connectedCastDeviceName: String?,
    discoveredDevices: List<DiscoveredDevice>,
    activeDeviceId: String?,
    stationName: String? = null
) {
    if (!showDialog) return

    AnimatedVisibility(
        visible = showDialog,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(250)),
        modifier = Modifier.zIndex(100f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Titolo
                Column(
                    modifier = Modifier.height(44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stationName ?: stringResource(R.string.player_volume),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.player_volume_station_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }

                // Slider volume
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 200.dp)
                    ) {
                        Slider(
                            value = currentVolume.coerceIn(0f, 1f),
                            onValueChange = onVolumeChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Valore volume + pulsante salva
                Row(
                    modifier = Modifier.height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(currentVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { onVolumeSave(currentVolume) },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.player_volume_save),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Sezione dispositivi
                if (discoveredDevices.isNotEmpty() || connectedCastDeviceName != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.player_volume_device_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )

                        // Dispositivo attivo
                        if (activeDeviceId != null) {
                            val activeDevice = discoveredDevices.find { it.id == activeDeviceId }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeDevice?.name ?: activeDeviceId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Dispositivi Cast scoperti
                        for (device in discoveredDevices) {
                            val isActive = device.id == activeDeviceId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = null,
                                    tint = if (isActive) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                    }
                                )
                            }
                        }

                        // Pulsante cancella
                        TextButton(
                            onClick = onVolumeClear,
                            enabled = hasSavedVolume,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (hasSavedVolume) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.player_volume_clear_label),
                                color = if (hasSavedVolume) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
