package shop.whitezia.client.ui

import android.view.HapticFeedbackConstants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView

@Composable
internal fun rememberHapticFeedback(): HapticFeedback {
    val view = LocalView.current
    return remember {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                when (hapticFeedbackType) {
                    HapticFeedbackType.LongPress -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    HapticFeedbackType.TextHandleMove -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }
}

/**
 * Performs a light haptic feedback.
 * Use for: tab switches, toggle interactions, minor actions.
 */
internal fun HapticFeedback.performLight() {
    performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

/**
 * Performs a medium haptic feedback.
 * Use for: button clicks, profile actions, important interactions.
 */
internal fun HapticFeedback.performMedium() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

/**
 * Performs a heavy haptic feedback.
 * Use for: drag operations, destructive actions.
 */
internal fun HapticFeedback.performHeavy() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}
