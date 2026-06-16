package com.cosmos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cosmos.app.navigation.CosmosNavHost
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.components.CosmosBottomNavBar
import com.cosmos.app.ui.theme.CosmosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.cosmos.app.data.repository.LocalStore.initialize(this)
        enableEdgeToEdge()
        setContent {
            CosmosTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()

                // Check if user is logged in and has completed onboarding on startup
                val authRepo = com.cosmos.app.data.repository.ServiceLocator.authRepository
                val cachedUser = authRepo.currentUserId?.let { uid ->
                    com.cosmos.app.data.repository.LocalStore.users[uid]
                }
                val isUserLoggedInAndComplete = cachedUser != null && cachedUser.isProfileComplete
                val startDestination = if (isUserLoggedInAndComplete) Screen.Connect.route else Screen.Welcome.route

                // Use a Box overlay so the pill floats above content (true Instagram style)
                Scaffold(
                    modifier       = Modifier.fillMaxSize(),
                    containerColor = com.cosmos.app.ui.theme.CosmosBackground
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Screen content — full size, behind the pill
                        CosmosNavHost(
                            navController    = navController,
                            startDestination = startDestination,
                            modifier         = Modifier.fillMaxSize()
                        )

                        // Glassmorphic pill floats at the bottom of the Box
                        Box(
                            modifier        = Modifier.align(Alignment.BottomCenter),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            CosmosBottomNavBar(navController = navController)
                        }
                    }
                }
            }
        }
    }
}
