package io.github.oks328.m3music.account

import io.github.oks328.m3music.music.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountControllerTest {
    private val session = AccountSession("SAPISID=dummy-test")
    private val profile = AccountProfile("Listener")

    private class MemoryStore(var account: StoredAccount? = null) : AccountSessionStore {
        override fun load() = account
        override fun save(account: StoredAccount) { this.account = account }
        override fun clear() { account = null }
    }
    private class Gateway : AccountGateway {
        var failure: Exception? = null
        var browse: suspend () -> AccountPage = { AccountPage() }
        override suspend fun profile(session: AccountSession): AccountProfile {
            failure?.let { throw it }; return AccountProfile("Listener")
        }
        override suspend fun browse(session: AccountSession, browseId: String, continuation: String?) = browse()
    }

    @Test fun sessionIsSavedOnlyAfterAccountVerification() = runTest {
        val store = MemoryStore()
        val gateway = Gateway()
        val controller = AccountController(backgroundScope, store, gateway, StandardTestDispatcher(testScheduler))
        runCurrent()
        backgroundScope.launch { assertTrue(controller.connect(session)) }
        runCurrent()
        assertEquals(profile, controller.state.value.profile)
        assertNotNull(store.account)
    }

    @Test fun refusedSignInDoesNotReplaceTheExistingAccount() = runTest {
        val store = MemoryStore(StoredAccount(session, profile))
        val gateway = Gateway()
        val controller = AccountController(backgroundScope, store, gateway, StandardTestDispatcher(testScheduler))
        runCurrent()
        gateway.failure = AccountSessionExpired()
        backgroundScope.launch { assertFalse(controller.connect(AccountSession("SAPISID=another"))) }
        runCurrent()
        assertSame(session, store.account?.session)
        assertEquals(profile, controller.state.value.profile)
    }

    @Test fun networkFailureDoesNotExpireOrDeleteTheSession() = runTest {
        val store = MemoryStore(StoredAccount(session, profile))
        val gateway = Gateway().apply { failure = IOException("offline") }
        val controller = AccountController(backgroundScope, store, gateway, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(profile, controller.state.value.profile)
        assertFalse(controller.state.value.expired)
        assertNotNull(store.account)
    }

    @Test fun rejectedSavedSessionIsShownAsExpired() = runTest {
        val gateway = Gateway().apply { failure = AccountSessionExpired() }
        val controller = AccountController(backgroundScope, MemoryStore(StoredAccount(session, profile)), gateway, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertTrue(controller.state.value.expired)
        assertEquals(profile, controller.state.value.profile)
    }

    @Test fun logoutClearsStoredSessionAndLateAccountPages() = runTest {
        val delayed = CompletableDeferred<AccountPage>()
        val gateway = Gateway().apply { browse = { withContext(NonCancellable) { delayed.await() } } }
        val store = MemoryStore(StoredAccount(session, profile))
        val controller = AccountController(backgroundScope, store, gateway, StandardTestDispatcher(testScheduler))
        runCurrent()
        controller.loadCategory(AccountCategory.PLAYLISTS)
        runCurrent()
        controller.disconnect()
        runCurrent()
        delayed.complete(AccountPage(listOf(AccountItem("VLprivate", "Private playlist", kind = AccountItemKind.PLAYLIST))))
        runCurrent()
        assertNull(store.account)
        assertNull(controller.state.value.profile)
        assertTrue(controller.state.value.page.items.isEmpty())
    }
    @Test fun collectionDownloadIncludesLaterPagesAndDeduplicatesSongs() = runTest {
        fun track(id: String) = Track(id, id, "Artist", null, 0)
        fun item(track: Track) = AccountItem(track.id, track.title, kind = AccountItemKind.SONG, track = track)
        val first = track("first")
        val second = track("second")
        val third = track("third")
        val pages = ArrayDeque(listOf(
            AccountPage(listOf(item(first), item(second)), continuation = "last"),
            AccountPage(listOf(item(third))),
        ))
        val gateway = Gateway().apply { browse = { pages.removeFirst() } }
        val controller = AccountController(backgroundScope, MemoryStore(StoredAccount(session, profile)),
            gateway, StandardTestDispatcher(testScheduler))
        runCurrent()
        val songs = controller.collectionTracks("VLtest", AccountPage(listOf(item(first)), continuation = "next"), true)
        assertEquals(listOf(first, second, third), songs)
    }
}
