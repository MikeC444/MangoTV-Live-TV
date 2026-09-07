package com.mangotv.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoBrandGradient
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.TextPrimary

enum class MangoButtonStyle { FILLED, GLASS, LIGHT }

@Composable
fun MangoButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MangoButtonStyle = MangoButtonStyle.GLASS,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null,
    bringIntoViewOnFocus: Boolean = true,
    // Used by the movie detail page to fit its whole layout on one screen
    // without scrolling — every other caller leaves this false, so their
    // buttons are completely unaffected.
    compact: Boolean = false,
    borderColor: Color = FocusBorder
) {
    val contentColor = when (style) {
        MangoButtonStyle.FILLED -> MangoBackground
        MangoButtonStyle.LIGHT -> Color.Black
        MangoButtonStyle.GLASS -> TextPrimary
    }
    val buttonHeight = if (compact) 40.dp else 52.dp
    val horizontalPadding = if (compact) 16.dp else 26.dp
    val iconHeight = if (compact) 16.dp else 22.dp
    val iconTextSpacing = if (compact) 6.dp else 10.dp
    val textStyle = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge

    // LIGHT is a full pill (used for the detail page's restyled Play
    // button); FILLED/GLASS keep the app's standard corner radius, used
    // everywhere else (Home's hero, Settings) — this doesn't change their
    // existing shape.
    val shape = if (style == MangoButtonStyle.LIGHT) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(MangoDimens.ButtonCornerRadius)
    }

    TvFocusSurface(
        onClick = onClick,
        modifier = modifier.height(buttonHeight),
        shape = shape,
        backgroundColor = when (style) {
            MangoButtonStyle.GLASS -> Color.White.copy(alpha = 0.12f)
            MangoButtonStyle.LIGHT -> Color.White
            MangoButtonStyle.FILLED -> Color.Transparent
        },
        backgroundBrush = if (style == MangoButtonStyle.FILLED) MangoBrandGradient else null,
        focusRequester = focusRequester,
        focusUp = focusUp,
        focusDown = focusDown,
        borderColor = borderColor,
        bringIntoViewOnFocus = bringIntoViewOnFocus
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.height(iconHeight)
            )
            Spacer(modifier = Modifier.width(iconTextSpacing))
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                style = textStyle
            )
        }
    }
}
