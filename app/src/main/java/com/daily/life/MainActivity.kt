package com.daily.life

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daily.life.core.designsystem.DailyTheme
import com.daily.life.core.navigation.DailyDestination
import com.daily.life.core.navigation.DailyNavHost
import com.daily.life.core.navigation.DailyRootState
import com.daily.life.core.navigation.navigateToPrimaryDestination

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DailyTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = DailyDestination.fromRoute(backStackEntry?.destination?.route)
                val rootState = DailyRootState(
                    currentDestination = currentDestination,
                    onDestinationSelected = { destination ->
                        navController.navigateToPrimaryDestination(destination)
                    }
                )

                DailyNavHost(
                    navController = navController,
                    rootState = rootState
                )
            }
        }
    }
}
