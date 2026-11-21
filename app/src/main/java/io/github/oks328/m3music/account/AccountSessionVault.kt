package io.github.oks328.m3music.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AccountSessionVault(
    context: Context,
    fileName: String = "youtube-session.enc",
    private val keyAlias: String = "m3music.youtube.session.v1",
) : AccountSessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))

    override fun save(account: StoredAccount) {
        val session = account.session
        SignedAccountRequest.authorization(session, 1)
        val json = JSONObject().apply {
            put("version", 1); put("cookie", session.cookie); put("visitor", session.visitorData)
            put("sync", session.dataSyncId); put("page", session.pageId); put("user", session.authUser)
            put("client", session.clientVersion); put("name", account.profile.name); put("handle", account.profile.handle)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try {
            output.write(1); output.write(cipher.iv.size); output.write(cipher.iv); output.write(encrypted)
            file.finishWrite(output)
        } catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    override fun load(): StoredAccount? {
        if (!file.baseFile.exists()) return null
        return try {
            val bytes = file.readFully()
            require(bytes.size in 31..262144 && bytes[0].toInt() == 1)
            val ivSize = bytes[1].toInt()
            require(ivSize == 12 && bytes.size > 2 + ivSize + 16)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(2, 2 + ivSize)))
            val json = JSONObject(cipher.doFinal(bytes.copyOfRange(2 + ivSize, bytes.size)).toString(Charsets.UTF_8))
            require(json.getInt("version") == 1)
            val session = AccountSession(json.getString("cookie"), json.optString("visitor"), json.optString("sync"),
                json.optString("page"), json.optString("user", "0"), json.optString("client", "1.20260114.03.00"))
            SignedAccountRequest.authorization(session, 1)
            StoredAccount(session, AccountProfile(json.getString("name"), json.optString("handle")))
        } catch (_: Exception) { clear(); null }
    }

    override fun clear() {
        file.delete()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias)
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        check(create) { "Account key is unavailable. Sign in again." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
}
