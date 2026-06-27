package shop.whitezia.client.scan

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanResolverChunkerTest {
    @Test
    fun chunkResolversRoundRobinDistributesResolversAcrossWorkers() {
        val chunks = chunkResolversRoundRobin(
            resolvers = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "4.4.4.4", "5.5.5.5"),
            requestedWorkerCount = 2,
        )

        assertEquals(
            listOf(
                listOf("1.1.1.1", "9.9.9.9", "5.5.5.5"),
                listOf("8.8.8.8", "4.4.4.4"),
            ),
            chunks,
        )
    }

    @Test
    fun chunkResolversRoundRobinCapsWorkersAtResolverCount() {
        val chunks = chunkResolversRoundRobin(
            resolvers = listOf("1.1.1.1", "8.8.8.8"),
            requestedWorkerCount = 8,
        )

        assertEquals(listOf(listOf("1.1.1.1"), listOf("8.8.8.8")), chunks)
    }

    @Test
    fun normalizeScanResolverTextStripsPortsBeforeScanning() {
        val validation = WhiteZiaScannerResultStore.normalizeScanResolverText(
            """
            1.1.1.1:52
            8.8.8.8:53
            9.9.9.9:5353
            192.168.10.0/30:5300
            [2001:4860:4860::8888]:5353
            """.trimIndent(),
        )

        assertEquals(emptyList<String>(), validation.invalidEntries)
        assertEquals(
            listOf(
                "1.1.1.1",
                "8.8.8.8",
                "9.9.9.9",
                "192.168.10.0/30",
                "2001:4860:4860:0:0:0:0:8888",
            ),
            validation.normalizedResolvers,
        )
    }

    @Test
    fun normalizeScanResolverTextAcceptsCrLfIpv4DefaultPortLists() {
        val validation = WhiteZiaScannerResultStore.normalizeScanResolverText(
            "2.144.2.72:53\r\n2.144.2.154:53\r\n2.144.6.75:53\r\n",
        )

        assertEquals(emptyList<String>(), validation.invalidEntries)
        assertEquals(
            listOf("2.144.2.72", "2.144.2.154", "2.144.6.75"),
            validation.normalizedResolvers,
        )
    }

    @Test
    fun normalizeScanResolverLinesStreamsLargeDefaultPortLists() {
        val lines = generateSequence(0) { index -> index + 1 }
            .take(5_000)
            .map { index -> "10.${index / 256 / 256}.${index / 256 % 256}.${index % 256}:53" }

        val validation = WhiteZiaScannerResultStore.normalizeScanResolverLines(lines)

        assertEquals(emptyList<String>(), validation.invalidEntries)
        assertEquals(5_000, validation.normalizedResolvers.size)
        assertEquals("10.0.0.0", validation.normalizedResolvers.first())
        assertEquals("10.0.19.135", validation.normalizedResolvers.last())
    }

    @Test
    fun writePendingScanResolverFileStreamsWithoutKeepingPendingList() {
        val outputFile = File.createTempFile("scan-resolvers", ".txt").apply {
            deleteOnExit()
        }
        val lines = sequenceOf(
            "1.1.1.1:53",
            "8.8.8.8:53",
            "1.1.1.1:53",
            "bad resolver",
            "9.9.9.9:5353",
        )

        val summary = WhiteZiaScannerResultStore.writePendingScanResolverFile(
            lines = lines,
            outputFile = outputFile,
            excludedResolvers = setOf("8.8.8.8"),
        )

        assertEquals(
            ScanResolverFileSummary(
                totalResolverCount = 3,
                pendingResolverCount = 2,
                alreadyValidResolverCount = 1,
                invalidEntryCount = 1,
            ),
            summary,
        )
        assertEquals("1.1.1.1\n9.9.9.9", outputFile.readText(Charsets.UTF_8))
    }

    @Test
    fun defaultResolverAssetContainsValidUniqueResolvers() {
        val assetFile = listOf(
            File("src/main/assets/default_resolvers.txt"),
            File("app/src/main/assets/default_resolvers.txt"),
        ).first(File::isFile)
        val rawResolvers = assetFile.readLines(Charsets.UTF_8)
            .map(String::trim)
            .filter(String::isNotEmpty)

        val validation = WhiteZiaScannerResultStore.normalizeScanResolverLines(rawResolvers.asSequence())

        assertEquals(emptyList<String>(), validation.invalidEntries)
        assertEquals(rawResolvers.distinct(), rawResolvers)
        assertEquals(
            listOf(
                "10.112.250.2",
                "10.112.248.234",
                "10.112.250.10",
                "10.112.248.238",
            ),
            validation.normalizedResolvers,
        )
    }
}
