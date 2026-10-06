package com.healthcompanion.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.healthcompanion.app.ui.theme.LocalHealthColors

/**
 * Standard Material 3 Top App Bar for detail screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val healthColors = LocalHealthColors.current

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = healthColors.background,
            titleContentColor = healthColors.textPrimary,
            navigationIconContentColor = healthColors.textPrimary,
            actionIconContentColor = healthColors.textPrimary
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}
