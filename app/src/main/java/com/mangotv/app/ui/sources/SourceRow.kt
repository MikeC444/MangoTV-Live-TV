package com.mangotv.app.ui.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.model.QualityTier
import com.mangotv.app.data.model.ResolutionTier
import com.mangotv.app.data.model.Stream
import com.mangotv.app.ui.components.GlowPlayBadge
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoAzure
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.MangoTeal
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

@Composable
fun SourceRow(
    stream: Stream,
    isRecommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val shape = RoundedCornerShape(MangoDimens.CardCornerRadius)

    Box(modifier = modifier.fillMaxWidth()) {
        TvFocusSurface(
            onClick = onClick,
            shape = shape,
            backgroundColor = MangoSurfaceHigh,
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focusRequester,
            // Drawn by TvFocusSurface itself (inside its focus-scale
            // transform) rather than as a border on the modifier above, so
            // the amber outline scales up together with the card instead
            // of staying a fixed size while the card grows around it.
            alwaysShowBorder = isRecommended,
            borderColor = if (isRecommended) MangoAmber else FocusBorder,
            bringIntoViewOnFocus = false
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QualityBadge(stream)
                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.releaseTitle,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = listOfNotNull(stream.codec, stream.sourceTag, stream.audioTag)
                        .joinToString("  •  ")
                    if (subtitle.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (stream.seedersLabel != null || stream.qualityTier != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            stream.seedersLabel?.let {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.width(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "$it seeders",
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            stream.qualityTier?.let { tier ->
                                val tierColor = when (tier) {
                                    QualityTier.VERY_HIGH, QualityTier.HIGH -> MangoTeal
                                    QualityTier.GOOD -> MangoAzure
                                    QualityTier.LOW -> TextTertiary
                                }
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = tierColor,
                                    modifier = Modifier.width(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = tier.label,
                                    color = tierColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.widthIn(max = 72.dp)
                ) {
                    stream.sizeLabel?.let {
                        Text(
                            text = it,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.width(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stream.providerLabel,
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                GlowPlayBadge(size = 28.dp, glowSize = 38.dp, iconSize = 13.dp)
            }
        }

        if (isRecommended) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = (-10).dp)
                    .background(MangoAmber, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.width(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Recommended",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun QualityBadge(stream: Stream) {
    val color = when (stream.resolutionTier) {
        ResolutionTier.UHD_4K -> MangoAmber
        ResolutionTier.FHD_1080P -> MangoAzure
        ResolutionTier.HD_720P -> MangoTeal
        ResolutionTier.OTHER -> TextTertiary
    }
    val badgeShape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .border(BorderStroke(1.dp, color), badgeShape)
            .background(MangoBackground, badgeShape)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stream.qualityBadge,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        stream.sourceTag?.let {
            Text(
                text = it,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
