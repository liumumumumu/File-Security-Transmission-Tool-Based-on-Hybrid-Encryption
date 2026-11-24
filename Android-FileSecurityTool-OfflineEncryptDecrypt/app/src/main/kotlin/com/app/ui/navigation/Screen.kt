package com.filesecuritytool.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.filesecuritytool.android.R

sealed class Screen(
    val route: String,
    val titleResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Crypto : Screen(
        "crypto", R.string.nav_crypto,
        Icons.Filled.Lock, Icons.Outlined.Lock
    )
    data object Contacts : Screen(
        "contacts", R.string.nav_contacts,
        Icons.Filled.Contacts, Icons.Outlined.Contacts
    )
    data object Keys : Screen(
        "keys", R.string.nav_keys,
        Icons.Filled.Key, Icons.Outlined.Key
    )
    data object Settings : Screen(
        "settings", R.string.nav_settings,
        Icons.Filled.Settings, Icons.Outlined.Settings
    )

    companion object {
        val bottomNavItems = listOf(Crypto, Contacts, Keys, Settings)
    }
}
