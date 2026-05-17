# Jules Prompt — SwaraPulse Debug Fix
## Issue: App shows blank "SwaraPulse App Scaffold" placeholder on launch

> The app builds and installs successfully on device.
> The problem is that `MainActivity` or `NavHost` is rendering a placeholder
> `Text("SwaraPulse App Scaffold")` composable instead of the real screen tree.
> Fix ALL of the following in one pass. Write complete working code — no stubs.

---

## DIAGNOSIS CHECKLIST

Work through each file in order. Fix every issue found.

---

## FIX 1 — `MainActivity.kt`

Open `MainActivity.kt`. It likely contains something like:
```kotlin
setContent {
    Text("SwaraPulse App Scaffold")
}
```
or
```kotlin
setContent {
    SwaraPulseTheme {
        Text("SwaraPulse App Scaffold")
    }
}
```

Replace the entire `setContent { }` block with:
```kotlin
setContent {
    SwaraPulseTheme {
        val startRoute = resolveStartRoute(intent)
        SwaraPulseNavHost(startRoute = startRoute)
    }
}
```

Ensure these are present at the top of `onCreate()` before `super.onCreate()`:
```kotlin
installSplashScreen()
super.onCreate(savedInstanceState)
WindowCompat.setDecorFitsSystemWindows(window, false)
```

