package dev.sweep.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sweep.SweepApplication
import dev.sweep.core.android.DeviceStorageInfo
import dev.sweep.core.android.FileAccess
import dev.sweep.core.android.PermissionStatus
import dev.sweep.core.android.ReminderWorker
import dev.sweep.core.data.LastScan
import dev.sweep.core.data.MotionPreference
import dev.sweep.core.data.SweepSettings
import dev.sweep.core.model.AppScanResult
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.FailedDeletion
import dev.sweep.core.model.ScanResult
import dev.sweep.core.model.ScanUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

enum class Stage {
    /** Nothing scanned yet this session, or the previous result was cleared. */
    IDLE,
    SCANNING,
    RESULTS,
    CLEANING,
    /** A cleanup finished and its receipt is showing on Home. */
    DONE,
}

data class CleanupSummary(
    /** Bytes the deleter confirmed gone. Failures and files that were already missing add nothing. */
    val bytesRecovered: Long,
    val filesRemoved: Int,
    val alreadyGone: Int,
    val failed: List<FailedDeletion>,
    val categories: List<CleanupCategory>,
    /** Android's free-space figure before the delete, and after it, measured again. */
    val freeBytesBefore: Long,
    val freeBytesAfter: Long,
)

/** Live progress through a delete, counted only from confirmed outcomes. */
data class CleaningProgress(
    val done: Int = 0,
    val total: Int = 0,
    val recoveredBytes: Long = 0L,
)

/** Computed once per change rather than on every read, because lists read it per frame. */
data class SelectionSummary(
    val count: Int = 0,
    val bytes: Long = 0L,
    val bytesByCategory: Map<CleanupCategory, Long> = emptyMap(),
    val countByCategory: Map<CleanupCategory, Int> = emptyMap(),
) {
    companion object {
        val NONE = SelectionSummary()
    }
}

/** A factual line about the last thing that happened on the Apps screen. */
data class AppNotice(val text: String, val positive: Boolean)

data class SweepUiState(
    val stage: Stage = Stage.IDLE,
    val permissions: PermissionStatus = PermissionStatus(FileAccess.NONE, false),
    val storage: DeviceStorageInfo = DeviceStorageInfo.UNKNOWN,
    val settings: SweepSettings = SweepSettings(),
    /** False until DataStore has emitted once. The splash screen stays up until then. */
    val settingsLoaded: Boolean = false,
    /** False until permissions and storage have been read once. Also holds the splash screen. */
    val environmentLoaded: Boolean = false,
    val lastScan: LastScan? = null,
    val progress: ScanUpdate.Progress? = null,
    /** Bumped on every progress update, so the tally can tell how busy the scanner is. */
    val scanPulse: Int = 0,
    val result: ScanResult? = null,
    val selectedPaths: Set<String> = emptySet(),
    val selection: SelectionSummary = SelectionSummary.NONE,
    val cleaning: CleaningProgress? = null,
    val cleanup: CleanupSummary? = null,
    val apps: AppScanResult? = null,
    val appsLoading: Boolean = false,
    val appNotice: AppNotice? = null,
    /** The one cache Sweep is allowed to clear: its own. */
    val ownCacheBytes: Long = 0L,
    /** True from the moment the user opens the Usage Access settings screen. */
    val usageAccessRequested: Boolean = false,
    /**
     * True once the user has opened the Usage Access screen and come back without it. Usually
     * Android's Restricted Settings, so the help opens itself for exactly these people.
     */
    val usageAccessRefused: Boolean = false,
) {
    val items: List<CleanupItem> get() = result?.items.orEmpty()

    fun itemsIn(category: CleanupCategory): List<CleanupItem> =
        result?.byCategory?.get(category).orEmpty()

    val selectedItems: List<CleanupItem> get() = items.filter { it.path in selectedPaths }

    val reducedMotion: Boolean get() = settings.motion == MotionPreference.REDUCED
}

/**
 * Owns everything the screens read. One for the whole app: the scan result is large and every
 * screen below Home is a filtered view of it, so passing it through navigation would be slower
 * and more fragile.
 */
class SweepViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SweepApplication).repository
    private val settingsStore = (application as SweepApplication).settings

    private val _state = MutableStateFlow(SweepUiState())
    val state: StateFlow<SweepUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    /** Cooperative stop: the scanner finishes early and still delivers what it found. */
    private val stopScan = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update { it.copy(settings = settings, settingsLoaded = true) }
            }
        }
        viewModelScope.launch {
            settingsStore.lastScan.collect { last -> _state.update { it.copy(lastScan = last) } }
        }
        refreshEnvironment()
    }

    /** Re-read permissions and storage. Called on every resume, since both change in Settings. */
    fun refreshEnvironment() {
        viewModelScope.launch {
            val (permissions, storage) = withContext(Dispatchers.IO) {
                repository.permissions() to repository.storage()
            }
            _state.update {
                it.copy(
                    permissions = permissions,
                    storage = storage,
                    environmentLoaded = true,
                    usageAccessRefused = it.usageAccessRequested && !permissions.hasUsageAccess,
                )
            }
        }
        // Reminders stay quiet for a while after the app is opened: the user already knows.
        viewModelScope.launch { settingsStore.recordAppOpened(System.currentTimeMillis()) }
    }

    fun noteUsageAccessRequested() = _state.update { it.copy(usageAccessRequested = true) }

    private fun refreshStorage() {
        viewModelScope.launch {
            val storage = withContext(Dispatchers.IO) { repository.storage() }
            _state.update { it.copy(storage = storage) }
        }
    }

    // ---- scanning ---------------------------------------------------------------------------

    fun startScan() {
        if (!_state.value.permissions.canScanFiles) return
        if (_state.value.stage == Stage.CLEANING) return
        scanJob?.cancel()
        stopScan.set(false)
        _state.update {
            it.copy(
                stage = Stage.SCANNING,
                progress = null,
                result = null,
                selectedPaths = emptySet(),
                selection = SelectionSummary.NONE,
                cleanup = null,
                cleaning = null,
            )
        }
        refreshStorage()
        scanJob = viewModelScope.launch {
            val settings = _state.value.settings
            repository.scanFiles(
                config = settings.toScanConfig(),
                exclusions = settings.excludedPaths,
                stopRequested = stopScan::get,
            ).collect { update ->
                when (update) {
                    is ScanUpdate.Progress -> _state.update {
                        it.copy(progress = update, scanPulse = it.scanPulse + 1)
                    }
                    is ScanUpdate.Complete -> {
                        refreshStorage()
                        // The only figure a background reminder may quote later is one a real
                        // scan measured, so it is recorded here.
                        settingsStore.recordScanResult(update.result.totalFoundBytes, System.currentTimeMillis())
                        _state.update {
                            val preselected = update.result.items
                                .filter { item -> item.isSafeSuggestion }
                                .mapTo(HashSet()) { item -> item.path }
                            it.copy(
                                stage = Stage.RESULTS,
                                result = update.result,
                                progress = null,
                                selectedPaths = preselected,
                                selection = summarise(update.result.items, preselected),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Keeps what the scan has found so far rather than throwing it away. */
    fun stopScan() {
        stopScan.set(true)
    }

    // ---- selection --------------------------------------------------------------------------

    fun toggle(path: String) = select { if (path in it) it - path else it + path }

    fun setSelected(paths: Collection<String>, selected: Boolean) =
        select { if (selected) it + paths else it - paths.toSet() }

    /** Only what [dev.sweep.core.scan.SafetyPolicy] marked as redundant. */
    fun selectSuggested(category: CleanupCategory) = select { current ->
        current + _state.value.itemsIn(category).filter { it.isSafeSuggestion }.map { it.path }
    }

    fun clearSelection(category: CleanupCategory? = null) = select { current ->
        if (category == null) emptySet()
        else current - _state.value.itemsIn(category).map { it.path }.toSet()
    }

    private inline fun select(change: (Set<String>) -> Set<String>) = _state.update { state ->
        val next = change(state.selectedPaths)
        state.copy(selectedPaths = next, selection = summarise(state.items, next))
    }

    private fun summarise(items: List<CleanupItem>, selected: Set<String>): SelectionSummary {
        if (selected.isEmpty()) return SelectionSummary.NONE
        var count = 0
        var bytes = 0L
        val byCategory = HashMap<CleanupCategory, Long>()
        val countByCategory = HashMap<CleanupCategory, Int>()
        for (item in items) {
            if (item.path !in selected) continue
            count++
            bytes += item.size
            byCategory[item.category] = (byCategory[item.category] ?: 0L) + item.size
            countByCategory[item.category] = (countByCategory[item.category] ?: 0) + 1
        }
        return SelectionSummary(count, bytes, byCategory, countByCategory)
    }

    // ---- exclusions -------------------------------------------------------------------------

    fun excludeItem(item: CleanupItem) {
        viewModelScope.launch { settingsStore.excludePath(item.path) }
        removeFromResult(listOf(item.path))
    }

    fun excludeApp(packageName: String) {
        viewModelScope.launch {
            settingsStore.excludePackage(packageName)
            reloadApps()
        }
    }

    fun clearExclusions() = viewModelScope.launch { settingsStore.clearExclusions() }

    // ---- apps -------------------------------------------------------------------------------

    fun loadApps(force: Boolean = false) {
        if (!force && (_state.value.apps != null || _state.value.appsLoading)) return
        _state.update { it.copy(appsLoading = true) }
        viewModelScope.launch { reloadApps() }
    }

    private suspend fun reloadApps() {
        val settings = _state.value.settings
        val result = repository.loadApps(settings.unusedAppThresholdDays, settings.excludedPackages)
        _state.update { it.copy(apps = result, appsLoading = false) }
    }

    fun refreshOwnCache() = viewModelScope.launch {
        _state.update { it.copy(ownCacheBytes = repository.ownCacheBytes()) }
    }

    fun clearOwnCache() = viewModelScope.launch {
        val before = _state.value.ownCacheBytes
        repository.clearOwnCache()
        val after = repository.ownCacheBytes()
        _state.update {
            it.copy(
                ownCacheBytes = after,
                appNotice = if (after < before) {
                    AppNotice("Sweep cleared ${(before - after).let(dev.sweep.core.model.ByteFormat::short)} of its own cache.", true)
                } else {
                    it.appNotice
                },
            )
        }
    }

    suspend fun appIcon(packageName: String) = repository.appInventory.icon(packageName)

    /**
     * The system uninstall dialog returned. Nothing here trusts its word: the package is looked up
     * directly, so Sweep only says an app is gone when Android agrees. Cancelling says nothing.
     */
    fun onUninstallReturned(packageName: String, label: String, resultCode: Int) {
        _state.update { it.copy(appsLoading = true) }
        viewModelScope.launch {
            reloadApps()
            val stillInstalled = withContext(Dispatchers.IO) { repository.isInstalled(packageName) }
            _state.update {
                it.copy(
                    appNotice = when {
                        !stillInstalled -> AppNotice("$label was uninstalled.", true)
                        resultCode == UNINSTALL_FAILED -> AppNotice("Android could not uninstall $label.", false)
                        else -> null
                    }
                )
            }
        }
        refreshEnvironment()
    }

    fun reportUninstallUnavailable(label: String) = _state.update {
        it.copy(appNotice = AppNotice("This device would not open an uninstall screen for $label.", false))
    }

    /**
     * The user came back from an app's storage page in Android's settings. Sweep re-measures that
     * app and says what changed, if anything did. Android did the clearing, and the notice says so.
     */
    fun onAppStorageReturned(packageName: String, label: String, cacheBefore: Long) {
        viewModelScope.launch {
            reloadApps()
            val after = _state.value.apps?.apps?.firstOrNull { it.packageName == packageName }?.cacheBytes
            _state.update {
                it.copy(
                    appNotice = when {
                        after == null -> it.appNotice
                        after < cacheBefore -> AppNotice(
                            "Android cleared ${dev.sweep.core.model.ByteFormat.short(cacheBefore - after)} " +
                                "of $label's cache.",
                            true,
                        )
                        else -> it.appNotice
                    }
                )
            }
        }
    }

    fun dismissAppNotice() = _state.update { it.copy(appNotice = null) }

    // ---- cleanup ----------------------------------------------------------------------------

    fun runCleanup() {
        val current = _state.value
        val targets = current.selectedItems
        if (targets.isEmpty() || current.stage == Stage.CLEANING) return

        val freeBefore = current.storage.freeBytes
        val categories = targets.map { it.category }.distinct().sortedBy { it.ordinal }
        _state.update {
            it.copy(
                stage = Stage.CLEANING,
                cleaning = CleaningProgress(total = targets.size),
            )
        }

        viewModelScope.launch {
            val outcome = repository.delete(targets) { done, total, recovered ->
                _state.update {
                    it.copy(cleaning = it.cleaning?.copy(done = done, total = total, recoveredBytes = recovered))
                }
            }
            // The headline number is Android's, measured again, never an estimate.
            val storageAfter = withContext(Dispatchers.IO) { repository.storage() }
            removeFromResult(outcome.deletedPaths)
            _state.update {
                it.copy(
                    stage = Stage.DONE,
                    storage = storageAfter,
                    cleaning = null,
                    cleanup = CleanupSummary(
                        bytesRecovered = outcome.bytesRecovered,
                        filesRemoved = outcome.deletedCount,
                        alreadyGone = outcome.alreadyGone,
                        failed = outcome.failed,
                        categories = categories,
                        freeBytesBefore = freeBefore,
                        freeBytesAfter = storageAfter.freeBytes,
                    ),
                )
            }
        }
    }

    fun dismissReceipt() = _state.update {
        it.copy(
            stage = if (it.items.isEmpty()) Stage.IDLE else Stage.RESULTS,
            cleanup = null,
            result = if (it.items.isEmpty()) null else it.result,
        )
    }

    private fun removeFromResult(paths: List<String>) {
        if (paths.isEmpty()) return
        val removed = paths.toSet()
        _state.update { state ->
            val result = state.result ?: return@update state
            val remaining = result.copy(items = result.items.filterNot { it.path in removed })
            val selected = state.selectedPaths - removed
            state.copy(result = remaining, selectedPaths = selected, selection = summarise(remaining.items, selected))
        }
        // What the last scan found has changed, and the reminder must not quote the old figure.
        val remainingBytes = _state.value.result?.totalFoundBytes ?: 0L
        viewModelScope.launch { settingsStore.updateScanFoundBytes(remainingBytes) }
    }

    // ---- settings ---------------------------------------------------------------------------

    fun setOldFileThreshold(days: Int) = viewModelScope.launch { settingsStore.setOldFileThreshold(days) }
    fun setLargeFileThreshold(bytes: Long) = viewModelScope.launch { settingsStore.setLargeFileThreshold(bytes) }
    fun setScreenshotThreshold(days: Int) = viewModelScope.launch { settingsStore.setScreenshotThreshold(days) }

    fun setUnusedAppThreshold(days: Int) = viewModelScope.launch {
        settingsStore.setUnusedAppThreshold(days)
        reloadApps()
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { settingsStore.setHaptics(enabled) }
    fun setMotion(preference: MotionPreference) = viewModelScope.launch { settingsStore.setMotion(preference) }

    // ---- reminders --------------------------------------------------------------------------

    /** Each switch writes the setting, then brings the schedule in line with it. */
    fun setCleanupReminders(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setCleanupReminders(enabled)
        syncReminderWork()
    }

    fun setUnusedAppReminders(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setUnusedAppReminders(enabled)
        syncReminderWork()
    }

    fun setReminderThreshold(bytes: Long) = viewModelScope.launch { settingsStore.setReminderThreshold(bytes) }

    private suspend fun syncReminderWork() {
        ReminderWorker.sync(getApplication(), settingsStore.currentSettings().anyReminderEnabled)
    }

    private companion object {
        /** `Activity.RESULT_FIRST_USER`, which ACTION_UNINSTALL_PACKAGE uses to mean "failed". */
        const val UNINSTALL_FAILED = 1
    }
}
