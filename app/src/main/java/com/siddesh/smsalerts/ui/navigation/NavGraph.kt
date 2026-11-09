package com.siddesh.smsalerts.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
<<<<<<< HEAD
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
=======
>>>>>>> origin/main
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.siddesh.smsalerts.ui.components.SafeAlertBottomNavBar
import com.siddesh.smsalerts.ui.screens.about.AboutScreen
import com.siddesh.smsalerts.ui.screens.auth.ForgotPasswordScreen
import com.siddesh.smsalerts.ui.screens.auth.LoginScreen
import com.siddesh.smsalerts.ui.screens.auth.RegisterScreen
import com.siddesh.smsalerts.ui.screens.auth.WelcomeScreen
import com.siddesh.smsalerts.ui.screens.contacts.ContactsScreen
import com.siddesh.smsalerts.ui.screens.history.AlertDetailScreen
import com.siddesh.smsalerts.ui.screens.history.HistoryScreen
import com.siddesh.smsalerts.ui.screens.home.HomeScreen
import com.siddesh.smsalerts.ui.screens.profile.ProfileScreen
import com.siddesh.smsalerts.ui.screens.settings.SettingsScreen
import com.siddesh.smsalerts.ui.screens.sos.SosCountdownScreen
import com.siddesh.smsalerts.ui.screens.splash.SplashScreen
import com.siddesh.smsalerts.ui.theme.BackgroundDark
import com.siddesh.smsalerts.ui.viewmodel.*

