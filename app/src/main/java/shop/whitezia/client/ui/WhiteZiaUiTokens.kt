package shop.whitezia.client.ui

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Static tokens retained from the current connection screen while its UI is split into modules.
internal val WhiteZiaBackground = Color(0xFF0F0F14)
internal val WhiteZiaPanel = Color(0xFF16161F)
internal val WhiteZiaBlue = Color(0xFF5B6AF0)
internal val WhiteZiaRed = Color(0xFFE53935)
internal val WhiteZiaSuccess = Color(0xFF00C9A7)
internal val WhiteZiaError = Color(0xFFFF4D4D)
internal val WhiteZiaSetupOrange = Color(0xFFFFA726)
internal val WhiteZiaTextMuted = Color.White.copy(alpha = 0.55f)
internal val WhiteZiaTextDim = Color.White.copy(alpha = 0.22f)

internal fun WhiteZiaSmallTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 3.sp,
    )
}

@Composable
internal fun whiteZiaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White.copy(alpha = 0.88f),
    unfocusedTextColor = Color.White.copy(alpha = 0.78f),
    disabledTextColor = Color.White.copy(alpha = 0.34f),
    focusedLabelColor = WhiteZiaBlue,
    unfocusedLabelColor = WhiteZiaTextMuted,
    disabledLabelColor = WhiteZiaTextDim,
    focusedBorderColor = WhiteZiaBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.45f),
    disabledBorderColor = Color.White.copy(alpha = 0.18f),
    cursorColor = WhiteZiaBlue,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedPlaceholderColor = WhiteZiaTextDim,
    unfocusedPlaceholderColor = WhiteZiaTextDim,
    disabledPlaceholderColor = WhiteZiaTextDim,
)
