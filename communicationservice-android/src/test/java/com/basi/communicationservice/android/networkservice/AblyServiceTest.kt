package com.basi.communicationservice.android.networkservice

import io.ably.lib.realtime.AblyRealtime.Channels
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.realtime.Connection
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.realtime.ConnectionStateListener
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.Message
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContentEquals

class AblyServiceTest {
    private val ablyRealtime: AblyRealtimeWrapper = mockk(relaxed = true)
    private val ablyConnection: Connection = mockk(relaxed = true)
    private val ablyChannels: Channels = mockk(relaxed = true)
    private val ablyChannel: Channel = mockk(relaxed = true)

    private val testScope: TestScope = TestScope()
    private lateinit var ablyService: AblyService

    @Before
    fun setUp() {
        ablyService = AblyService(
            provider = NETWORK_SERVICE_PROVIDER_ABLY,
            config = NetworkServiceConfig(
                connectTimeout = TEST_ABLY_TIMEOUT,
                disconnectTimeout = TEST_ABLY_TIMEOUT
            )
        )
        ablyService.setAbly(ablyRealtime)
        every { ablyRealtime.getConnection() } returns ablyConnection
        every { ablyRealtime.getChannels() } returns ablyChannels
        every { ablyChannels.get(TEST_ABLY_TOPIC) } returns ablyChannel
    }

    @Test
    fun utest_AblyService_Connect_Success() = testScope.runTest {
        runTestConnect(ConnectionState.connected)
        ablyService.connect(TEST_ABLY_TOKEN)
        assertEquals(true, ablyService.isConnected())
    }

    @Test
    fun utest_AblyService_Connect_Failed() = testScope.runTest {
        runTestConnect(ConnectionState.failed)
        try {
            ablyService.connect(TEST_ABLY_TOKEN)
        } catch (ex: Exception) {
            assertEquals(NetworkServiceException.DEFAULT_CONNECT_ERROR_MESSAGE, ex.message)
        }
        assertEquals(false, ablyService.isConnected())
    }

    @Test
    fun utest_AblyService_Connect_Timeout() = testScope.runTest {
        runTestConnect(ConnectionState.disconnected)
        try {
            ablyService.connect(TEST_ABLY_TOKEN)
        } catch (ex: Exception) {
            assert(ex is NetworkServiceException)
            assert(ex.cause is TimeoutCancellationException)
        }
        assertEquals(false, ablyService.isConnected())
    }

    @Test
    fun utest_AblyService_Subscribe_Success() = testScope.runTest {
        ensureConnected()
        val completionListener = slot<CompletionListener>()
        every { ablyChannel.attach(capture(completionListener)) } answers {
            completionListener.captured.onSuccess()
        }
        val messageListener = slot<Channel.MessageListener>()
        every { ablyChannel.subscribe(capture(messageListener)) } answers {
            messageListener.captured.onMessage(Message("", TEST_MESSAGE_DATA))
        }
        every { ablyChannel.unsubscribe() } just Runs
        val onMessageCallback = object : (ByteArray) -> Unit {
            override fun invoke(data: ByteArray) {
                assertContentEquals(TEST_MESSAGE_DATA, data)
            }
        }
        ablyService.subscribe(TEST_ABLY_TOPIC, onMessageCallback)
        verifySequence {
            ablyChannel.attach(capture(completionListener))
            ablyChannel.unsubscribe()
            ablyChannel.subscribe(capture(messageListener))
        }
    }

    @Test
    fun utest_AblyService_Subscribe_NotConnected() {
        runTestConnect(ConnectionState.disconnected)
        try {
            ablyService.subscribe(TEST_ABLY_TOPIC, object : (ByteArray) -> Unit {
                override fun invoke(data: ByteArray) {
                }
            })
        } catch (ex: NetworkServiceException) {
            assert(ex.cause?.cause is TimeoutCancellationException)
        }
        verify(exactly = 0) { ablyChannels.get(TEST_ABLY_TOPIC) }
    }

    @Test
    fun utest_AblyService_Subscribe_AttachError() {
        val errMessage = "Attach Error"
        val errorCode = 100
        ensureConnected()
        val completionListener = slot<CompletionListener>()
        every { ablyChannel.attach(capture(completionListener)) } answers {
            completionListener.captured.onError(ErrorInfo(errMessage, errorCode))
        }
        val onMessageCallback = object : (ByteArray) -> Unit {
            override fun invoke(data: ByteArray) {
            }
        }
        try {
            ablyService.subscribe(TEST_ABLY_TOPIC, onMessageCallback)
        } catch (ex: NetworkServiceException) {
            assertEquals(errorCode, ex.errCode)
            assert(ex.message.contains(errMessage))
        }
    }

    @Test
    fun utest_AblyService_Publish_Success() {
        ensureConnected()
        val completionListener = slot<CompletionListener>()
        every { ablyChannel.publish(TEST_ABLY_TOPIC, TEST_MESSAGE_DATA, capture(completionListener)) } answers {
            completionListener.captured.onSuccess()
        }
        ablyService.publish(TEST_ABLY_TOPIC, TEST_MESSAGE_DATA)
        verify { ablyChannel.publish(TEST_ABLY_TOPIC, TEST_MESSAGE_DATA, capture(completionListener)) }
    }

