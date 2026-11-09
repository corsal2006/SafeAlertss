package com.siddesh.smsalerts.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object Auth : Screen("welcome")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Register : Screen("signup")
    object ForgotPassword : Screen("forgot_password")

    object Home : Screen("home")
    object EmergencyType : Screen("emergency_type")
    object EmergencyReview : Screen("emergency_review/{type}") {
        fun createRoute(type: String): String = "emergency_review/$type"
    }

    object TrustedContacts : Screen("trusted_contacts")
    object Contacts : Screen("trusted_contacts")
    object AddContact : Screen("add_contact")

    object AlertHistory : Screen("alert_history")
    object History : Screen("alert_history")
    object AlertDetails : Screen("alert_detail/{alertId}") {
        fun createRoute(alertId: String): String = "alert_detail/$alertId"
    }
    object AlertDetail : Screen("alert_detail/{alertId}") {
        fun createRoute(alertId: String): String = "alert_detail/$alertId"
    }

    object SafetyCheckIn : Screen("safety_check_in")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object EmergencyTriggers : Screen("emergency_triggers")
<<<<<<< HEAD
    object WalkieTalkie : Screen("walkie_talkie")
    object WomensSafety : Screen("womens_safety")
    object TrustedCircle : Screen("trusted_circle")
=======
>>>>>>> origin/main
    object About : Screen("about")

    object SosCountdown : Screen("sos_countdown/{type}?trigger={trigger}") {
        fun createRoute(type: String, trigger: String = "BUTTON"): String = "sos_countdown/$type?trigger=$trigger"
    }
}
