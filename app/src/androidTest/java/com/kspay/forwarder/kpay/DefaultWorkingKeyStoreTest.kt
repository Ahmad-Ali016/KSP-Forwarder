package com.kspay.forwarder.kpay

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented, not Robolectric: EncryptedSharedPreferences needs the real AndroidKeyStore
 * provider, which Robolectric cannot simulate on the plain JVM.
 */
@RunWith(AndroidJUnit4::class)
class DefaultWorkingKeyStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        DefaultWorkingKeyStore(context).clear()
    }

    @Test
    fun saveThenGetReturnsTheSameKeys() {
        val store = DefaultWorkingKeyStore(context)
        val keys = SignInResponse(platformPublicKey = "pub", appPrivateKey = "priv")

        store.save(keys)

        assertEquals(keys, store.get())
    }

    @Test
    fun getReturnsNullWhenNothingSaved() {
        val store = DefaultWorkingKeyStore(context)
        assertNull(store.get())
    }

    @Test
    fun clearRemovesSavedKeys() {
        val store = DefaultWorkingKeyStore(context)
        store.save(SignInResponse("pub", "priv"))

        store.clear()

        assertNull(store.get())
    }

    @Test
    fun keysPersistAcrossANewStoreInstanceBackedByTheSamePrefs() {
        val keys = SignInResponse(platformPublicKey = "pub2", appPrivateKey = "priv2")
        DefaultWorkingKeyStore(context).save(keys)

        val reloaded = DefaultWorkingKeyStore(context).get()

        assertEquals(keys, reloaded)
    }
}
