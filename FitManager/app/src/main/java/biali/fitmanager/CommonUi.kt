package biali.fitmanager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.LightGreen80

@Composable
fun FitBottomNav(
    currentRoute: String,
    onNavigateToHome: () -> Unit,
    onNavigateToTrainers: () -> Unit,
    onNavigateToMemberships: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToAccount: () -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = onNavigateToHome,
            label = { Text("Panel") },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Panel") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = currentRoute == "trainers",
            onClick = onNavigateToTrainers,
            label = { Text("Trener") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Trener") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )

        NavigationBarItem(
            selected = currentRoute == "progress",
            onClick = onNavigateToProgress,
            label = { Text("Postęp") },
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Postęp") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = currentRoute == "memberships",
            onClick = onNavigateToMemberships,
            label = { Text("Karnety") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Karnety") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = currentRoute == "account",
            onClick = onNavigateToAccount,
            label = { Text("Konto") },
            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Konto") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
    }
}