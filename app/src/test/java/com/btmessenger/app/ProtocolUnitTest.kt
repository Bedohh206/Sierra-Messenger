package com.btmessenger.app

import com.btmessenger.app.bluetooth.Protocol
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProtocolUnitTest {

    @Test
    fun groupMessage_create_and_parse_roundtrip() {
        val msgId = UUID.randomUUID().toString()
        val from = "device-123"
        val groupId = "group-abc"
        val body = "Hello group"

        val json = Protocol.createGroupTextMessage(msgId, from, groupId, body)
        val parsed = Protocol.parseMessage(json)

        assertNotNull(parsed)
        assertEquals(Protocol.TYPE_GROUP_MESSAGE, parsed?.type)
        assertEquals(msgId, parsed?.msgId)
        assertEquals(from, parsed?.from)
        assertEquals(groupId, parsed?.groupId)
        assertEquals(body, parsed?.body)
    }

    @Test
    fun registerMessage_create_and_parse_roundtrip() {
        val msgId = UUID.randomUUID().toString()
        val from = "device-xyz"
        val display = "Alice"

        val json = Protocol.createRegisterMessage(msgId, from, display)
        val parsed = Protocol.parseMessage(json)

        assertNotNull(parsed)
        assertEquals(Protocol.TYPE_REGISTER, parsed?.type)
        assertEquals(msgId, parsed?.msgId)
        assertEquals(from, parsed?.from)
        assertEquals(display, parsed?.body)
    }
}
