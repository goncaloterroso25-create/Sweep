package dev.sweep.core

import dev.sweep.core.model.InstalledApp
import dev.sweep.core.model.Reason
import dev.sweep.core.scan.UnusedAppPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(listOf("com.idle", "com.borderline"), result.map { it.app.packageName })
        assertEquals(210, result.first().daysSinceUse)
    }

    @Test
    fun `system apps are excluded because they cannot be uninstalled normally`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(app("com.android.systemui", system = true, lastUsedDaysAgo = 900)),
            thresholdDays = 30,
            now = NOW,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `no usage record only counts once the app has been installed long enough`() {
        val fresh = UnusedAppPolicy.find(
            apps = listOf(app("com.new", installedDaysAgo = 2, lastUsedDaysAgo = null)),
            thresholdDays = 90,
            now = NOW,
        )
        assertTrue("a freshly installed app must not be called unused", fresh.isEmpty())

        val old = UnusedAppPolicy.find(
            apps = listOf(app("com.forgotten", installedDaysAgo = 500, lastUsedDaysAgo = null)),
            thresholdDays = 90,
            now = NOW,
        ).single()
        assertNull(old.daysSinceUse)
        assertTrue(old.reasons.contains(Reason.NoUsageRecord))
    }

    @Test
    fun `excluded packages are never suggested again`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(app("com.keep", lastUsedDaysAgo = 500), app("com.go", lastUsedDaysAgo = 500)),
            thresholdDays = 30,
            now = NOW,
            excludedPackages = setOf("com.keep"),
        )
        assertEquals(listOf("com.go"), result.map { it.app.packageName })
    }

    @Test
    fun `apps with no usage record sort above apps with one, then by size`() {
        val result = UnusedAppPolicy.find(
            apps = listOf(
                app("com.small", lastUsedDaysAgo = 100, bytes = 1_000_000),
                app("com.unknown", lastUsedDaysAgo = null, installedDaysAgo = 400),
                app("com.big", lastUsedDaysAgo = 100, bytes = 900_000_000),
            ),
            thresholdDays = 90,
            now = NOW,
        )
        assertEquals(listOf("com.unknown", "com.big", "com.small"), result.map { it.app.packageName })
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
