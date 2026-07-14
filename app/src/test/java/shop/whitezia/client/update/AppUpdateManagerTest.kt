package shop.whitezia.client.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun parsesValidRelease() {
        val release = parseAppRelease(
            JSONObject(
                """
                {
                  "application_id": "shop.whitezia.client",
                  "channel": "production",
                  "version_code": 24,
                  "version_name": "1.5.7.8",
                  "min_supported_version_code": 22,
                  "mandatory": false,
                  "apk_url": "https://api.whitezia.ru/api/app/releases/android/apk",
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "certificate_sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "size_bytes": 12345,
                  "release_notes": ["Исправлена цепочка", "Добавлено обновление"]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(24, release.versionCode)
        assertEquals("1.5.7.8", release.versionName)
        assertEquals(2, release.releaseNotes.size)
        assertEquals("production", release.channel)
    }

    @Test
    fun rejectsNonHttpsAPK() {
        val json = JSONObject()
            .put("application_id", "shop.whitezia.client")
            .put("channel", "production")
            .put("version_code", 24)
            .put("version_name", "1.5.7.8")
            .put("apk_url", "http://api.whitezia.ru/update.apk")
            .put("sha256", "a".repeat(64))
            .put("certificate_sha256", "b".repeat(64))
            .put("size_bytes", 12345)

        assertThrows(IllegalArgumentException::class.java) {
            parseAppRelease(json)
        }
    }

    @Test
    fun rejectsInvalidHash() {
        val json = JSONObject()
            .put("application_id", "shop.whitezia.client")
            .put("channel", "production")
            .put("version_code", 24)
            .put("version_name", "1.5.7.8")
            .put("apk_url", "https://api.whitezia.ru/update.apk")
            .put("sha256", "invalid")
            .put("certificate_sha256", "b".repeat(64))
            .put("size_bytes", 12345)

        assertThrows(IllegalArgumentException::class.java) {
            parseAppRelease(json)
        }
    }

    @Test
    fun rejectsOtherReleaseTarget() {
        val release = parseAppRelease(
            JSONObject()
                .put("application_id", "shop.whitezia.client")
                .put("channel", "production")
                .put("version_code", 24)
                .put("version_name", "1.5.7.8")
                .put("apk_url", "https://api.whitezia.ru/api/app/releases/android/24/apk")
                .put("sha256", "a".repeat(64))
                .put("certificate_sha256", "b".repeat(64))
                .put("size_bytes", 12345),
        )

        assertThrows(IllegalArgumentException::class.java) {
            validateAppReleaseTarget(
                release,
                AppUpdateTarget(
                    applicationId = "shop.whitezia.client.debug",
                    channel = "debug",
                    certificateSha256 = "c".repeat(64),
                ),
            )
        }
    }

    @Test
    fun manualCheckReopensDismissedOptionalUpdate() {
        val release = AppRelease(
            applicationId = "shop.whitezia.client",
            channel = "production",
            versionCode = 25,
            versionName = "1.5.7.9",
            minSupportedVersionCode = 0,
            mandatory = false,
            apkUrl = "https://api.whitezia.ru/api/app/releases/android/25/apk",
            sha256 = "a".repeat(64),
            certificateSha256 = "b".repeat(64),
            sizeBytes = 12345,
            releaseNotes = emptyList(),
        )

        assertEquals(
            false,
            shouldOfferUpdate(
                release = release,
                currentVersionCode = 24,
                dismissedRecently = true,
                manualCheck = false,
            ),
        )
        assertEquals(
            true,
            shouldOfferUpdate(
                release = release,
                currentVersionCode = 24,
                dismissedRecently = true,
                manualCheck = true,
            ),
        )
    }
}
