package shop.whitezia.client.controlplane

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LimitedResponseReaderTest {
    @Test
    fun readsResponseWithinLimit() {
        val response = "payload".toByteArray(Charsets.UTF_8)

        assertEquals(
            "payload",
            ByteArrayInputStream(response).readUtf8Limited(response.size),
        )
    }

    @Test
    fun rejectsResponseBeyondLimit() {
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(ByteArray(17)).readUtf8Limited(16)
        }
    }
}
