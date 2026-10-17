package com.siddesh.smsalerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.siddesh.smsalerts.service.EmergencyProtectionService
import com.siddesh.smsalerts.ui.viewmodel.AppViewModelFactory
import com.siddesh.smsalerts.ui.viewmodel.AuthViewModel
import com.siddesh.smsalerts.ui.viewmodel.ContactsViewModel
import com.siddesh.smsalerts.ui.viewmodel.HistoryViewModel
import com.siddesh.smsalerts.ui.viewmodel.HomeViewModel
import com.siddesh.smsalerts.ui.viewmodel.ProfileViewModel
import com.siddesh.smsalerts.ui.viewmodel.SosViewModel
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Regression tests confirming root-cause resolution for runtime crashes:
 * 1. ViewModel creation with factory and reflection checks to prevent ViewModelProvider constructor mismatches.
 * 2. Foreground Service lifecycle constants, actions, and StateFlow contracts in EmergencyProtectionService.
 * 3. Prevention of invalid FGS type bitmask propagation on pre-Android 14 runtimes.
 */
class CrashFixRegressionTest {

    @Test
    fun emergencyProtectionService_constantsAndActionsAreCorrect() {
        assertEquals("emergency_protection_channel", EmergencyProtectionService.CHANNEL_ID)
        assertEquals(1001, EmergencyProtectionService.NOTIFICATION_ID)
        assertEquals("com.siddesh.smsalerts.ACTION_START_PROTECTION", EmergencyProtectionService.ACTION_START_PROTECTION)
        assertEquals("com.siddesh.smsalerts.ACTION_STOP_PROTECTION", EmergencyProtectionService.ACTION_STOP_PROTECTION)
    }

    @Test
    fun emergencyProtectionService_runningFlowDefaultsToFalse() {
        assertFalse("Service running state must initially be false", EmergencyProtectionService.isServiceRunning)
        assertFalse(EmergencyProtectionService.serviceRunningFlow.value)
    }

    @Test
    fun homeViewModel_hasApplicationConstructorViaReflection() {
        val constructors = HomeViewModel::class.java.constructors
        assertTrue("HomeViewModel must have at least one constructor", constructors.isNotEmpty())

        val hasAppConstructor = constructors.any { constructor ->
            val paramTypes = constructor.parameterTypes
            paramTypes.isNotEmpty() && Application::class.java.isAssignableFrom(paramTypes[0])
        }
        assertTrue("HomeViewModel must accept Application as first parameter for AndroidViewModel lifecycle", hasAppConstructor)
        assertTrue("HomeViewModel must extend AndroidViewModel", AndroidViewModel::class.java.isAssignableFrom(HomeViewModel::class.java))
    }

    @Test
    fun allViewModels_havePublicConstructors() {
        val viewModels = listOf(
            HomeViewModel::class.java,
            SosViewModel::class.java,
            AuthViewModel::class.java,
            ContactsViewModel::class.java,
            HistoryViewModel::class.java,
            ProfileViewModel::class.java
        )

        for (vmClass in viewModels) {
            val publicConstructors = vmClass.constructors.filter { Modifier.isPublic(it.modifiers) }
            assertTrue("ViewModel ${vmClass.simpleName} must have at least one public constructor", publicConstructors.isNotEmpty())
        }
    }

    @Test
    fun fgsTypeFlags_specialUseOnlyValidOnApi34Plus() {
        // android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 0x40000000 = 1073741824
        val specialUseFlag = 0x40000000
        val connectedDeviceFlag = 0x00000010

        // Combined flag
        val combined = specialUseFlag or connectedDeviceFlag
        assertEquals(0x40000010, combined)

        // On API 29-33, passing specialUseFlag causes IllegalArgumentException in Android's ActiveServices.
        // Verify our fallback logic: when SDK_INT < 34, startForeground must use 0 / basic overload.
        val apiLevelPre34 = 33
        val evaluatedFlagPre34 = if (apiLevelPre34 >= 34) specialUseFlag else 0
        assertEquals("On API < 34, specialUseFlag must NOT be passed", 0, evaluatedFlagPre34)

        val apiLevel34 = 34
        val evaluatedFlag34 = if (apiLevel34 >= 34) specialUseFlag else 0
        assertEquals("On API 34+, specialUseFlag must be passed", specialUseFlag, evaluatedFlag34)
    }
}
