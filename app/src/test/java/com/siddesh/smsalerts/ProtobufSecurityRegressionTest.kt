package com.siddesh.smsalerts

import com.google.protobuf.Empty
import com.google.protobuf.InvalidProtocolBufferException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ProtobufSecurityRegressionTest {
    @Test fun deeplyNestedUnknownFieldsAreRejectedWithoutStackOverflow() {
        // Empty has no known fields: field 1 is a protobuf group (start=11, end=12).
        // GHSA-735f-pc8j-v9w8 bypassed recursion limits on this unknown-field path.
        val depth = 10_000
        val payload = ByteArray(depth * 2) { if (it < depth) 11 else 12 }
        try {
            Empty.parseFrom(payload)
            fail("Excessive nested unknown fields must be rejected")
        } catch (_: InvalidProtocolBufferException) {
            // Expected bounded parser rejection. StackOverflowError must fail the test.
        }
    }

    @Test fun ordinaryUnknownFieldsRemainWireCompatible() {
        val payload = byteArrayOf(8, 42) // Unknown field 1, varint 42.
        val message = Empty.parseFrom(payload)
        assertEquals(2, message.serializedSize)
        assertEquals(message, Empty.parseFrom(message.toByteArray()))
    }
}
