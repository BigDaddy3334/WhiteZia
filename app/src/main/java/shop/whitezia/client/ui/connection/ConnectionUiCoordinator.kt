package shop.whitezia.client.ui.connection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import shop.whitezia.client.account.AccountDeviceNotBoundException
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.selectedTransportMode

internal enum class ConnectionUiPhase { Idle, Preparing, WaitingForPermission, Disconnecting, SwitchingMode }

internal data class ConnectionUiState(
    val phase: ConnectionUiPhase = ConnectionUiPhase.Idle,
    val permissionRequest: Long? = null,
    val error: String? = null,
    val openSplitTunnelRequest: Long? = null,
)

internal interface ConnectionUiActions {
    val settings: WhiteZiaSettings
    val connectionStatus: ConnectionStatus
    fun prepareSubscription(link: String, transportMode: String, connectionMode: String): String?
    fun importSubscription(link: String): Result<Unit>
    fun resetConnectionLog()
    fun log(message: String)
    fun applyCachedResolvers(): Boolean
    suspend fun discoverResolvers(): String?
    fun beginConnection(): Boolean
    fun disconnect()
    suspend fun awaitRuntimeStop(): Boolean
    fun updateSettings(settings: WhiteZiaSettings)
    fun clearSubscription()
    fun refreshOperator() {}
}

internal interface ConnectionAccount {
    suspend fun refreshProfile(): String?
    fun profileApplied(bundle: String)
    fun profileRejected(message: String)
}

