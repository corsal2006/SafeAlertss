package com.siddesh.smsalerts

import com.siddesh.smsalerts.utils.ProfileDates
import com.siddesh.smsalerts.utils.EmergencyMessageFormatter
import com.siddesh.smsalerts.service.TimerPolicy
import com.siddesh.smsalerts.data.sensor.ShakePattern
import com.siddesh.smsalerts.mesh.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class ProductionSafetyRegressionTest {
    @Test fun ageChangesOnlyAtBirthday() {
        assertEquals(25, ProfileDates.age("2000-10-18", "2026-10-17"))
        assertEquals(26, ProfileDates.age("2000-10-18", "2026-10-18"))
        assertEquals(26, ProfileDates.age("2000-10-18", "2026-10-19"))
    }
    @Test fun leapDayAgesAndInvalidDates() {
        assertEquals(24, ProfileDates.age("2000-02-29", "2025-02-28"))
        assertEquals(25, ProfileDates.age("2000-02-29", "2025-03-01"))
        assertNull(ProfileDates.age("2026-02-29", "2026-10-18"))
        assertNull(ProfileDates.age("2026-10-19", "2026-10-18"))
        assertNull(ProfileDates.age(null)); assertNull(ProfileDates.age("")); assertNull(ProfileDates.age("not a date"))
    }
    @Test fun graceIsAnchoredToDeadlineAndCannotBeSkippedByRepeatedCallback() {
        repeat(50) { assertEquals(TimerPolicy.Phase.GRACE, TimerPolicy.phase(true, false, 1000, 2, 120999)) }
        assertEquals(TimerPolicy.Phase.EXPIRED, TimerPolicy.phase(true, false, 1000, 2, 121000))
        assertEquals(TimerPolicy.Phase.EXPIRED, TimerPolicy.phase(true, false, 1000, 2, 500000))
    }
    @Test fun immediateCancellationPauseAndEarlyWake() {
        assertEquals(TimerPolicy.Phase.EXPIRED, TimerPolicy.phase(true, false, 1000, 0, 1000))
        assertEquals(TimerPolicy.Phase.WAITING, TimerPolicy.phase(true, false, 1000, 0, 999))
        assertEquals(TimerPolicy.Phase.INACTIVE, TimerPolicy.phase(false, false, 1000, 0, 99999))
        assertEquals(TimerPolicy.Phase.INACTIVE, TimerPolicy.phase(true, true, 1000, 0, 99999))
    }
    @Test fun generalTemplateNeverInheritsJourneyHelplineOrInventsLocationOrAge() {
        val text = EmergencyMessageFormatter.formatEmergencyMessage("Synthetic A", womensHelplineNumber = "TEST-WOMEN", isWomensHelplineEnabled = true)
        assertFalse(text.contains("TEST-WOMEN")); assertFalse(text.contains("Age:")); assertFalse(text.contains("Live tracking:"))
        assertTrue(text.contains("Latest location: unavailable")); assertTrue(text.contains("Please call me immediately"))
    }
    @Test fun journeyIncludesOnlyExplicitFieldsAndActualLocationTimestamp() {
        val text = EmergencyMessageFormatter.formatEmergencyMessage("Synthetic A", isJourney = true, age = 26, womensHelplineNumber = "TEST-WOMEN", latitude = 1.0, longitude = 2.0, locationTimestampMs = 1000, incidentTimestampMs = 2000)
        assertTrue(text.contains("Age: 26")); assertTrue(text.contains("TEST-WOMEN")); assertTrue(text.contains("Location time:"))
    }
    @Test fun shakeNeedsFiveSeparatePeaksAndCooldown() {
        val pattern = ShakePattern()
        repeat(4) { i -> assertFalse(pattern.sample(4f, i * 400L)); pattern.sample(0f, i * 400L + 100) }
        assertTrue(pattern.sample(4f, 1600)); pattern.sample(0f, 1700)
        assertFalse(pattern.sample(4f, 2000))
    }
    @Test fun walkingConstantAccelerationAndSlowShakesDoNotTrigger() {
        val pattern = ShakePattern()
        repeat(100) { assertFalse(pattern.sample(0.5f, it * 100L)) }
        repeat(100) { assertFalse(pattern.sample(4f, 11000 + it * 100L)) }
        pattern.reset()
        repeat(5) { assertFalse(pattern.sample(4f, 30000 + it * 1500L)); pattern.sample(0f, 30100 + it * 1500L) }
    }
    @Test fun encryptedPacketRoundTripsAndRejectsTamperingWrongGroupAndTruncation() {
        val key = ByteArray(32) { it.toByte() }
        val clear = "Synthetic private SOS".toByteArray()
        val encrypted = MeshCrypto.encrypt(key, clear)
        assertArrayEquals(clear, MeshCrypto.decrypt(key, encrypted))
        assertFalse(encrypted.contentEquals(clear))
        listOf(encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }, encrypted.copyOf(14)).forEach {
            assertThrows(Exception::class.java) { MeshCrypto.decrypt(key, it) }
        }
        assertThrows(Exception::class.java) { MeshCrypto.decrypt(ByteArray(32), encrypted) }
    }
    private fun signedPacket(): MeshPacket {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(keys.public.encoded)
        val packet = MeshPacket(senderDeviceId = MeshSecurity.sha256Hex(pub).take(32), publicKey = pub, senderName = "Synthetic", age = 25, messageText = "Test SOS")
        return packet.copy(signature = Base64.getEncoder().encodeToString(MeshCrypto.sign(keys.private, packet.getSigningPayload().toByteArray())))
    }
    private fun valid(packet: MeshPacket) = MeshSecurity.verifyPacketIntegrity(packet, System.currentTimeMillis()) { Base64.getDecoder().decode(it) }
    @Test fun realSignatureRejectsChangedSensitiveFieldsAndInventedRelayHop() {
        val packet = signedPacket()
        assertTrue(valid(packet))
        listOf(packet.copy(senderName = "Forged"), packet.copy(age = 99), packet.copy(messageText = "Forged"), packet.copy(recipientId = "other"), packet.copy(maxHops = 4), packet.copy(hopCount = 1), packet.copy(latitude = 80.0)).forEach { assertFalse(valid(it)) }
    }
    @Test fun packetRejectsMalformedOversizedAndExpiredData() {
        assertNull(MeshPacket.fromJson("{")); assertNull(MeshPacket.fromJson("x".repeat(17000)))
        assertFalse(valid(signedPacket().copy(expiryTimestamp = 0)))
        assertFalse(valid(signedPacket().copy(messageText = "x".repeat(5000))))
    }
}
