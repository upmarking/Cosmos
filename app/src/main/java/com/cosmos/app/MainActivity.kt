package com.cosmos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
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
                val currentRoute = navBackStackEntry?.destination?.route

                // Check if user is already cached in Firebase Auth on startup
                val isUserLoggedIn = com.cosmos.app.data.repository.ServiceLocator.authRepository.currentUserId != null
                val startDestination = if (isUserLoggedIn) Screen.Connect.route else Screen.Welcome.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        CosmosBottomNavBar(navController = navController)
                    },
                    containerColor = com.cosmos.app.ui.theme.CosmosBackground
                ) { innerPadding ->
                    CosmosNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
