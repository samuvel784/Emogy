package com.vurka.emogy.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.ui.navigation.Screen
import com.vurka.emogy.ui.navigation.TopLevelDestinations
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.TextMuted

/**
 * One bottom bar for the five top-level destinations. Secondary screens (a game, a store, settings)
 * deliberately hide it and use their own back arrow instead.
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    hasTaskClaims: Boolean = false,
    hasAlbumClaims: Boolean = false,
    onSelect: (String) -> Unit,
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val unselectedColor = if (isLight) Color(0xFF64748B) else TextMuted

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        TopLevelDestinations.tabs.forEach { tab ->
            val hasBadge = when (tab.route) {
                Screen.Daily.route -> hasTaskClaims
                Screen.Album.route -> hasAlbumClaims
                else -> false
            }

            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { if (currentRoute != tab.route) onSelect(tab.route) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (hasBadge) {
                                Text(
                                    text = "🪙",
                                    fontSize = 11.sp,
                                    modifier = Modifier.offset(x = 4.dp, y = (-2).dp)
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.testTag(tab.testTag),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GoldStar,
                    selectedTextColor = GoldStar,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    unselectedIconColor = unselectedColor,
                    unselectedTextColor = unselectedColor
                )
            )
        }
    }
}
