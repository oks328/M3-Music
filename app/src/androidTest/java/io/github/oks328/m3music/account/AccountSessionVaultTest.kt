package io.github.oks328.m3music.account

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AccountSessionVaultTest {
    @Test fun sessionIsEncryptedAndCanBeRestoredAndRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val filename = "account-test-$id.enc"
        val vault = AccountSessionVault(context, filename, "account-test-$id")
        try {
            val saved = StoredAccount(AccountSession("SAPISID=synthetic-private-test-cookie", "visitor"), AccountProfile("Test listener"))
            vault.save(saved)
            val disk = File(context.noBackupFilesDir, filename).readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(disk.contains("synthetic-private-test-cookie"))
            assertFalse(disk.contains("Test listener"))
            val restored = AccountSessionVault(context, filename, "account-test-$id").load()
            assertEquals(saved.session.cookie, restored?.session?.cookie)
            assertEquals(saved.profile, restored?.profile)
            vault.clear()
            assertNull(vault.load())
            assertFalse(File(context.noBackupFilesDir, filename).exists())
        } finally { vault.clear() }
    }

    @Test fun tamperingMakesTheSessionUnreadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val filename = "account-test-$id.enc"
        val vault = AccountSessionVault(context, filename, "account-test-$id")
        try {
            vault.save(StoredAccount(AccountSession("SAPISID=dummy"), AccountProfile("Test listener")))
            val file = File(context.noBackupFilesDir, filename)
            val data = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(data)
            assertNull(vault.load())
            assertFalse(file.exists())
        } finally { vault.clear() }
    }
}
