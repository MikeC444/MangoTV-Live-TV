package com.mangotv.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.ui.theme.MangoBrandGradient
import com.mangotv.app.ui.theme.TextPrimary

@Composable
fun MangoLogo(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "MANGO",
            style = TextStyle(
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "TV",
            style = TextStyle(
                brush = MangoBrandGradient,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                letterSpacing = 0.5.sp
            )
        )
    }
}
