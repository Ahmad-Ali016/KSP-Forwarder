package com.kspay.forwarder.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ConfigProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ConfigApi
    private lateinit var provider: ConfigProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ConfigClientFactory.create(baseUrl = server.url("/").toString())
        provider = ConfigProvider(RuntimeEnvironment.getApplication(), api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `current returns safe defaults when nothing has ever been fetched`() = runTest {
        val config = provider.current()

        assertEquals(FeatureConfig.SAFE_DEFAULTS, config)
    }

    @Test
    fun `refresh fetches and persists config, current reflects it afterward`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"enableRefund":true,"enableVoid":true,"pollIntervalSeconds":10,"backendIngestUrl":"https://api.kspay.example/ingest"}""",
            ),
        )

        val fetched = provider.refresh(deviceToken = "device-token")

        assertEquals(true, fetched.enableRefund)
        assertEquals(true, fetched.enableVoid)
        assertEquals(10, fetched.pollIntervalSeconds)
        assertEquals("https://api.kspay.example/ingest", fetched.backendIngestUrl)
        assertEquals(fetched, provider.current())
    }

    @Test
    fun `refresh on a network failure falls back to the cached config instead of crashing`() = runTest {
        server.enqueue(MockResponse().setBody("""{"enableRefund":true,"enableVoid":false,"pollIntervalSeconds":7}"""))
        val firstFetch = provider.refresh(deviceToken = "device-token")
        server.shutdown() // simulate the backend becoming unreachable

        val result = provider.refresh(deviceToken = "device-token")

        assertEquals(firstFetch, result)
    }
}
