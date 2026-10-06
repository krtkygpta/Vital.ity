package com.healthcompanion.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.healthcompanion.app.ui.theme.Elevation
import com.healthcompanion.app.ui.theme.ExpressiveMotion
import com.healthcompanion.app.ui.theme.LocalHealthColors

/**
 * Material 3 Expressive card component.
 *
 * Provides tactile press scaling, ripple feedback when clickable, and
 * standard M3 token styling (extraLarge 24dp shape, cardBackground color).
 */
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    enabled: Boolean = true,
    animateIn: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val healthColors = LocalHealthColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && onClick != null) 0.97f else 1f,
        animationSpec = ExpressiveMotion.bouncySpring(),
        label = "cardPress"
    )

    val currentElevation = if (pressed && onClick != null) Elevation.Level2 else Elevation.Level1

    val cardModifier = modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.semantics { role = Role.Button }
            } else {
                Modifier
            }
        )

    val cardContent: @Composable () -> Unit = {
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = cardModifier,
                enabled = enabled,
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = healthColors.cardBackground,
                    contentColor = healthColors.textPrimary
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = currentElevation,
                    pressedElevation = Elevation.Level2
                ),
                interactionSource = interactionSource
            ) {
                Column { content() }
            }
        } else {
            Card(
                modifier = cardModifier,
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = healthColors.cardBackground,
                    contentColor = healthColors.textPrimary
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = Elevation.Level1
                )
            ) {
                Column { content() }
            }
        }
    }

    if (animateIn) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(ExpressiveMotion.emphasizedDecelerateSpec()) +
                scaleIn(ExpressiveMotion.bouncySpring(), initialScale = 0.94f),
            exit = fadeOut(tween(ExpressiveMotion.DURATION_SHORT)) +
                scaleOut(tween(ExpressiveMotion.DURATION_SHORT), targetScale = 0.96f)
        ) {
            cardContent()
        }
    } else {
        cardContent()
    }
}
