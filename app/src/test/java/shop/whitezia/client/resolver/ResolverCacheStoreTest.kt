package shop.whitezia.client.resolver

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.rules.TemporaryFolder
import shop.whitezia.client.model.WhiteZiaSettings

class ResolverCacheStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun migratesExistingCacheWithoutDroppingLegacyKeysOrIncrementingInService() {
        val file = File(temporary.root, "resolver.properties")
        val service = ResolverCacheStore(file, mapOf(
            "resolver_benchmark_launch_count" to 9,
            "cached_resolvers" to "176.59.127.146",
            "auto_tune_winner_config.mts" to "preserved",
        ), incrementLaunchCount = false)
        assertEquals(9, service.launchCount)
        assertEquals(listOf("176.59.127.146"), service.readCachedResolvers("mts") { true })
        assertEquals("preserved", service.readAutoTuneWinner("mts"))
        assertEquals(10, ResolverCacheStore(file).launchCount)
        assertEquals(10, service.launchCount)
        assertEquals("preserved", ResolverCacheStore(file, incrementLaunchCount = false).readAutoTuneWinner("mts"))
    }

    @Test
    fun serviceSeesUiLaunchesAndUiSeesWinnerFromSeparateJvmProcesses() {
        val file = File(temporary.root, "resolver.properties")
        val service = ResolverCacheStore(file, incrementLaunchCount = false)
        fork(file, "launch", "10")
        assertEquals(10, service.launchCount)
        service.markBenchmarkAttempted("mts", listOf("176.59.127.146"))
        fork(file, "winner", "0")
        assertEquals("yandex", service.readBenchmarkWinner("mts", listOf("176.59.127.146")))
        assertEquals(1, service.readBenchmarkLastLaunchBucket("mts", listOf("176.59.127.146")))
        assertEquals(10, service.launchCount)
    }

    @Test
    fun concurrentUiLaunchesAreNotLostAcrossProcesses() {
        val file = File(temporary.root, "resolver.properties")
        val service = ResolverCacheStore(file, incrementLaunchCount = false)
        val first = process(file, "launch", "10").start()
        val second = process(file, "launch", "10").start()
        assertTrue(first.waitFor(30, TimeUnit.SECONDS))
        assertTrue(second.waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, first.exitValue())
        assertEquals(0, second.exitValue())
        assertEquals(20, service.launchCount)
    }

    private fun fork(file: File, action: String, count: String) {
        val child = process(file, action, count).start()
        assertTrue(child.waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, child.exitValue())
    }

    private fun process(file: File, action: String, count: String): ProcessBuilder {
        val classes = listOf(ResolverCacheProcessProbe::class.java, ResolverCacheStore::class.java,
            WhiteZiaSettings::class.java, SharedPreferences::class.java, Unit::class.java)
        val classpath = classes.map { File(it.protectionDomain.codeSource.location.toURI()).path }
            .distinct().joinToString(File.pathSeparator)
        return ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
            "-cp", classpath, ResolverCacheProcessProbe::class.java.name, file.path, action, count).inheritIO()
    }

    @Test
    fun serviceReadsLaunchCountWithoutIncrementingIt() {
        val preferences = memoryResolverPreferences()
        val service = ResolverCacheStore(preferences, incrementLaunchCount = false)
        assertEquals(0, service.launchCount)
        val ui = ResolverCacheStore(preferences)
        assertEquals(1, ui.launchCount)
        assertEquals(1, service.launchCount)
        assertEquals(1, ResolverCacheStore(preferences, incrementLaunchCount = false).launchCount)
        assertEquals(2, ResolverCacheStore(preferences).launchCount)
        assertEquals(2, service.launchCount)
        assertEquals(1, ui.launchCount)
    }

    @Test
    fun serviceBenchmarkUsesUiLaunchBucket() {
        val preferences = memoryResolverPreferences()
        val service = ResolverCacheStore(preferences, incrementLaunchCount = false)
        repeat(10) { ResolverCacheStore(preferences) }
        service.markBenchmarkAttempted("mts", listOf("176.59.127.146"))
        assertEquals(1, service.readBenchmarkLastLaunchBucket("mts", listOf("176.59.127.146")))
        assertEquals(10, service.launchCount)
    }
}

internal object ResolverCacheProcessProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val file = File(args[0])
        if (args[1] == "launch") {
            repeat(args[2].toInt()) { ResolverCacheStore(file) }
        } else {
            ResolverCacheStore(file, incrementLaunchCount = false).saveBenchmarkWinner(
                "mts", listOf("176.59.127.146"), "yandex", listOf("77.88.8.8"),
            )
        }
    }
}

internal fun memoryResolverPreferences(): SharedPreferences {
    val values = mutableMapOf<String, Any>()
    val editor = Proxy.newProxyInstance(
        SharedPreferences.Editor::class.java.classLoader,
        arrayOf(SharedPreferences.Editor::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "putString", "putInt" -> {
                val key = args!![0] as String
                val value = args[1]
                if (value == null) values.remove(key) else values[key] = value
                proxy
            }
            "remove" -> { values.remove(args!![0] as String); proxy }
            "apply" -> null
            "commit" -> true
            else -> error("Unexpected editor method ${method.name}")
        }
    } as SharedPreferences.Editor
    return Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getInt", "getString" -> values[args!![0]] ?: args[1]
            "contains" -> values.containsKey(args!![0])
            "edit" -> editor
            else -> error("Unexpected preferences method ${method.name}")
        }
    } as SharedPreferences
}
