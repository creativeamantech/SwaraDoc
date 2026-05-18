# Jules Prompt — Surgical Fix: Blank Screen

> The app launches and shows ONLY the text "SwaraPulse App Scaffold" on a white screen.
> This is a hardcoded placeholder. The fix requires rewriting exactly 2 files.
> Do NOT make assumptions. Read each file first, then overwrite it completely.

---

## STEP 1 — READ AND REPORT

Before writing any code, read these files and print their FULL current content
so it is visible in your output:

1. `app/src/main/java/com/swarapulse/MainActivity.kt`
2. `app/src/main/java/com/swarapulse/presentation/navigation/NavHost.kt`
   (it may also be named `SwaraPulseNavHost.kt` — check both)

Also check:
3. `app/src/main/java/com/swarapulse/presentation/auth/AuthScreen.kt`
   — just the first 30 lines and the composable signature line

Print the content of each file before making any changes.

---

## STEP 2 — REWRITE `MainActivity.kt`

**Completely overwrite** `MainActivity.kt` with exactly this content.
Do not add anything. Do not remove anything. Copy verbatim:

```kotlin
package com.swarapulse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.swarapulse.presentation.navigation.SwaraPulseNavHost
import com.swarapulse.presentation.ui.theme.SwaraPulseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SwaraPulseTheme {
                SwaraPulseNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
```

---

## STEP 3 — REWRITE the NavHost file

Find the file that contains `SwaraPulseNavHost` — it may be:
- `presentation/navigation/NavHost.kt`
- `presentation/navigation/SwaraPulseNavHost.kt`

**Completely overwrite** it with exactly this content:

```kotlin
package com.swarapulse.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swarapulse.presentation.analytics.AnalyticsScreen
import com.swarapulse.presentation.appointments.AppointmentsScreen
import com.swarapulse.presentation.auth.AuthScreen
import com.swarapulse.presentation.dashboard.DashboardScreen
import com.swarapulse.presentation.patients.PatientDetailScreen
import com.swarapulse.presentation.patients.PatientListScreen
import com.swarapulse.presentation.settings.SettingsScreen
import com.swarapulse.presentation.visit.VisitFormScreen

@Composable
fun SwaraPulseNavHost() {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    val showBottomBar = currentRoute != null
        && currentRoute != "auth"
        && !currentRoute.startsWith("visit/")
        && currentRoute != "patients/new"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                SwaraPulseBottomBar(navController = navController)
            }
        }
    ) { innerPadding ->

        NavHost(
            navController    = navController,
            startDestination = "auth",
            enterTransition  = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) +
                fadeIn(animationSpec = tween(280))
            },
            exitTransition   = {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(280)) +
                fadeOut(animationSpec = tween(280))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(280)) +
                fadeIn(animationSpec = tween(280))
            },
            popExitTransition  = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280)) +
                fadeOut(animationSpec = tween(280))
            }
        ) {

            composable("auth") {
                AuthScreen(navController = navController)
            }

            composable("dashboard") {
                DashboardScreen(navController = navController)
            }

            composable("patients") {
                PatientListScreen(navController = navController)
            }

            composable(
                route     = "patients/{patientId}",
                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
            ) { backStack ->
                PatientDetailScreen(
                    patientId     = backStack.arguments!!.getLong("patientId"),
                    navController = navController
                )
            }

            composable("patients/new") {
                VisitFormScreen(navController = navController)
            }

            composable(
                route     = "visit/new?patientId={patientId}",
                arguments = listOf(navArgument("patientId") {
                    type         = NavType.LongType
                    defaultValue = -1L
                })
            ) {
                VisitFormScreen(navController = navController)
            }

            composable(
                route     = "visit/{visitId}/edit",
                arguments = listOf(
                    navArgument("visitId") { type = NavType.LongType },
                    navArgument("patientId") {
                        type         = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                VisitFormScreen(navController = navController)
            }

            composable("appointments") {
                AppointmentsScreen(navController = navController)
            }

            composable("analytics") {
                AnalyticsScreen(navController = navController)
            }

            composable("settings") {
                SettingsScreen(navController = navController)
            }
        }
    }
}

@Composable
fun SwaraPulseBottomBar(navController: NavController) {
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "dashboard",
            onClick  = {
                navController.navigate("dashboard") {
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentRoute == "patients",
            onClick  = {
                navController.navigate("patients") {
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(Icons.Rounded.People, contentDescription = "Patients") },
            label = { Text("Patients") }
        )
        NavigationBarItem(
            selected = currentRoute == "appointments",
            onClick  = {
                navController.navigate("appointments") {
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(Icons.Rounded.CalendarMonth, contentDescription = "Schedule") },
            label = { Text("Schedule") }
        )
        NavigationBarItem(
            selected = currentRoute == "analytics",
            onClick  = {
                navController.navigate("analytics") {
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(Icons.Rounded.BarChart, contentDescription = "Analytics") },
            label = { Text("Analytics") }
        )
        NavigationBarItem(
            selected = currentRoute == "settings",
            onClick  = {
                navController.navigate("settings") {
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}
```

