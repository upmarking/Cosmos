package com.cosmos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.cosmos.app.navigation.CosmosNavHost
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.components.CosmosBottomNavBar
import com.cosmos.app.ui.theme.CosmosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CosmosTheme {
                val navController = rememberNavController()

                // Check if user is logged in on startup via Firebase Auth
                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val startDestination = if (firebaseUser != null) Screen.Connect.route else Screen.Welcome.route

                // Note: Navigation routing is handled by:
                // 1. startDestination (based on cached profile on app launch)
                // 2. WelcomeScreen's LaunchedEffect (auto-redirect for complete profiles)
                // 3. signIn/signUp callbacks (explicit user actions)

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
