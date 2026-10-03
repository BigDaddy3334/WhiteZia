package shop.whitezia.client.vpn

import java.io.ByteArrayInputStream
import org.amnezia.awg.config.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmneziaWg31ConfigTest {
    @Test
    fun parseAndRenderAwg31InterfaceOptions() {
        val configText = """
            [Interface]
            PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            Address = 10.8.0.2/32
            HeaderProtectionKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=
            ContentPaddingAddition = 0-64
        """.trimIndent()

        val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))

        assertTrue(config.`interface`.headerProtectionKey.isPresent)
        assertEquals(
            "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            config.`interface`.headerProtectionKey.get().toBase64(),
        )
        assertEquals("0-64", config.`interface`.contentPaddingAddition.orElseThrow())
        assertTrue(config.toAwgQuickString().contains("ContentPaddingAddition = 0-64"))
    }
}
