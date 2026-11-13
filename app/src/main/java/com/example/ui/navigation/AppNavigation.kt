package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings

enum class NavigationDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_item_home"
    ),
    BROWSE(
        route = "browse",
        title = "Browse",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        testTag = "nav_item_browse"
    ),
    CATEGORIES(
        route = "categories",
        title = "Categories",
        selectedIcon = Icons.Filled.Category,
        unselectedIcon = Icons.Outlined.Category,
        testTag = "nav_item_categories"
    ),
    TOOLS(
        route = "tools",
        title = "Tools",
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_item_tools"
    ),
    SETTINGS(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_item_settings"
    );

    fun localizedTitle(strings: AppStrings): String = when (this) {
        HOME -> strings.navHome
        BROWSE -> strings.navBrowse
        CATEGORIES -> strings.navCategories
        TOOLS -> strings.navTools
        SETTINGS -> strings.navSettings
    }
}

@Composable
fun AppBottomNavigationBar(
    currentDestination: NavigationDestination,
    onNavigateToDestination: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    NavigationBar(
        modifier = modifier.testTag("main_bottom_nav_bar")
    ) {
        NavigationDestination.entries.forEach { destination ->
            val isSelected = currentDestination == destination
            val label = destination.localizedTitle(strings)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag(destination.testTag)
            )
        }
    }
}

@Composable
fun AppNavigationRail(
    currentDestination: NavigationDestination,
    onNavigateToDestination: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    NavigationRail(
        modifier = modifier.testTag("main_navigation_rail")
    ) {
        NavigationDestination.entries.forEach { destination ->
            val isSelected = currentDestination == destination
            val label = destination.localizedTitle(strings)
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("${destination.testTag}_rail")
            )
        }
    }
}
