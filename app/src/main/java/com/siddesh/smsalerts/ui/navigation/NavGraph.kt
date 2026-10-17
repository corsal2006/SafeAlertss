package com.siddesh.smsalerts.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    sosViewModel: SosViewModel,
    contactsViewModel: ContactsViewModel,
    historyViewModel: HistoryViewModel,
    profileViewModel: ProfileViewModel,
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val authState by authViewModel.authState.collectAsState()

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
                    }
                }
            }
            else -> {}
        }
    }

    val bottomBarRoutes = listOf(
        Screen.Home.route,
        Screen.Contacts.route,
        Screen.History.route,
        Screen.Settings.route
    )

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
                        navController.navigate(Screen.Profile.route)
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
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
                    viewModel = profileViewModel,
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
                        navController.navigate(Screen.About.route)
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
                    onNavigateBack = {
                        historyViewModel.loadHistory()
                        homeViewModel.refreshDashboard()
                        navController.popBackStack()
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
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToContacts = {
                        navController.navigate(Screen.Contacts.route)
                    },
                    onNavigateToEmergencyTriggers = {
                        navController.navigate(Screen.EmergencyTriggers.route)
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
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
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.WalkieTalkie.route) {
                com.siddesh.smsalerts.ui.screens.walkie.WalkieTalkieScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.WomensSafety.route) {
                com.siddesh.smsalerts.ui.screens.womensafety.WomensSafetyScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToSos = {
                        navController.navigate(Screen.SosCountdown.createRoute("PANIC", "WOMENS_SAFETY"))
                    }
                )
            }

            composable(Screen.TrustedCircle.route) {
                com.siddesh.smsalerts.ui.screens.trustedcircle.TrustedCircleScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.About.route) {
                AboutScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
