package shop.whitezia.client.ui

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val WhiteZiaBackground: Color @Composable get() = WhiteZiaPalette.Background
internal val WhiteZiaPanel: Color @Composable get() = WhiteZiaPalette.Surface
internal val WhiteZiaBlue: Color @Composable get() = WhiteZiaPalette.AccentText
internal val WhiteZiaRed: Color @Composable get() = WhiteZiaPalette.Error
internal val WhiteZiaSuccess: Color @Composable get() = WhiteZiaPalette.Success
internal val WhiteZiaError: Color @Composable get() = WhiteZiaPalette.Error
internal val WhiteZiaSetupOrange: Color @Composable get() = WhiteZiaPalette.WarningText
internal val WhiteZiaTextMuted: Color @Composable get() = WhiteZiaPalette.Muted
internal val WhiteZiaTextDim: Color @Composable get() = WhiteZiaPalette.Pale
internal val WhiteZiaInk: Color @Composable get() = WhiteZiaPalette.Ink

internal fun WhiteZiaSmallTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
}

@Composable
internal fun whiteZiaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WhiteZiaInk,
    unfocusedTextColor = WhiteZiaInk,
    disabledTextColor = WhiteZiaTextMuted.copy(alpha = 0.6f),
    focusedLabelColor = WhiteZiaBlue,
    unfocusedLabelColor = WhiteZiaTextMuted,
    disabledLabelColor = WhiteZiaTextDim,
    focusedBorderColor = WhiteZiaBlue,
    unfocusedBorderColor = WhiteZiaPalette.ControlBorder,
    disabledBorderColor = WhiteZiaPalette.Border,
    cursorColor = WhiteZiaBlue,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedPlaceholderColor = WhiteZiaTextDim,
    unfocusedPlaceholderColor = WhiteZiaTextDim,
    disabledPlaceholderColor = WhiteZiaTextDim,
)
