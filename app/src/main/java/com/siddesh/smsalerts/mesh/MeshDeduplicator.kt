package com.siddesh.smsalerts.mesh

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe deduplication engine for SafeAlerts Mesh packets.
 * Prevents message loops, broadcast storms, and repeated emergency notifications.
 */
class MeshDeduplicator(
    private val maxCapacity: Int = 1000,
    private val retentionPeriodMs: Long = 24 * 60 * 60 * 1000L // 24 hours
) {
    // Maps messageId -> timestamp when first seen
    private val seenMessages = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun isDuplicate(messageId: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        pruneIfNecessary(currentTimeMs)
        return seenMessages.containsKey(messageId)
    }

    @Synchronized
    fun markSeen(messageId: String, currentTimeMs: Long = System.currentTimeMillis()) {
        pruneIfNecessary(currentTimeMs)
        if (seenMessages.size >= maxCapacity) {
            // Remove oldest entries
            val oldest = seenMessages.entries.minByOrNull { it.value }
            oldest?.let { seenMessages.remove(it.key) }
        }
        seenMessages[messageId] = currentTimeMs
    }

    @Synchronized
    fun recordAndCheckDuplicate(messageId: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (isDuplicate(messageId, currentTimeMs)) {
            return true
        }
        markSeen(messageId, currentTimeMs)
        return false
    }

    @Synchronized
    fun pruneIfNecessary(currentTimeMs: Long = System.currentTimeMillis()) {
        val iterator = seenMessages.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTimeMs - entry.value > retentionPeriodMs) {
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        seenMessages.clear()
    }

    val size: Int
        get() = seenMessages.size
}