@Composable
fun SafeAlertNavGraph(
<<<<<<< HEAD
    openNearby: Boolean = false,
    onNotificationOpened: () -> Unit = {},
=======
>>>>>>> origin/main
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    sosViewModel: SosViewModel,
    contactsViewModel: ContactsViewModel,
    historyViewModel: HistoryViewModel,
    profileViewModel: ProfileViewModel,
    onGoogleSignInClick: () -> Unit,
<<<<<<< HEAD
    modifier: Modifier = Modifier,
    notificationIncidentId: String? = null
=======
    modifier: Modifier = Modifier
>>>>>>> origin/main
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val authState by authViewModel.authState.collectAsState()
<<<<<<< HEAD
    val emergencyStage by sosViewModel.stage.collectAsState()
    val profile by profileViewModel.userProfile.collectAsState()
=======
>>>>>>> origin/main

    androidx.compose.runtime.LaunchedEffect(authState) {
        when (authState) {
            is com.siddesh.smsalerts.data.repository.AuthState.SignedIn -> {
                val current = navController.currentDestination?.route
                if (current == Screen.Welcome.route ||
                    current == Screen.Login.route ||
                    current == Screen.Register.route
                ) {
                    homeViewModel.refreshUser()
                    contactsViewModel.loadContacts()
                    historyViewModel.loadHistory()
                    profileViewModel.loadProfile()
<<<<<<< HEAD
                    com.siddesh.smsalerts.service.EmergencyMessagingService.registerCurrentDevice()
=======
>>>>>>> origin/main

                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
            is com.siddesh.smsalerts.data.repository.AuthState.SignedOut -> {
                val current = navController.currentDestination?.route
                if (current != null &&
                    current != Screen.Splash.route &&
                    current != Screen.Welcome.route &&
                    current != Screen.Login.route &&
                    current != Screen.Register.route &&
                    current != Screen.ForgotPassword.route
                ) {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
<<<<<<< HEAD
                        launchSingleTop = true
=======
>>>>>>> origin/main
                    }
                }
            }
            else -> {}
        }
    }

<<<<<<< HEAD
    val safePopBack: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigate(Screen.Home.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(notificationIncidentId, authState) {
        if (notificationIncidentId != null && authState is com.siddesh.smsalerts.data.repository.AuthState.SignedIn) navController.navigate("incident/$notificationIncidentId") { launchSingleTop = true }
    }
=======
>>>>>>> origin/main
    val bottomBarRoutes = listOf(
        Screen.Home.route,
        Screen.Contacts.route,
        Screen.History.route,
        Screen.Settings.route
    )
<<<<<<< HEAD
    if (emergencyStage is SosStage.Countdown && currentRoute != Screen.SosCountdown.route &&
        authState is com.siddesh.smsalerts.data.repository.AuthState.SignedIn) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { sosViewModel.cancelEmergency() },
            title = { androidx.compose.material3.Text("Emergency in ${(emergencyStage as SosStage.Countdown).secondsLeft}s") },
            text = { androidx.compose.material3.Text("Cancel now if you are safe. Otherwise the saved emergency will proceed automatically.") },
            confirmButton = {
                androidx.compose.material3.Button(onClick = { sosViewModel.cancelEmergency() }) { androidx.compose.material3.Text("I'm safe — cancel") }
            })
    }

    androidx.compose.runtime.LaunchedEffect(openNearby, currentRoute) {
        if (openNearby && currentRoute == Screen.Home.route) {
            navController.navigate(Screen.WalkieTalkie.route) { launchSingleTop = true }
            onNotificationOpened()
        }
    }
=======
>>>>>>> origin/main

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                SafeAlertBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { targetScreen ->
                        if (currentRoute != targetScreen.route) {
                            navController.navigate(targetScreen.route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        },
        containerColor = BackgroundDark,
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
<<<<<<< HEAD
            composable("user_manual") { com.siddesh.smsalerts.ui.screens.settings.UserManualScreen { safePopBack() } }
            composable("incident/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                com.siddesh.smsalerts.ui.screens.history.IncidentScreen(entry.arguments?.getString("id").orEmpty()) { safePopBack() }
            }
=======
>>>>>>> origin/main
            // Splash Screen
            composable(Screen.Splash.route) {
                SplashScreen(
                    authViewModel = authViewModel,
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToWelcome = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // Welcome Screen
            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    authViewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route)
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    },
                    onNavigateToHome = {
                        homeViewModel.refreshUser()
                        contactsViewModel.loadContacts()
                        historyViewModel.loadHistory()
                        profileViewModel.loadProfile()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    onGoogleSignInClick = onGoogleSignInClick
                )
            }

            // Auth Screens
            composable(Screen.Login.route) {
                LoginScreen(
                    authViewModel = authViewModel,
                    onGoogleSignInClick = onGoogleSignInClick,
                    onNavigateToHome = {
                        // Refresh user data across viewmodels
                        homeViewModel.refreshUser()
                        contactsViewModel.loadContacts()
                        historyViewModel.loadHistory()
                        profileViewModel.loadProfile()

                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    },
                    onNavigateToForgotPassword = {
                        navController.navigate(Screen.ForgotPassword.route)
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    authViewModel = authViewModel,
                    onNavigateToHome = {
                        homeViewModel.refreshUser()
                        contactsViewModel.loadContacts()
                        historyViewModel.loadHistory()
                        profileViewModel.loadProfile()

                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    authViewModel = authViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // Main Tabs
            composable(Screen.Home.route) {
                HomeScreen(
                    homeViewModel = homeViewModel,
                    onNavigateToEmergencyType = {
                        navController.navigate(Screen.EmergencyType.route)
                    },
                    onNavigateToEmergencyReview = { emergencyType ->
                        navController.navigate(Screen.EmergencyReview.createRoute(emergencyType.name))
                    },
                    onNavigateToCountdown = { emergencyType, triggerSource ->
<<<<<<< HEAD
                        homeViewModel.resetSosStage()
=======
>>>>>>> origin/main
                        navController.navigate(Screen.SosCountdown.createRoute(emergencyType, triggerSource))
                    },
                    onNavigateToContacts = {
                        navController.navigate(Screen.Contacts.route)
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    onNavigateToSafetyCheckIn = {
                        navController.navigate(Screen.SafetyCheckIn.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToProfile = {
<<<<<<< HEAD
                        navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route) { launchSingleTop = true }
                    },
                    onNavigateToTriggers = {
                        navController.navigate(Screen.EmergencyTriggers.route)
                    },
                    onNavigateToWalkieTalkie = {
                        navController.navigate(Screen.WalkieTalkie.route)
                    },
                    onNavigateToWomensSafety = {
                        navController.navigate(Screen.WomensSafety.route)
                    },
                    onNavigateToTrustedCircle = {
                        navController.navigate(Screen.TrustedCircle.route)
                    },
                    onNavigateToIncident = { incidentId ->
                        navController.navigate("incident/$incidentId") { launchSingleTop = true }
=======
                        navController.navigate(Screen.Profile.route)
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    },
                    onNavigateToTriggers = {
                        navController.navigate(Screen.EmergencyTriggers.route)
>>>>>>> origin/main
                    }
                )
            }

            // Emergency Flow Screens
            composable(Screen.EmergencyType.route) {
                com.siddesh.smsalerts.ui.screens.sos.EmergencyTypeScreen(
                    onTypeSelected = { type ->
                        navController.navigate(Screen.EmergencyReview.createRoute(type.name))
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.EmergencyReview.route,
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { backStackEntry ->
                val typeName = backStackEntry.arguments?.getString("type") ?: "OTHER"
                val emergencyType = try {
                    com.siddesh.smsalerts.data.model.EmergencyType.valueOf(typeName)
                } catch (e: Exception) {
                    com.siddesh.smsalerts.data.model.EmergencyType.OTHER
                }

                com.siddesh.smsalerts.ui.screens.sos.EmergencyReviewScreen(
                    emergencyType = emergencyType,
<<<<<<< HEAD
                    onCountdownStarted = {
                        navController.navigate(Screen.SosCountdown.createRoute(emergencyType.name, "REVIEW")) { launchSingleTop = true }
                    },
=======
>>>>>>> origin/main
                    contactsViewModel = contactsViewModel,
                    homeViewModel = homeViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToContacts = {
                        navController.navigate(Screen.Contacts.route)
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    onNavigateToHome = {
                        historyViewModel.loadHistory()
                        homeViewModel.refreshDashboard()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }
                )
            }

            // Contacts Screens
            composable(Screen.Contacts.route) {
                ContactsScreen(
                    contactsViewModel = contactsViewModel
                )
            }

            composable(Screen.AddContact.route) {
                com.siddesh.smsalerts.ui.screens.contacts.AddContactScreen(
                    contactsViewModel = contactsViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            // History Screens
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onAlertClick = { alertId ->
                        navController.navigate(Screen.AlertDetail.createRoute(alertId))
                    }
                )
            }

            // Safety Check-In Screen
            composable(Screen.SafetyCheckIn.route) {
                com.siddesh.smsalerts.ui.screens.home.SafetyCheckInScreen(
                    homeViewModel = homeViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
<<<<<<< HEAD
                    onNavigateToContacts = { navController.navigate(Screen.Contacts.route) { launchSingleTop = true } },
                    onContinue = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route); launchSingleTop = true } },                    viewModel = profileViewModel,
=======
                    viewModel = profileViewModel,
>>>>>>> origin/main
                    onLogoutSuccess = {
                        authViewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToAbout = {
<<<<<<< HEAD
                        navController.navigate(Screen.About.route) { launchSingleTop = true }
=======
                        navController.navigate(Screen.About.route)
>>>>>>> origin/main
                    }
                )
            }

            // SOS Countdown & Execution Screen
            composable(
                route = Screen.SosCountdown.route,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("trigger") {
                        type = NavType.StringType
                        defaultValue = "BUTTON"
                    }
                )
            ) { backStackEntry ->
                val emergencyType = backStackEntry.arguments?.getString("type") ?: "EMERGENCY"
                val triggerSource = backStackEntry.arguments?.getString("trigger") ?: "BUTTON"
                SosCountdownScreen(
                    emergencyType = emergencyType,
                    customMessage = "",
                    triggerSource = triggerSource,
                    sosViewModel = sosViewModel,
<<<<<<< HEAD
                    onIncident = { incidentId ->
                        navController.navigate("incident/$incidentId") {
                            popUpTo(Screen.SosCountdown.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateBack = {
                        historyViewModel.loadHistory()
                        homeViewModel.resetSosStage()
                        if (!navController.popBackStack(Screen.Home.route, false)) {
                            navController.navigate(Screen.Home.route) { launchSingleTop = true }
                        }
=======
                    onNavigateBack = {
                        historyViewModel.loadHistory()
                        homeViewModel.refreshDashboard()
                        navController.popBackStack()
>>>>>>> origin/main
                    }
                )
            }

            // Alert Detail Screen
            composable(
                route = Screen.AlertDetail.route,
                arguments = listOf(navArgument("alertId") { type = NavType.StringType })
            ) { backStackEntry ->
                val alertId = backStackEntry.arguments?.getString("alertId") ?: ""
                AlertDetailScreen(
                    alertId = alertId,
                    viewModel = historyViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            // Settings & About
            composable(Screen.Settings.route) {
                SettingsScreen(
<<<<<<< HEAD
                    onUserManual = { navController.navigate("user_manual") { launchSingleTop = true } },
                    onBackClick = safePopBack,
=======
                    onBackClick = {
                        navController.popBackStack()
                    },
>>>>>>> origin/main
                    onNavigateToContacts = {
                        navController.navigate(Screen.Contacts.route)
                    },
                    onNavigateToEmergencyTriggers = {
                        navController.navigate(Screen.EmergencyTriggers.route)
                    },
                    onNavigateToAbout = {
<<<<<<< HEAD
                        navController.navigate(Screen.About.route) { launchSingleTop = true }
=======
                        navController.navigate(Screen.About.route)
>>>>>>> origin/main
                    },
                    onLogoutClick = {
                        authViewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.EmergencyTriggers.route) {
                com.siddesh.smsalerts.ui.screens.settings.EmergencyTriggersScreen(
<<<<<<< HEAD
                    onBackClick = safePopBack
                )
            }

            composable(Screen.WalkieTalkie.route) {
                com.siddesh.smsalerts.ui.screens.walkie.WalkieTalkieScreen(
                    onBackClick = safePopBack
                )
            }

            composable(Screen.WomensSafety.route) {
                com.siddesh.smsalerts.ui.screens.womensafety.WomensSafetyScreen(
                    onBackClick = safePopBack,
                    onNavigateToSos = {
                        navController.navigate(Screen.SosCountdown.createRoute("PANIC", "WOMENS_SAFETY"))
=======
                    onBackClick = {
                        navController.popBackStack()
>>>>>>> origin/main
                    }
                )
            }

<<<<<<< HEAD
            composable(Screen.TrustedCircle.route) {
                com.siddesh.smsalerts.ui.screens.trustedcircle.TrustedCircleScreen(
                    onIncidentClick = { navController.navigate("incident/$it") { launchSingleTop = true } },
                    onBackClick = safePopBack
                )
            }

            composable(Screen.About.route) {
                AboutScreen(
                    onBackClick = safePopBack
=======
            composable(Screen.About.route) {
                AboutScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
>>>>>>> origin/main
                )
            }
        }
    }
}
