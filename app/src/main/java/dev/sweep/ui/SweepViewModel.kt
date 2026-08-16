package dev.sweep.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sweep.SweepApplication
import dev.sweep.core.android.DeviceStorageInfo
import dev.sweep.core.android.PermissionStatus
import dev.sweep.core.android.FileAccess
import dev.sweep.core.android.ScanRoots
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class Stage {
    /** Nothing scanned yet, or the previous result was cleared. */
    IDLE,
    SCANNING,
    RESULTS,
    CLEANING,
    DONE,
}

data class CleanupSummary(
    val bytesRecovered: Long,
    val filesRemoved: Int,
    val alreadyGone: Int,
    val failed: List<FailedDeletion>,
    val categories: List<CleanupCategory>,
    val freeBytesBefore: Long,
    val freeBytesAfter: Long,
) {
    val nothingHappened: Boolean get() = filesRemoved == 0 && failed.isEmpty()
}

data class SweepUiState(
    val stage: Stage = Stage.IDLE,
    val permissions: PermissionStatus = PermissionStatus(FileAccess.NONE, false),
    val storage: DeviceStorageInfo = DeviceStorageInfo.UNKNOWN,
    val settings: SweepSettings = SweepSettings(),
    /** False until DataStore has emitted once, so onboarding never flashes on a return visit. */
    val settingsLoaded: Boolean = false,
    val progress: ScanUpdate.Progress? = null,
    val result: ScanResult? = null,
    val apps: AppScanResult? = null,
    val appsLoading: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val deleteProgress: Float = 0f,
    val cleanup: CleanupSummary? = null,
    val scanWasCancelled: Boolean = false,
    val scanRootLabel: String = "",
    /** The one cache Sweep is allowed to clear: its own. */
    val ownCacheBytes: Long = 0L,
) {
    val items: List<CleanupItem> get() = result?.items.orEmpty()

    val selectedItems: List<CleanupItem> get() = items.filter { it.path in selectedPaths }

    val selectedBytes: Long get() = selectedItems.sumOf { it.size }

    val selectedCount: Int get() = selectedItems.size

    val affectedCategories: List<CleanupCategory>
        get() = selectedItems.map { it.category }.distinct().sortedBy { it.ordinal }

    fun itemsIn(category: CleanupCategory): List<CleanupItem> =
        result?.byCategory?.get(category).orEmpty()

    fun selectedIn(category: CleanupCategory): List<CleanupItem> =
        itemsIn(category).filter { it.path in selectedPaths }

    val hasFindings: Boolean get() = items.isNotEmpty()

    val reducedMotion: Boolean get() = settings.motion == MotionPreference.REDUCED
}

/**
 * Owns everything the screens read. There is one of these for the whole app: the scan result is
 * large and the category screens are just filtered views of it, so passing it through navigation
 * arguments would be both slower and more fragile.
 */
class SweepViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SweepApplication).repository
    private val settingsStore = (application as SweepApplication).settings

    private val _state = MutableStateFlow(SweepUiState())
    val state: StateFlow<SweepUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var cleanupJob: Job? = null

    /**
     * Cooperative stop. Pressing Stop sets this instead of cancelling the job, so the scanner
     * finishes early and still delivers what it found.
     */
    private val stopScan = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update { it.copy(settings = settings, settingsLoaded = true) }
            }
        }
        refreshEnvironment()
    }

    /** Re-read permissions and storage. Called on every resume, because both can change in Settings. */
    fun refreshEnvironment() {
        viewModelScope.launch {
            // AppOps, StorageStatsManager and the volume walk are all binder or filesystem
            // calls. They are quick, but "quick" is not "free" and this runs on every resume.
            val snapshot = withContext(Dispatchers.IO) {
                Triple(
                    repository.permissions(),
                    repository.storage(),
                    ScanRoots.describe(repository.scanRoots()),
                )
            }
            _state.update {
                it.copy(
                    permissions = snapshot.first,
                    storage = snapshot.second,
                    scanRootLabel = snapshot.third,
                )
            }
        }
    }

    /**
     * Re-reads the storage figures alone.
     *
     * The free-space headline is the one number the user acts on, so it is refreshed at every
     * moment it could have moved — before a scan, when one finishes, after a deletion — rather
     * than only on resume. Permissions cannot change without leaving the app, so this skips them.
     */
    fun refreshStorage() {
        viewModelScope.launch {
            val storage = withContext(Dispatchers.IO) { repository.storage() }
            _state.update { it.copy(storage = storage) }
        }
    }

    // ---- scanning ---------------------------------------------------------------------------

    fun startScan() {
        if (!_state.value.permissions.canScanFiles) return
        scanJob?.cancel()
        stopScan.set(false)
        _state.update {
            it.copy(
                stage = Stage.SCANNING,
                progress = null,
                result = null,
                selectedPaths = emptySet(),
                cleanup = null,
                scanWasCancelled = false,
            )
        }
        refreshStorage()
        scanJob = viewModelScope.launch {
            val settings = _state.value.settings
            repository.scanFiles(
                config = settings.toScanConfig(),
                exclusions = settings.excludedPaths,
                stopRequested = stopScan::get,
            )
                .collect { update ->
                    when (update) {
                        is ScanUpdate.Progress -> _state.update { it.copy(progress = update) }
                        is ScanUpdate.Complete -> {
                            // A long scan can outlive the figures shown when it started.
                            refreshStorage()
                            _state.update {
                                it.copy(
                                    stage = Stage.RESULTS,
                                    result = update.result,
                                    scanWasCancelled = update.result.stoppedEarly,
                                    progress = null,
                                    selectedPaths = update.result.items
                                        .filter { item -> item.isSafeSuggestion }
                                        .map { item -> item.path }
                                        .toSet(),
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * Stopping keeps whatever the scan had already found rather than throwing it away, so a user
     * who stops a long scan still gets something useful. The job is left running for the moment
     * it takes to finish the current pass and emit its partial result.
     */
    fun cancelScan() {
        stopScan.set(true)
    }

    fun resetToIdle() {
        stopScan.set(true)
        scanJob?.cancel()
        _state.update {
            it.copy(
                stage = Stage.IDLE,
                result = null,
                progress = null,
                selectedPaths = emptySet(),
                cleanup = null,
                scanWasCancelled = false,
            )
        }
    }

    // ---- selection --------------------------------------------------------------------------

    fun toggle(path: String) = _state.update {
        val next = if (path in it.selectedPaths) it.selectedPaths - path else it.selectedPaths + path
        it.copy(selectedPaths = next)
    }

    fun setSelected(paths: Collection<String>, selected: Boolean) = _state.update {
        it.copy(
            selectedPaths = if (selected) it.selectedPaths + paths else it.selectedPaths - paths.toSet()
        )
    }

    /** Selects only what [dev.sweep.core.scan.SafetyPolicy] marked as redundant. */
    fun selectSafe(category: CleanupCategory? = null) = _state.update { state ->
        val pool = category?.let(state::itemsIn) ?: state.items
        state.copy(selectedPaths = state.selectedPaths + pool.filter { it.isSafeSuggestion }.map { it.path })
    }

    fun clearSelection(category: CleanupCategory? = null) = _state.update { state ->
        val pool = category?.let(state::itemsIn) ?: state.items
        state.copy(selectedPaths = state.selectedPaths - pool.map { it.path }.toSet())
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

    fun includePath(path: String) = viewModelScope.launch { settingsStore.includePath(path) }

    fun includePackage(packageName: String) =
        viewModelScope.launch { settingsStore.includePackage(packageName) }

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
        repository.clearOwnCache()
        _state.update { it.copy(ownCacheBytes = repository.ownCacheBytes()) }
    }

    suspend fun appIcon(packageName: String) = repository.appInventory.icon(packageName)

    /** Called when returning from the system uninstall dialog; verifies what actually happened. */
    fun verifyUninstall() {
        _state.update { it.copy(appsLoading = true) }
        viewModelScope.launch { reloadApps() }
        refreshEnvironment()
    }

    // ---- cleanup ----------------------------------------------------------------------------

    fun runCleanup() {
        val current = _state.value
        val targets = current.selectedItems
        if (targets.isEmpty() || current.stage == Stage.CLEANING) return

        val freeBefore = current.storage.freeBytes
        val categories = current.affectedCategories
        _state.update { it.copy(stage = Stage.CLEANING, deleteProgress = 0f) }

        cleanupJob = viewModelScope.launch {
            val outcome = repository.delete(targets) { done, total ->
                _state.update {
                    it.copy(deleteProgress = if (total == 0) 1f else done.toFloat() / total)
                }
            }
            val storageAfter = repository.storage()
            removeFromResult(outcome.deletedPaths)
            _state.update {
                it.copy(
                    stage = Stage.DONE,
                    storage = storageAfter,
                    deleteProgress = 1f,
                    selectedPaths = it.selectedPaths - outcome.deletedPaths.toSet(),
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

    fun dismissCompletion() = _state.update {
        it.copy(
            stage = if (it.result?.items.isNullOrEmpty()) Stage.IDLE else Stage.RESULTS,
            cleanup = null,
            deleteProgress = 0f,
        )
    }

    private fun removeFromResult(paths: List<String>) {
        if (paths.isEmpty()) return
        val removed = paths.toSet()
        _state.update { state ->
            val result = state.result ?: return@update state
            state.copy(
                result = result.copy(items = result.items.filterNot { it.path in removed }),
                selectedPaths = state.selectedPaths - removed,
            )
        }
    }

    // ---- settings ---------------------------------------------------------------------------

    fun setOldFileThreshold(days: Int) = viewModelScope.launch {
        settingsStore.setOldFileThreshold(days)
    }

    fun setLargeFileThreshold(bytes: Long) = viewModelScope.launch {
        settingsStore.setLargeFileThreshold(bytes)
    }

    fun setScreenshotThreshold(days: Int) = viewModelScope.launch {
        settingsStore.setScreenshotThreshold(days)
    }

    fun setUnusedAppThreshold(days: Int) = viewModelScope.launch {
        settingsStore.setUnusedAppThreshold(days)
        reloadApps()
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { settingsStore.setHaptics(enabled) }

    fun setMotion(preference: MotionPreference) = viewModelScope.launch {
        settingsStore.setMotion(preference)
    }

    fun completeOnboarding() = viewModelScope.launch { settingsStore.setOnboardingComplete() }
}