---

## STEP 4 — FIX `AuthScreen` navigation on success

Open `AuthScreen.kt`. Find the block that handles `AuthState.Authenticated`.
It will look something like:

```kotlin
is AuthState.Authenticated -> {
    // some navigation call
}
```

Make sure the navigation call inside it is EXACTLY:
```kotlin
navController.navigate("dashboard") {
    popUpTo("auth") { inclusive = true }
}
```

If the `AuthScreen` composable does NOT have a `NavController` parameter,
update its signature to:
```kotlin
@Composable
fun AuthScreen(navController: NavController)
```

And make sure the `NavHost` call above passes `navController = navController`.

---

## STEP 5 — CHECK `SwaraPulseTheme`

Open `presentation/ui/theme/Theme.kt`.

If the function signature requires any injected parameter (like a ViewModel
or DataStore), simplify it to require nothing:

```kotlin
@Composable
fun SwaraPulseTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary    = com.swarapulse.presentation.ui.theme.Indigo600,
            secondary  = com.swarapulse.presentation.ui.theme.Purple600,
            tertiary   = com.swarapulse.presentation.ui.theme.Cyan500,
            background = com.swarapulse.presentation.ui.theme.Slate900,
            surface    = com.swarapulse.presentation.ui.theme.Slate800,
            error      = com.swarapulse.presentation.ui.theme.Rose500
        )
    } else {
        lightColorScheme(
            primary    = com.swarapulse.presentation.ui.theme.Indigo600,
            secondary  = com.swarapulse.presentation.ui.theme.Purple600,
            tertiary   = com.swarapulse.presentation.ui.theme.Cyan500,
            background = com.swarapulse.presentation.ui.theme.Slate50,
            surface    = androidx.compose.ui.graphics.Color.White,
            error      = com.swarapulse.presentation.ui.theme.Rose500
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = com.swarapulse.presentation.ui.theme.SwaraPulseTypography,
        content     = content
    )
}
```

---

## STEP 6 — VERIFY `SwaraPulseApp.kt`

Open `SwaraPulseApp.kt`. It must contain `@HiltAndroidApp`. If it doesn't,
add it. The full file must be:

```kotlin
package com.swarapulse

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.swarapulse.shortcuts.AppShortcutManager
import com.swarapulse.worker.FollowupReminderWorker
import com.swarapulse.worker.WidgetUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SwaraPulseApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var shortcutManager: AppShortcutManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FollowupReminderWorker.schedule(this)
        shortcutManager.pushStaticShortcuts()
    }
}
```

If `AppShortcutManager` or `WidgetUpdateWorker` don't exist yet,
remove those lines — do NOT leave a compile error. The minimum viable
version is:

```kotlin
@HiltAndroidApp
class SwaraPulseApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

---

## STEP 7 — BUILD AND CONFIRM

After all rewrites are complete:

1. Run `./gradlew assembleDebug` (or trigger a build)
2. Report any compile errors — do NOT stop if there are errors, list them all
3. Fix each compile error one by one:
   - Missing import → add the import
   - Unresolved reference → check the package path and correct it
   - Type mismatch on `innerPadding` → add `Modifier.padding(innerPadding)` to the `NavHost`
4. Rebuild until the build is clean

---

## WHAT MUST NOT HAPPEN

- Do NOT leave any `Text("SwaraPulse App Scaffold")` anywhere in the codebase
- Do NOT leave any `Text("Dashboard coming soon")` or similar
- Do NOT skip reading the files first — reading them tells you which exact
  placeholder to replace
- Do NOT create a new file if the existing file just needs to be overwritten
- The `NavHost` `startDestination` must be `"auth"` — not `"dashboard"`