Full corrected `MainActivity.kt`:
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
import com.swarapulse.shortcuts.AppShortcutManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        val DEEP_LINK_KEY = androidx.glance.action.ActionParameters.Key<String>("deepLink")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SwaraPulseTheme {
                val startRoute = resolveStartRoute(intent)
                SwaraPulseNavHost(startRoute = startRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun resolveStartRoute(intent: Intent?): String? {
        intent ?: return null
        return when {
            intent.data?.toString()?.startsWith("swarapulse://") == true -> {
                val uri = intent.data.toString()
                when {
                    uri.contains("/appointments") -> "appointments"
                    uri.contains("/patients/")   ->
                        "patients/${uri.substringAfterLast('/')}"
                    else -> null
                }
            }
            intent.action == AppShortcutManager.ACTION_NEW_PATIENT    -> "patients/new"
            intent.action == AppShortcutManager.ACTION_TODAY_SCHEDULE -> "appointments"
            intent.action == AppShortcutManager.ACTION_NEW_VISIT      -> {
                val id = intent.getLongExtra(AppShortcutManager.EXTRA_PATIENT_ID, -1L)
                "visit/new?patientId=$id"
            }
            else -> null
        }
    }
}
```

---

## FIX 2 — `SwaraPulseNavHost.kt`

Open `presentation/navigation/NavHost.kt` (or wherever `SwaraPulseNavHost` is defined).

It may look like one of these broken states:

**Broken state A** — function body is empty or has a placeholder:
```kotlin
@Composable
fun SwaraPulseNavHost(startRoute: String? = null) {
    Text("NavHost placeholder")
}
```

**Broken state B** — NavHost exists but all `composable()` blocks contain placeholders:
```kotlin
composable("dashboard") {
    Text("Dashboard coming soon")
}
```

**Broken state C** — NavHost doesn't compile due to missing imports or
screen constructors that don't match.

**Fix:** Replace the entire file with the following complete implementation.
Import every screen that exists in the project. If a screen composable
doesn't exist yet, use a temporary `Box(Modifier.fillMaxSize())` as its
body — but every screen from Phases 1–5 should exist.

```kotlin
package com.swarapulse.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.*
import com.swarapulse.presentation.analytics.AnalyticsScreen
import com.swarapulse.presentation.appointments.AppointmentsScreen
import com.swarapulse.presentation.auth.AuthScreen
import com.swarapulse.presentation.dashboard.DashboardScreen
import com.swarapulse.presentation.patients.PatientDetailScreen
import com.swarapulse.presentation.patients.PatientListScreen
import com.swarapulse.presentation.settings.SettingsScreen
import com.swarapulse.presentation.visit.VisitFormScreen

// Routes that should NOT show the bottom navigation bar
private val routesWithoutBottomBar = setOf(
    "auth",
    "patients/new",
    "visit/new",       // prefix match handled below
    "visit/{visitId}/edit"
)

@Composable
fun SwaraPulseNavHost(startRoute: String? = null) {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    // Bottom bar is hidden on auth, form screens, and patient detail
    val showBottomBar = currentRoute != null &&
        currentRoute != "auth" &&
        !currentRoute.startsWith("visit/") &&
        currentRoute != "patients/new"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = "auth",
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) +
                fadeIn(tween(280))
            },
            exitTransition   = {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(280)) +
                fadeOut(tween(280))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(280)) +
                fadeIn(tween(280))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280)) +
                fadeOut(tween(280))
            }
        ) {
            // ── Auth ──────────────────────────────────────────────────────
            composable("auth") {
                AuthScreen(
                    navController = navController,
                    startRoute    = startRoute
                )
            }

            // ── Dashboard ─────────────────────────────────────────────────
            composable("dashboard") {
                DashboardScreen(navController = navController)
            }

            // ── Patients list ─────────────────────────────────────────────
            composable("patients") {
                PatientListScreen(navController = navController)
            }

            // ── Patient detail ────────────────────────────────────────────
            composable(
                route     = "patients/{patientId}",
                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
            ) { backStack ->
                PatientDetailScreen(
                    patientId     = backStack.arguments!!.getLong("patientId"),
                    navController = navController
                )
            }

            // ── New patient (opens visit form in new-patient mode) ─────────
            composable("patients/new") {
                VisitFormScreen(navController = navController)
            }

            // ── New visit for existing patient ────────────────────────────
            composable(
                route     = "visit/new?patientId={patientId}",
                arguments = listOf(navArgument("patientId") {
                    type         = NavType.LongType
                    defaultValue = -1L
                })
            ) {
                VisitFormScreen(navController = navController)
            }

            // ── Edit existing visit ───────────────────────────────────────
            composable(
                route     = "visit/{visitId}/edit",
                arguments = listOf(
                    navArgument("visitId")   { type = NavType.LongType },
                    navArgument("patientId") {
                        type         = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                VisitFormScreen(navController = navController)
            }

            // ── Appointments ──────────────────────────────────────────────
            composable("appointments") {
                AppointmentsScreen(navController = navController)
            }

            // ── Analytics ─────────────────────────────────────────────────
            composable("analytics") {
                AnalyticsScreen(navController = navController)
            }

            // ── Settings ──────────────────────────────────────────────────
            composable("settings") {
                SettingsScreen(navController = navController)
            }
        }
    }
}
```

---

## FIX 3 — `AuthScreen.kt` — pass `startRoute` and use it on success

Open `presentation/auth/AuthScreen.kt`.

The `AuthScreen` composable must accept `startRoute: String?` and navigate
to it (or `"dashboard"`) on successful authentication.

Update the composable signature:
```kotlin
@Composable
fun AuthScreen(
    navController: NavController,
    startRoute: String? = null      // ADD this parameter
)
```

Find the navigation call that fires on `AuthState.Authenticated`
(inside a `LaunchedEffect` collecting `vm.authState`). It likely reads:
```kotlin
AuthState.Authenticated -> {
    navController.navigate("dashboard") {
        popUpTo("auth") { inclusive = true }
    }
}
```

Change it to:
```kotlin
AuthState.Authenticated -> {
    navController.navigate(startRoute ?: "dashboard") {
        popUpTo("auth") { inclusive = true }
    }
}
```

---

## FIX 4 — `SwaraPulseTheme.kt` — ensure it doesn't block rendering

Open `presentation/ui/theme/Theme.kt`.

The theme must NOT require any suspended or async data to render its first
frame. If it collects `darkMode` from DataStore, it must have a safe default:

```kotlin
@Composable
fun SwaraPulseTheme(
    content: @Composable () -> Unit
) {
    // If SettingsDataStore isn't injected here, use a simple default
    // Dark mode can be toggled at Settings level — theme reads from a
    // local CompositionLocal or a simple remembered state
    val darkTheme = isSystemInDarkTheme()   // safe fallback if DataStore not wired yet

    val colorScheme = if (darkTheme) darkColorScheme(
        primary    = Indigo600,
        secondary  = Purple600,
        tertiary   = Cyan500,
        background = Slate900,
        surface    = Slate800,
        error      = Rose500
    ) else lightColorScheme(
        primary    = Indigo600,
        secondary  = Purple600,
        tertiary   = Cyan500,
        background = Slate50,
        surface    = Color.White,
        error      = Rose500
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SwaraPulseTypography,
        content     = content
    )
}
```

---

## FIX 5 — `BottomBar` composable

If `BottomBar` is missing or is a placeholder, add it at the bottom of
`NavHost.kt` (same file):

```kotlin
@Composable
fun BottomBar(navController: NavController) {
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "dashboard",
            onClick  = {
                navController.navigate("dashboard") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(androidx.compose.material.icons.Icons.Rounded.Home, null) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentRoute == "patients",
            onClick  = {
                navController.navigate("patients") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(androidx.compose.material.icons.Icons.Rounded.People, null) },
            label = { Text("Patients") }
        )
        NavigationBarItem(
            selected = currentRoute == "appointments",
            onClick  = {
                navController.navigate("appointments") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(androidx.compose.material.icons.Icons.Rounded.CalendarMonth, null) },
            label = { Text("Schedule") }
        )
        NavigationBarItem(
            selected = currentRoute == "analytics",
            onClick  = {
                navController.navigate("analytics") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(androidx.compose.material.icons.Icons.Rounded.BarChart, null) },
            label = { Text("Analytics") }
        )
        NavigationBarItem(
            selected = currentRoute == "settings",
            onClick  = {
                navController.navigate("settings") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(androidx.compose.material.icons.Icons.Rounded.Settings, null) },
            label = { Text("Settings") }
        )
    }
}
```

---

## FIX 6 — Check every screen composable for placeholders

Search the entire project for any of these strings and replace with the
real composable body from the Phase prompts:

```
"coming soon"
"placeholder"
"TODO"
"Not implemented"
Box(Modifier.fillMaxSize())  ← only if it's the ENTIRE screen body
```

For each placeholder found, reinstate the full composable as specified
in the relevant Phase prompt. Priority order to fix:
1. `AuthScreen` — must render before anything else works
2. `DashboardScreen`
3. `PatientListScreen`
4. `PatientDetailScreen`
5. `VisitFormScreen`
6. `AppointmentsScreen`
7. `AnalyticsScreen`
8. `SettingsScreen`

---

## FIX 7 — Verify Hilt wiring

Confirm all of the following (fix any that are missing):

```kotlin
// SwaraPulseApp.kt must be:
@HiltAndroidApp
class SwaraPulseApp : Application(), Configuration.Provider { ... }

// MainActivity must be:
@AndroidEntryPoint
class MainActivity : ComponentActivity() { ... }

// Every ViewModel must have:
@HiltViewModel
class XxxViewModel @Inject constructor(...) : ViewModel()

// AndroidManifest.xml must have:
android:name=".SwaraPulseApp"
```

If `@AndroidEntryPoint` is missing from `MainActivity`, Hilt won't inject
into any composable that uses `hiltViewModel()` — all screens will crash
silently and show nothing.

---

## COMPLETION CHECKLIST

After all fixes, do a clean build (`./gradlew clean assembleDebug`) and verify:

- [ ] App launches and shows Auth screen (PIN setup on first launch)
- [ ] After PIN setup/entry, Dashboard screen renders with bottom nav bar
- [ ] All 5 bottom nav tabs navigate without crashing
- [ ] No screen shows placeholder text
- [ ] `@AndroidEntryPoint` on `MainActivity` is present
- [ ] `SwaraPulseTheme` does not crash on first composition
- [ ] `BottomBar` is hidden on `auth`, `patients/new`, and `visit/*` routes