    @Test
    fun utest_AblyService_Publish_NotConnected() {
        runTestConnect(ConnectionState.disconnected)
        try {
            ablyService.publish(TEST_ABLY_TOPIC, TEST_MESSAGE_DATA)
        } catch (ex: NetworkServiceException) {
            assert(ex.cause?.cause is TimeoutCancellationException)
        }
        verify(exactly = 0) { ablyChannels.get(TEST_ABLY_TOPIC) }
    }

    @Test
    fun utest_AblyService_Publish_Error() {
        val errMessage = "Publish Error"
        val errorCode = 100
        ensureConnected()
        val completionListener = slot<CompletionListener>()
        every { ablyChannel.publish(TEST_ABLY_TOPIC, TEST_MESSAGE_DATA, capture(completionListener)) } answers {
            completionListener.captured.onError(ErrorInfo(errMessage, errorCode))
        }
        try {
            ablyService.publish(TEST_ABLY_TOPIC, TEST_MESSAGE_DATA)
        } catch (ex: NetworkServiceException) {
            assertEquals(errorCode, ex.errCode)
            assert(ex.message.contains(errMessage))
        }
    }

    @Test
    fun utest_AblyService_Unsubscribe_Success() = testScope.runTest {
        ensureConnected()
        val completionListener = slot<CompletionListener>()
        every { ablyChannel.detach(capture(completionListener)) } answers {
            completionListener.captured.onSuccess()
        }
        ablyService.unsubscribe(TEST_ABLY_TOPIC)
        verify(exactly = 1) {
            ablyChannel.detach(capture(completionListener))
        }
    }

    @Test
    fun utest_AblyService_UnSubscribe_NotConnected() {
        runTestConnect(ConnectionState.disconnected)
        ablyService.unsubscribe(TEST_ABLY_TOPIC)
        verify(exactly = 0) { ablyChannels.get(TEST_ABLY_TOPIC) }
    }

    @Test
    fun utest_AblyService_UnSubscribe_DetachError() {
        val errMessage = "Detach Error"
        val errorCode = 100
        ensureConnected()
        val completionListener = slot<CompletionListener>()
        every { ablyChannel.detach(capture(completionListener)) } answers {
            completionListener.captured.onError(ErrorInfo(errMessage, errorCode))
        }
        try {
            ablyService.unsubscribe(TEST_ABLY_TOPIC)
        } catch (ex: NetworkServiceException) {
            assertEquals(errorCode, ex.errCode)
            assert(ex.message.contains(errMessage))
        }
    }

    @Test
    fun utest_AblyService_UnSubscribe_ThrowAblyException() {
        val errMessage = "Host Failed"
        val errorCode = 100
        ensureConnected()
        val completionListener = slot<CompletionListener>()
        every { ablyChannel.detach(capture(completionListener)) } answers {
            throw AblyException.fromErrorInfo(ErrorInfo(errMessage, errorCode))
        }
        try {
            ablyService.unsubscribe(TEST_ABLY_TOPIC)
        } catch (ex: NetworkServiceException) {
            assertEquals(errorCode, ex.errCode)
            assert(ex.message.contains(errMessage))
        }
    }

    @Test
    fun utest_AblyService_Disconnect_Success() {
        ensureConnected()
        val connectionStateListener = slot<ConnectionStateListener>()
        every { ablyConnection.on(ConnectionState.closed, capture(connectionStateListener)) } answers {
            connectionStateListener.captured.onConnectionStateChanged(
                ConnectionStateListener
                    .ConnectionStateChange(ConnectionState.initialized, ConnectionState.closed, 10, null)
            )
        }
        ablyService.disconnect()
        verify(exactly = 1) { ablyConnection.close() }
        assertEquals(false, ablyService.isConnected())
    }

    @Test
    fun utest_AblyService_Disconnect_Timeout() {
        ensureConnected()
        try {
            ablyService.disconnect()
        } catch (ex: Exception) {
            assert(ex is NetworkServiceException)
            assert(ex.cause is TimeoutCancellationException)
        }
        assertEquals(true, ablyService.isConnected())
    }

    private fun runTestConnect(currentState: ConnectionState) {
        val connectionStateListener = slot<ConnectionStateListener>()
        every { ablyConnection.on(capture(connectionStateListener)) } answers {
            connectionStateListener.captured.onConnectionStateChanged(
                ConnectionStateListener
                    .ConnectionStateChange(ConnectionState.initialized, currentState, 10, null)
            )
        }
    }

    private fun ensureConnected() {
        runTestConnect(ConnectionState.connected)
        ablyService.connect(TEST_ABLY_TOKEN)
    }

    companion object {
        private const val TEST_ABLY_TOKEN = "fake_token"
        private const val TEST_ABLY_TOPIC = "fake_topic"
        private const val TEST_ABLY_TIMEOUT = 100L
        private val TEST_MESSAGE_DATA = byteArrayOf(1, 1, 1, 1)
    }
}