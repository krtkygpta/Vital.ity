package com.healthcompanion.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthcompanion.app.ui.theme.ExpressiveMotion
import com.healthcompanion.app.ui.theme.LocalHealthColors

/**
 * Material 3 Expressive connected button group.
 *
 * Connected pill geometry matching M3 tokens.
 */
@Composable
fun <T> M3EConnectedButtonGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = false
) {
    val healthColors = LocalHealthColors.current

    val rowContent: @Composable RowScope.() -> Unit = {
        items.forEach { item ->
            val isSelected = item == selectedItem

            val shape = if (isSelected) {
                MaterialTheme.shapes.extraLarge
            } else {
                MaterialTheme.shapes.small
            }

            val targetBg = if (isSelected) {
                healthColors.pillSelectedBg
            } else {
                healthColors.pillUnselectedBg
            }
            val targetText = if (isSelected) {
                healthColors.pillSelectedText
            } else {
                healthColors.pillUnselectedText
            }

            val animatedBg by animateColorAsState(
                targetValue = targetBg,
                animationSpec = ExpressiveMotion.emphasizedDecelerateSpec(),
                label = "btnGroupBg"
            )
            val animatedText by animateColorAsState(
                targetValue = targetText,
                animationSpec = ExpressiveMotion.emphasizedDecelerateSpec(),
                label = "btnGroupText"
            )

            val itemModifier = if (isScrollable) {
                Modifier
                    .defaultMinSize(minWidth = 56.dp)
                    .height(38.dp)
                    .clip(shape)
                    .background(animatedBg)
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 12.dp)
            } else {
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(shape)
                    .background(animatedBg)
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 4.dp)
            }

            Box(
                modifier = itemModifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    color = animatedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (isScrollable) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rowContent()
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rowContent()
        }
    }
}

/**
 * Material 3 Expressive connected list shape helper.
 */
fun m3eConnectedCardShape(
    index: Int,
    totalCount: Int,
    outerCorner: Dp = 16.dp,
    innerCorner: Dp = 4.dp
): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner
        )
        else -> RoundedCornerShape(innerCorner)
    }
}
