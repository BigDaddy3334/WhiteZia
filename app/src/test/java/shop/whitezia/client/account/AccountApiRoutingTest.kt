package shop.whitezia.client.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountApiRoutingTest {
    @Test
    fun keepsPrimaryThenFallbackAndRemovesDuplicates() {
        assertEquals(
            listOf("https://api.whitezia.ru/api", "https://whitezia.su/api"),
            accountApiBaseCandidates(
                primary = " https://api.whitezia.ru/api/ ",
                fallback = "https://whitezia.su/api/",
            ),
        )
        assertEquals(
            listOf("https://api.whitezia.ru/api"),
            accountApiBaseCandidates(
                primary = "https://api.whitezia.ru/api",
                fallback = "https://api.whitezia.ru/api/",
            ),
        )
    }
}
