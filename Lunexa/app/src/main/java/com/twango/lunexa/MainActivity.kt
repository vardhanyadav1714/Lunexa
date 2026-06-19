package com.twango.lunexa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.twango.lunexa.feature.auth.presentation.AuthRoute
import com.twango.lunexa.feature.home.presentation.HomeRoute
import com.twango.lunexa.ui.theme.LunexaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val accessToken by appViewModel.accessToken.collectAsState()
            val navController = rememberNavController()

            LunexaTheme {
                LunexaApp(
                    navController = navController,
                    isAuthenticated = !accessToken.isNullOrBlank()
                )
            }
        }
    }
}

@Composable
private fun LunexaApp(
    navController: NavHostController,
    isAuthenticated: Boolean
) {
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            navController.navigate(Route.Home.path) {
                popUpTo(Route.Auth.path) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated) Route.Home.path else Route.Auth.path
    ) {
        composable(Route.Auth.path) {
            AuthRoute(
                onAuthenticated = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Auth.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Route.Home.path) {
            HomeRoute(
                onLoggedOut = {
                    navController.navigate(Route.Auth.path) {
                        popUpTo(Route.Home.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private enum class Route(val path: String) {
    Auth("auth"),
    Home("home")
}

@Preview(showBackground = true)
@Composable
fun LunexaPreview() {
    LunexaTheme {
        LunexaApp(
            navController = rememberNavController(),
            isAuthenticated = false
        )
    }
}