// Only user-requested launch preparation lives here. VPN runtime transitions belong to the service.
internal class ConnectionUiCoordinator(
    private val scope: CoroutineScope,
    private val actions: ConnectionUiActions,
) {
    private val mutableState = MutableStateFlow(ConnectionUiState())
    val state: StateFlow<ConnectionUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L
    private var presentedPermission: Long? = null

    fun toggleConnection(account: ConnectionAccount, link: String) {
        when {
            state.value.phase == ConnectionUiPhase.Disconnecting ||
                state.value.phase == ConnectionUiPhase.SwitchingMode -> Unit
            state.value.phase != ConnectionUiPhase.Idle || actions.connectionStatus != ConnectionStatus.DISCONNECTED -> disconnect()
            else -> connect(account, link)
        }
    }

    fun importProfile(link: String): Result<Unit> = actions.importSubscription(link)

    fun connect(account: ConnectionAccount, link: String, connectionMode: String = "vpn") {
        if (state.value.phase != ConnectionUiPhase.Idle || actions.connectionStatus != ConnectionStatus.DISCONNECTED) return
        val request = ++generation
        mutableState.value = ConnectionUiState(phase = ConnectionUiPhase.Preparing)
        actions.resetConnectionLog()
        operation = scope.launch {
            try {
                val refreshed = try {
                    account.refreshProfile()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: AccountDeviceNotBoundException) {
                    ensureActive()
                    if (generation != request) return@launch
                    actions.clearSubscription()
                    fail(checkNotNull(error.message))
                    return@launch
                } catch (_: Exception) {
                    actions.log("Account refresh unavailable; using saved profile")
                    null
                }
                ensureActive()
                if (generation != request) return@launch
                actions.refreshOperator()
                val profileLink = refreshed?.takeIf(String::isNotBlank) ?: link.trim()
                val settings = actions.settings
                val transport = settings.selectedTransportMode()
                val preparationError = actions.prepareSubscription(profileLink, transport, connectionMode)
                if (preparationError != null) {
                    if (!refreshed.isNullOrBlank()) account.profileRejected(preparationError)
                    if (!refreshed.isNullOrBlank() && profileLink != link.trim()) {
                        actions.log("Refreshed profile invalid; using saved profile")
                        val cachedError = actions.prepareSubscription(link.trim(), transport, connectionMode)
                        if (cachedError != null) {
                            fail(cachedError)
                            return@launch
                        }
                    } else {
                        fail(preparationError)
                        return@launch
                    }
                } else if (!refreshed.isNullOrBlank()) {
                    account.profileApplied(refreshed)
                }
                ensureActive()
                if (generation != request) return@launch
                if (connectionMode != "vpn") {
                    if (!actions.applyCachedResolvers() && actions.settings.resolve().resolverEntries.isEmpty()) {
                        val resolverError = actions.discoverResolvers()
                        ensureActive()
                        if (generation != request) return@launch
                        if (resolverError != null) {
                            fail(resolverError)
                            return@launch
                        }
                    }
                    launchPreparedConnection()
                } else {
                    mutableState.value = ConnectionUiState(
                        phase = ConnectionUiPhase.WaitingForPermission,
                        permissionRequest = request.takeIf { presentedPermission == null },
                    )
                }
            } catch (cancellation: CancellationException) {
                if (generation == request) {
                    operation = null
                    mutableState.value = ConnectionUiState()
                }
                throw cancellation
            } catch (error: Exception) {
                if (generation == request) fail(error.message ?: "Connection preparation failed")
            }
        }
    }

    fun claimPermissionRequest(request: Long): Boolean {
        if (state.value.permissionRequest != request || presentedPermission != null) return false
        presentedPermission = request
        mutableState.value = state.value.copy(permissionRequest = null)
        return true
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val request = presentedPermission ?: return
        presentedPermission = null
        if (request != generation || state.value.phase != ConnectionUiPhase.WaitingForPermission) {
            if (state.value.phase == ConnectionUiPhase.WaitingForPermission) {
                mutableState.value = state.value.copy(permissionRequest = generation)
            }
            return
        }
        if (granted) launchPreparedConnection() else fail("VPN permission is required")
    }

    fun disconnect(clearSubscription: Boolean = false) {
        cancelPending()
        val request = generation
        mutableState.value = ConnectionUiState(phase = ConnectionUiPhase.Disconnecting)
        actions.disconnect()
        if (clearSubscription) actions.clearSubscription()
        operation = scope.launch {
            try {
                val stopped = actions.awaitRuntimeStop()
                ensureActive()
                if (generation == request) {
                    if (stopped) mutableState.value = ConnectionUiState() else fail("VPN tunnel did not stop")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generation == request) fail(error.message ?: "VPN tunnel did not stop")
            }
        }
    }

    fun cancelPreparation() {
        if (state.value.phase != ConnectionUiPhase.Preparing &&
            state.value.phase != ConnectionUiPhase.WaitingForPermission) return
        cancelPending()
        mutableState.value = ConnectionUiState()
    }

    fun changeMode(settings: WhiteZiaSettings, openSplitTunnel: Boolean = false) {
        val current = actions.settings
        val modeChanged = settings.manualMode != current.manualMode ||
            settings.transportMode != current.transportMode ||
            settings.forceDnsTunnel != current.forceDnsTunnel ||
            settings.connectionMode != current.connectionMode
        if (!modeChanged && state.value.phase == ConnectionUiPhase.Idle) {
            actions.updateSettings(settings)
            if (openSplitTunnel) mutableState.value = state.value.copy(openSplitTunnelRequest = ++generation)
            return
        }
        cancelPending()
        val request = generation
        mutableState.value = ConnectionUiState(phase = ConnectionUiPhase.SwitchingMode)
        actions.disconnect()
        operation = scope.launch {
            try {
                val stopped = actions.awaitRuntimeStop()
                ensureActive()
                if (generation != request) return@launch
                if (!stopped) {
                    fail("VPN tunnel did not stop")
                    return@launch
                }
                actions.updateSettings(settings)
                mutableState.value = ConnectionUiState(openSplitTunnelRequest = request.takeIf { openSplitTunnel })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generation == request) fail(error.message ?: "Mode change failed")
            }
        }
    }

    fun consumeSplitTunnelRequest(request: Long) {
        if (state.value.openSplitTunnelRequest == request) {
            mutableState.value = state.value.copy(openSplitTunnelRequest = null)
        }
    }

    fun onNetworkChanged() {
        if (state.value.phase == ConnectionUiPhase.Idle && actions.connectionStatus == ConnectionStatus.DISCONNECTED) {
            actions.refreshOperator()
        }
    }

    fun applyAccountProfile(bundle: String, account: ConnectionAccount) {
        if (state.value.phase != ConnectionUiPhase.Idle) return
        actions.importSubscription(bundle)
            .onSuccess { account.profileApplied(bundle) }
            .onFailure { account.profileRejected(it.message ?: "Profile import failed") }
    }

    private fun launchPreparedConnection() {
        if (actions.beginConnection()) mutableState.value = ConnectionUiState() else fail("Connection could not start")
    }

    private fun cancelPending() {
        generation++
        operation?.cancel()
        operation = null
    }

    private fun fail(message: String) {
        actions.log(message)
        mutableState.value = ConnectionUiState(error = message)
    }
}
