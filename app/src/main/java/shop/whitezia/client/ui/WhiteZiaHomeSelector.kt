package shop.whitezia.client.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.R
import java.util.Locale

internal enum class HomeSelectorType {
    CONNECTION,
    RESOLVER,
    ADVANCED,
}

internal data class HomeSelectorItem(
    val id: String,
    val title: String,
    val detail: String,
)

@Composable
internal fun HomeSelectorCard(
    label: String,
    value: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
    editEnabled: Boolean = enabled,
) {
    val haptic = rememberHapticFeedback()
    val borderColor = when {
        !enabled -> WhiteZiaPalette.Divider
        selected -> WhiteZiaPalette.Accent.copy(alpha = 0.28f)
        else -> WhiteZiaPalette.Border
    }
    val textColor = if (enabled) WhiteZiaPalette.Ink else WhiteZiaPalette.Disabled
    val detailColor = when {
        !enabled -> WhiteZiaPalette.Disabled
        selected -> WhiteZiaPalette.AccentText
        else -> WhiteZiaPalette.Muted
    }

    Column(modifier = modifier) {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) WhiteZiaPalette.Surface else WhiteZiaPalette.SurfaceAlt)
                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) {
                    haptic.performLight()
                    onClick()
                }
                .padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                Text(
                    text = detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = detailColor,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (onEditClick != null) {
                ProfileIconButton(
                    icon = Icons.Rounded.Edit,
                    contentDescription = "${WhiteZiaL10n.cdEditPrefix} $label",
                    emphasized = false,
                    enabled = editEnabled,
                    onClick = onEditClick,
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_dropdown_advanced_settings),
                tint = if (enabled) WhiteZiaPalette.Muted else WhiteZiaPalette.Disabled,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun HomeSelectorSheet(
    visible: Boolean,
    title: String,
    searchPlaceholder: String,
    items: List<HomeSelectorItem>,
    selectedId: String?,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredItems = remember(items, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            items
        } else {
            val lowerQuery = normalizedQuery.lowercase(Locale.getDefault())
            items.filter { item ->
                item.title.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                    item.detail.lowercase(Locale.getDefault()).contains(lowerQuery)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialOffsetX = { -it },
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutHorizontally(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            targetOffsetX = { it },
        ) + fadeOut(animationSpec = tween(160)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .whiteZiaPageBackground()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = WhiteZiaL10n.cdCloseSelector,
                        emphasized = false,
                        enabled = true,
                        onClick = onDismiss,
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = WhiteZiaPalette.Ink,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.lg))
                WhiteZiaTextField(
                    label = WhiteZiaL10n.settingSearch,
                    value = query,
                    onValueChange = { query = it },
                    placeholder = searchPlaceholder,
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (filteredItems.isEmpty()) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = WhiteZiaPalette.Muted,
                            ),
                        )
                    } else {
                        filteredItems.forEach { item ->
                            HomeSelectorSheetRow(
                                item = item,
                                selected = item.id == selectedId,
                                onClick = { onSelect(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSelectorSheetRow(
    item: HomeSelectorItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val profileDescription = stringResource(R.string.cd_select_profile_item, item.title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) WhiteZiaPalette.AccentSurface else WhiteZiaPalette.Surface)
            .border(
                1.5.dp,
                if (selected) WhiteZiaPalette.Accent.copy(alpha = 0.28f) else WhiteZiaPalette.Border,
                RoundedCornerShape(12.dp),
            )
            .semantics {
                contentDescription = profileDescription
            }
            .clickable {
                haptic.performLight()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = item.detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = if (selected) WhiteZiaPalette.AccentText else WhiteZiaPalette.Muted,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = WhiteZiaL10n.cdSelected,
                tint = WhiteZiaPalette.AccentText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
