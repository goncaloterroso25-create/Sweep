package dev.sweep.core

import dev.sweep.core.model.AppScanResult
import dev.sweep.core.model.InstalledApp
import dev.sweep.core.scan.UnusedAppPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnusedAppPolicyTest {

    private fun app(
        pkg: String,
        label: String = pkg,
        system: Boolean = false,
        installedDaysAgo: Int = 400,
        lastUsedDaysAgo: Int? = null,
        bytes: Long = 50_000_000,
    ) = InstalledApp(
        packageName = pkg,
        label = label,
        isSystemApp = system,
        installedAt = daysAgo(installedDaysAgo),
        lastUsedAt = lastUsedDaysAgo?.let { daysAgo(it) },
        appBytes = bytes,
    )

    @Test
    fun `apps idle past the threshold are surfaced, recent ones are not`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(
                app("com.idle", lastUsedDaysAgo = 210),
                app("com.recent", lastUsedDaysAgo = 3),
                app("com.borderline", lastUsedDaysAgo = 90),
            ),
            thresholdDays = 90,
            now = NOW,
        )
        assertEquals(listOf("com.idle", "com.borderline"), result.unused.map { it.app.packageName })
        assertEquals(210, result.unused.first().daysSinceUse)
        assertTrue(result.unknownUsage.isEmpty())
    }

    @Test
    fun `system apps are excluded because they cannot be uninstalled normally`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(
                app("com.android.systemui", system = true, lastUsedDaysAgo = 900),
                app("com.android.quiet", system = true, lastUsedDaysAgo = null),
            ),
            thresholdDays = 30,
            now = NOW,
        )
        assertTrue(result.unused.isEmpty())
        assertTrue(result.unknownUsage.isEmpty())
    }

    @Test
    fun `an app with no usage record is never counted as unused`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(app("com.forgotten", installedDaysAgo = 500, lastUsedDaysAgo = null)),
            thresholdDays = 90,
            now = NOW,
        )
        assertTrue(
            "no usage record is not evidence of disuse",
            result.unused.isEmpty(),
        )
        assertEquals(listOf("com.forgotten"), result.unknownUsage.map { it.app.packageName })
        assertEquals(500, result.unknownUsage.single().installedDays)
    }

    @Test
    fun `a zero last-used timestamp counts as unknown, not as ancient`() {
        val zeroStamp = InstalledApp(
            packageName = "com.zero",
            label = "Zero",
            isSystemApp = false,
            installedAt = daysAgo(300),
            lastUsedAt = 0L,
            appBytes = 1_000,
        )
        val result = UnusedAppPolicy.find(listOf(zeroStamp), thresholdDays = 30, now = NOW)
        assertTrue(result.unused.isEmpty())
        assertEquals(listOf("com.zero"), result.unknownUsage.map { it.app.packageName })
    }

    @Test
    fun `an app used within the threshold never appears anywhere`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(app("com.daily", lastUsedDaysAgo = 0, installedDaysAgo = 900)),
            thresholdDays = 30,
            now = NOW,
        )
        assertTrue(result.unused.isEmpty())
        assertTrue(
            "a real timestamp must keep an app out of the unknown list",
            result.unknownUsage.isEmpty(),
        )
    }

    @Test
    fun `a recently installed app with no history is not listed at all`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(app("com.new", installedDaysAgo = 2, lastUsedDaysAgo = null)),
            thresholdDays = 90,
            now = NOW,
        )
        assertTrue(result.unused.isEmpty())
        assertTrue(result.unknownUsage.isEmpty())
    }

    @Test
    fun `excluded packages are never suggested again`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(
                app("com.keep", lastUsedDaysAgo = 500),
                app("com.go", lastUsedDaysAgo = 500),
                app("com.keep.unknown", lastUsedDaysAgo = null, installedDaysAgo = 500),
            ),
            thresholdDays = 30,
            now = NOW,
            excludedPackages = setOf("com.keep", "com.keep.unknown"),
        )
        assertEquals(listOf("com.go"), result.unused.map { it.app.packageName })
        assertTrue(result.unknownUsage.isEmpty())
    }

    @Test
    fun `unused apps sort by idle time then size, unknown apps by size`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(
                app("com.small", lastUsedDaysAgo = 100, bytes = 1_000_000),
                app("com.unknown.small", lastUsedDaysAgo = null, bytes = 2_000_000),
                app("com.older", lastUsedDaysAgo = 300, bytes = 5_000),
                app("com.unknown.big", lastUsedDaysAgo = null, bytes = 900_000_000),
                app("com.big", lastUsedDaysAgo = 100, bytes = 900_000_000),
            ),
            thresholdDays = 90,
            now = NOW,
        )
        assertEquals(
            listOf("com.older", "com.big", "com.small"),
            result.unused.map { it.app.packageName },
        )
        assertEquals(
            listOf("com.unknown.big", "com.unknown.small"),
            result.unknownUsage.map { it.app.packageName },
        )
    }

    @Test
    fun `apps of unknown usage add nothing to the reclaimable total`() {
        val apps = listOf(
            app("com.idle", lastUsedDaysAgo = 200, bytes = 100),
            app("com.unknown", lastUsedDaysAgo = null, installedDaysAgo = 400, bytes = 900),
            app("com.active", lastUsedDaysAgo = 1, bytes = 7_000),
        )
        val evaluated = UnusedAppPolicy.find(apps, thresholdDays = 90, now = NOW)
        val result = AppScanResult(
            apps = apps,
            unused = evaluated.unused,
            unknownUsage = evaluated.unknownUsage,
            thresholdDays = 90,
            hasUsageAccess = true,
        )

        assertEquals(1, result.unused.size)
        assertEquals(100L, result.reclaimableBytes)
        assertEquals(900L, result.unknownBytes)
        assertTrue(result.hasAnyUsageHistory)
    }

    @Test
    fun `a device that reports no usage at all is detectable`() {
        val apps = listOf(
            app("com.a", lastUsedDaysAgo = null, installedDaysAgo = 400),
            app("com.b", lastUsedDaysAgo = null, installedDaysAgo = 400),
        )
        val evaluated = UnusedAppPolicy.find(apps, thresholdDays = 90, now = NOW)
        val result = AppScanResult(apps, evaluated.unused, evaluated.unknownUsage, 90, true)

        assertTrue(result.unused.isEmpty())
        assertEquals(0L, result.reclaimableBytes)
        assertTrue("the screen has to be able to say so", !result.hasAnyUsageHistory)
    }

    @Test
    fun `total size adds app, data and cache`() {
        val app = InstalledApp(
            packageName = "com.x", label = "X", isSystemApp = false,
            installedAt = daysAgo(100), lastUsedAt = daysAgo(100),
            appBytes = 100, dataBytes = 20, cacheBytes = 3,
        )
        assertEquals(123L, app.totalBytes)
    }
}
