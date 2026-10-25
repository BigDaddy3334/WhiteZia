package shop.whitezia.client.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun resolverCountLabel(
    count: Int,
    singular: String,
    plural: String,
): String {
    return "$count ${if (count == 1) singular else plural}"
}

internal data class ProfileTopAction(
    val label: String,
    val emphasized: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun ProfileTopActionGrid(actions: List<ProfileTopAction>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.sm),
    ) {
        actions.chunked(2).forEach { rowActions ->
            if (rowActions.size == 1) {
                val action = rowActions.first()
                ResolverActionButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    label = action.label,
                    emphasized = action.emphasized,
                    enabled = action.enabled,
                    onClick = action.onClick,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowActions.forEach { action ->
                        ResolverActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            label = action.label,
                            emphasized = action.emphasized,
                            enabled = action.enabled,
                            onClick = action.onClick,
                        )
                    }
                }
            }
        }
    }
}
