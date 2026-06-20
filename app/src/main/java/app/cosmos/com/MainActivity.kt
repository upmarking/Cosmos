package app.cosmos.com

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
import app.cosmos.com.navigation.CosmosNavHost
import app.cosmos.com.navigation.Screen
import app.cosmos.com.ui.components.CosmosBottomNavBar
import app.cosmos.com.ui.theme.CosmosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CosmosTheme {
                val navController = rememberNavController()

                val startDestination = Screen.Splash.route

                // Note: Navigation routing is handled by:
                // 1. startDestination (always starts at Splash screen to run the 3D intro)
                // 2. SplashScreen's onAnimationFinished (evaluates auth and redirects to Welcome or Connect)
                // 3. WelcomeScreen's LaunchedEffect (auto-redirect for complete profiles)

                // Use a Box overlay so the pill floats above content (true Instagram style)
                Scaffold(
                    modifier       = Modifier.fillMaxSize(),
                    containerColor = app.cosmos.com.ui.theme.CosmosBackground
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
