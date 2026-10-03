package shop.whitezia.client.ui.connection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.account.AccountDeviceNotBoundException
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings

class ConnectionUiCoordinatorTest {
    @Test fun autoWithoutAwgHandsOffOnceWithoutUiResolverWork() = runBlocking {
        val actions = FakeActions()
        val account = FakeAccount(bundle = "fresh")
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(account, "saved")
        coordinator.connect(account, "saved")
        yield()

        assertEquals(1, account.refreshes)
        assertEquals(listOf("fresh"), actions.preparedLinks)
        assertEquals(listOf(WhiteZiaOptions.TransportAuto), actions.preparedTransports)
        assertEquals(listOf("fresh"), account.applied)
        assertEquals(0, actions.resolverPreparations)
        assertEquals(0, actions.starts)
        val request = coordinator.state.value.permissionRequest!!
        assertTrue(coordinator.claimPermissionRequest(request))
        assertFalse(coordinator.claimPermissionRequest(request))
        coordinator.onVpnPermissionResult(true)
        coordinator.onVpnPermissionResult(true)
        assertEquals(1, actions.starts)
        assertEquals(WhiteZiaOptions.TransportAuto, actions.settings.transportMode)
    }

    @Test fun manualXrayIsNotOverriddenByProfilePreparation() = runBlocking {
        val actions = FakeActions(WhiteZiaSettings(manualMode = true, transportMode = WhiteZiaOptions.TransportXray))
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(), "saved")
        yield()
        assertEquals(listOf(WhiteZiaOptions.TransportXray), actions.preparedTransports)
        assertTrue(actions.settings.manualMode)
        assertEquals(0, actions.resolverPreparations)
    }

    @Test fun manualDnsAlsoLeavesResolverPreparationToVpnService() = runBlocking {
        val actions = FakeActions(WhiteZiaSettings(manualMode = true, forceDnsTunnel = true))
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(), "saved")
        yield()
        assertEquals(listOf(WhiteZiaOptions.TransportDns), actions.preparedTransports)
        assertTrue(actions.settings.forceDnsTunnel)
        assertEquals(0, actions.resolverPreparations)
        assertNotNull(coordinator.state.value.permissionRequest)
    }

    @Test fun automaticModeIgnoresStaleManualTransportSelection() = runBlocking {
        val actions = FakeActions(WhiteZiaSettings(transportMode = WhiteZiaOptions.TransportXray, forceDnsTunnel = true))
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(), "saved")
        yield()
        assertEquals(listOf(WhiteZiaOptions.TransportAuto), actions.preparedTransports)
    }

    @Test fun disabledDnsSwitchIgnoresStaleDnsTransportInManualMode() = runBlocking {
        val actions = FakeActions(WhiteZiaSettings(
            manualMode = true,
            transportMode = WhiteZiaOptions.TransportDns,
            forceDnsTunnel = false,
        ))
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(), "saved")
        yield()
        assertEquals(listOf(WhiteZiaOptions.TransportAuto), actions.preparedTransports)
    }

    @Test fun unavailableAccountUsesSavedProfile() = runBlocking {
        val actions = FakeActions()
        val account = FakeAccount(refresh = { throw IllegalStateException("offline") })
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(account, "saved")
        yield()
        assertEquals(listOf("saved"), actions.preparedLinks)
        assertNull(coordinator.state.value.error)
        assertNotNull(coordinator.state.value.permissionRequest)
    }

    @Test fun removedAccountDeviceCannotFallBackToSavedProfile() = runBlocking {
        val actions = FakeActions(WhiteZiaSettings(subscriptionLink = "saved"))
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(refresh = { throw AccountDeviceNotBoundException() }), "saved")
        yield()
        assertEquals("", actions.settings.subscriptionLink)
        assertTrue(actions.preparedLinks.isEmpty())
        assertEquals(0, actions.starts)
        assertNull(coordinator.state.value.permissionRequest)
        assertNotNull(coordinator.state.value.error)
    }

    @Test fun lateMissingDeviceResultCannotClearNewProfileAfterCancellation() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val actions = FakeActions(WhiteZiaSettings(subscriptionLink = "saved"))
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(refresh = {
            withContext(NonCancellable) { release.await() }
            throw AccountDeviceNotBoundException()
        }), "saved")
        yield()
        coordinator.cancelPreparation()
        coordinator.connect(FakeAccount(bundle = "new"), "new")
        yield()
        release.complete(Unit)
        yield()
        assertEquals("new", actions.settings.subscriptionLink)
        assertNull(coordinator.state.value.error)
        assertNotNull(coordinator.state.value.permissionRequest)
    }

    @Test fun invalidRefreshedProfileFallsBackToSavedProfile() = runBlocking {
        val actions = FakeActions().apply { invalidLink = "invalid" }
        val account = FakeAccount(bundle = "invalid")
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(account, "saved")
        yield()
        assertEquals(listOf("invalid", "saved"), actions.preparedLinks)
        assertEquals(1, account.rejected.size)
        assertTrue(account.applied.isEmpty())
        assertNotNull(coordinator.state.value.permissionRequest)
    }

    @Test fun cancelledProfileFetchReturnsToIdleWithoutLaunching() = runBlocking {
        val actions = FakeActions()
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(refresh = { throw CancellationException("Account logged out") }), "saved")
        yield()
        assertEquals(ConnectionUiPhase.Idle, coordinator.state.value.phase)
        assertNull(coordinator.state.value.permissionRequest)
        assertNull(coordinator.state.value.error)
        assertTrue(actions.preparedLinks.isEmpty())
        assertEquals(0, actions.starts)
    }

    @Test fun cancelledOlderPreparationDoesNotResetNewGeneration() = runBlocking {
        val releaseOld = CompletableDeferred<Unit>()
        val newProfile = CompletableDeferred<String?>()
        val actions = FakeActions()
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(refresh = {
            withContext(NonCancellable) { releaseOld.await() }
            throw CancellationException("Old account generation")
        }), "first")
        yield()
        coordinator.cancelPreparation()
        coordinator.connect(FakeAccount(refresh = { newProfile.await() }), "second")
        yield()
        releaseOld.complete(Unit)
        yield()
        assertEquals(ConnectionUiPhase.Preparing, coordinator.state.value.phase)
        assertNull(coordinator.state.value.permissionRequest)
        assertEquals(0, actions.starts)
        newProfile.complete("second")
        yield()
        assertEquals(ConnectionUiPhase.WaitingForPermission, coordinator.state.value.phase)
        assertEquals(listOf("second"), actions.preparedLinks)
    }

    @Test fun cancellingUnmanagedPreparationPreservesSubscriptionAndDoesNotStopRuntime() = runBlocking {
        val profile = CompletableDeferred<String?>()
        val actions = FakeActions(WhiteZiaSettings(subscriptionLink = "saved"))
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(refresh = { profile.await() }), "saved")
        yield()
        coordinator.cancelPreparation()
        profile.complete("late")
        yield()
        assertEquals(ConnectionUiPhase.Idle, coordinator.state.value.phase)
        assertEquals("saved", actions.settings.subscriptionLink)
        assertEquals(0, actions.stops)
        assertEquals(0, actions.starts)
        assertTrue(actions.preparedLinks.isEmpty())
    }

    @Test fun cancellingPreparationDoesNotDisconnectAnUnmanagedActiveConnection() = runBlocking {
        val actions = FakeActions(WhiteZiaSettings(subscriptionLink = "saved"))
            .apply { connectionStatus = ConnectionStatus.CONNECTED }
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.cancelPreparation()
        assertEquals(ConnectionStatus.CONNECTED, actions.connectionStatus)
        assertEquals("saved", actions.settings.subscriptionLink)
        assertEquals(0, actions.stops)
    }

    @Test fun disconnectRejectsLateAccountRefreshEvenIfItIgnoresCancellation() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val actions = FakeActions()
        val account = FakeAccount(refresh = {
            withContext(NonCancellable) { release.await(); "late" }
        })
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(account, "saved")
        yield()
        coordinator.disconnect()
        release.complete(Unit)
        yield()
        assertTrue(actions.preparedLinks.isEmpty())
        assertEquals(0, actions.starts)
        assertNull(coordinator.state.value.permissionRequest)
    }

    @Test fun cancelledPermissionResultCannotLaunchNewAttempt() = runBlocking {
        val actions = FakeActions()
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(), "first")
        yield()
        assertTrue(coordinator.claimPermissionRequest(coordinator.state.value.permissionRequest!!))
        coordinator.disconnect()
        yield()
        coordinator.connect(FakeAccount(), "second")
        yield()
        assertNull(coordinator.state.value.permissionRequest)
        coordinator.onVpnPermissionResult(true)
        assertEquals(0, actions.starts)
        val nextRequest = coordinator.state.value.permissionRequest!!
        assertTrue(coordinator.claimPermissionRequest(nextRequest))
        coordinator.onVpnPermissionResult(true)
        assertEquals(1, actions.starts)
    }

    @Test fun deniedPermissionDoesNotStartRuntime() = runBlocking {
        val actions = FakeActions()
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(), "saved")
        yield()
        coordinator.claimPermissionRequest(coordinator.state.value.permissionRequest!!)
        coordinator.onVpnPermissionResult(false)
        assertEquals(0, actions.starts)
        assertEquals(ConnectionUiPhase.Idle, coordinator.state.value.phase)
        assertNotNull(coordinator.state.value.error)
    }

    @Test fun modeChangeWaitsForRuntimeStopAndCannotRaceAccountRefresh() = runBlocking {
        val profile = CompletableDeferred<String?>()
        val stopped = CompletableDeferred<Boolean>()
        val actions = FakeActions().apply { awaitStop = { stopped.await() } }
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(refresh = { profile.await() }), "saved")
        yield()
        val selected = actions.settings.copy(manualMode = true, transportMode = WhiteZiaOptions.TransportXray)
        coordinator.changeMode(selected, openSplitTunnel = true)
        yield()
        assertEquals(ConnectionUiPhase.SwitchingMode, coordinator.state.value.phase)
        assertTrue(actions.updatedSettings.isEmpty())
        profile.complete("late")
        stopped.complete(true)
        yield()
        assertTrue(actions.preparedLinks.isEmpty())
        assertEquals(listOf(selected), actions.updatedSettings)
        val event = coordinator.state.value.openSplitTunnelRequest!!
        coordinator.consumeSplitTunnelRequest(event)
        assertNull(coordinator.state.value.openSplitTunnelRequest)
    }

    @Test fun failedRuntimeStopDoesNotApplyModeChange() = runBlocking {
        val actions = FakeActions().apply { awaitStop = { false } }
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.changeMode(actions.settings.copy(manualMode = true))
        yield()
        assertTrue(actions.updatedSettings.isEmpty())
        assertNotNull(coordinator.state.value.error)
    }

    @Test fun supersededModeChangeAppliesOnlyNewestSettings() = runBlocking {
        val stopped = CompletableDeferred<Boolean>()
        val actions = FakeActions().apply { awaitStop = { stopped.await() } }
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.changeMode(actions.settings.copy(manualMode = true))
        yield()
        val newest = actions.settings.copy(manualMode = true, forceDnsTunnel = true)
        coordinator.changeMode(newest)
        stopped.complete(true)
        yield()
        assertEquals(listOf(newest), actions.updatedSettings)
    }

    @Test fun secondConnectClickCancelsPreparation() = runBlocking {
        val profile = CompletableDeferred<String?>()
        val actions = FakeActions()
        val account = FakeAccount(refresh = { profile.await() })
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.toggleConnection(account, "saved")
        yield()
        coordinator.toggleConnection(account, "saved")
        profile.complete("late")
        yield()
        assertEquals(1, actions.stops)
        assertTrue(actions.preparedLinks.isEmpty())
        assertEquals(ConnectionUiPhase.Idle, coordinator.state.value.phase)
    }

    @Test fun proxyRetainsResolverPreparationAndDoesNotRequestVpnPermission() = runBlocking {
        val actions = FakeActions()
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.connect(FakeAccount(), "saved", connectionMode = "proxy")
        yield()
        assertEquals("proxy", actions.settings.connectionMode)
        assertEquals(2, actions.resolverPreparations)
        assertEquals(1, actions.starts)
        assertNull(coordinator.state.value.permissionRequest)
    }

    @Test fun networkChangesNeverRestartAnActiveVpn() = runBlocking {
        val actions = FakeActions().apply { connectionStatus = ConnectionStatus.CONNECTED }
        val coordinator = ConnectionUiCoordinator(this, actions)
        coordinator.onNetworkChanged()
        coordinator.connect(FakeAccount(), "saved")
        yield()
        assertEquals(0, actions.starts)
        assertEquals(0, actions.stops)
        assertEquals(0, actions.operatorRefreshes)
        assertTrue(actions.preparedLinks.isEmpty())
    }

    private class FakeActions(override var settings: WhiteZiaSettings = WhiteZiaSettings()) : ConnectionUiActions {
        override var connectionStatus = ConnectionStatus.DISCONNECTED
        val preparedLinks = mutableListOf<String>()
        val preparedTransports = mutableListOf<String>()
        val updatedSettings = mutableListOf<WhiteZiaSettings>()
        var invalidLink: String? = null
        var starts = 0
        var stops = 0
        var resolverPreparations = 0
        var operatorRefreshes = 0
        var awaitStop: suspend () -> Boolean = { true }
        override fun prepareSubscription(link: String, transportMode: String, connectionMode: String): String? {
            preparedLinks += link
            preparedTransports += transportMode
            if (link == invalidLink) return "Invalid profile"
            settings = settings.copy(subscriptionLink = link, transportMode = transportMode, connectionMode = connectionMode)
            return null
        }
        override fun importSubscription(link: String) = Result.success(Unit)
        override fun resetConnectionLog() {}
        override fun log(message: String) {}
        override fun applyCachedResolvers(): Boolean { resolverPreparations++; return false }
        override suspend fun discoverResolvers(): String? { resolverPreparations++; return null }
        override fun beginConnection(): Boolean { starts++; connectionStatus = ConnectionStatus.CONNECTING; return true }
        override fun disconnect() { stops++; connectionStatus = ConnectionStatus.DISCONNECTED }
        override suspend fun awaitRuntimeStop() = awaitStop()
        override fun updateSettings(settings: WhiteZiaSettings) { updatedSettings += settings; this.settings = settings }
        override fun clearSubscription() { settings = settings.copy(subscriptionLink = "") }
        override fun refreshOperator() { operatorRefreshes++ }
    }

    private class FakeAccount(
        private val bundle: String? = null,
        private val refresh: (suspend () -> String?)? = null,
    ) : ConnectionAccount {
        var refreshes = 0
        val applied = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        override suspend fun refreshProfile(): String? { refreshes++; return refresh?.invoke() ?: bundle }
        override fun profileApplied(bundle: String) { applied += bundle }
        override fun profileRejected(message: String) { rejected += message }
    }
}
