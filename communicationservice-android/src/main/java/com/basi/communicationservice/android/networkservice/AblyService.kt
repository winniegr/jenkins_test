package com.basi.communicationservice.android.networkservice

import android.util.Log
import com.basi.communicationservice.android.networkservice.NetworkServiceException.Companion.DEFAULT_CONNECT_ERROR_MESSAGE
import com.basi.communicationservice.android.networkservice.NetworkServiceException.Companion.DEFAULT_CONNECT_TIMEOUT_ERROR_MESSAGE
import com.basi.communicationservice.android.networkservice.NetworkServiceException.Companion.DEFAULT_DISCONNECT_ERROR_MESSAGE
import com.basi.communicationservice.android.networkservice.NetworkServiceException.Companion.DEFAULT_DISCONNECT_TIMEOUT_ERROR_MESSAGE
import com.bsci.communication.networkService.NetworkService
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.realtime.ConnectionStateListener
import io.ably.lib.rest.Auth
import io.ably.lib.types.ClientOptions
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.Message
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.VisibleForTesting
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AblyService(
    override val provider: String = NETWORK_SERVICE_PROVIDER_ABLY,
    private val config: NetworkServiceConfig = NetworkServiceConfig()
) : NetworkService {
    private lateinit var ablyToken: String
    private lateinit var ably: AblyRealtimeWrapper

    private val connectionState = MutableStateFlow(ConnectionState.initialized)
    private val mutex = Mutex()

    override fun <Config> connect(config: Config) = runBlocking {
        mutex.withLock {
            if (!::ably.isInitialized) initService(config as String)
            ensureConnected()
        }
    }

    override fun isConnected(): Boolean = connectionState.value == ConnectionState.connected

    override fun subscribe(key: String, fn: (ByteArray) -> Unit) = runBlocking {
        suspendCancellableCoroutine { continuation ->
            try {
                ensureConnected()
                val channel: Channel = ably.getChannels()[key]
                channel.attach(object : CompletionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "Subscribe to $key success!")
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(reason: ErrorInfo) {
                        val errMessage = "Subscribe to $key failed! $reason"
                        Log.e(TAG, errMessage)
                        if (continuation.isActive) continuation.resumeWithException(NetworkServiceException(reason.code, errMessage))
                    }

                })
                channel.unsubscribe()
                channel.subscribe(object : Channel.MessageListener {
                    override fun onMessage(message: Message) {
                        Log.d(TAG, "New messages arrived from $key. ${(message.data as ByteArray).toString(Charsets.UTF_8)}")
                        fn(message.data as ByteArray)
                    }
                })
            } catch (ex: Exception) {
                val errMessage = "Subscribe to $key failed! ${ex.message}"
                Log.e(TAG, errMessage)
                continuation.resumeWithException(NetworkServiceException.fromException(errMessage, ex))
            }
        }
    }

    override fun publish(key: String, data: ByteArray) = runBlocking {
        suspendCancellableCoroutine { continuation ->
            try {
                ensureConnected()
                val channel: Channel = ably.getChannels()[key]
                // Event name, if provided
                // The presence update payload, if provided
                channel.publish(key, data, object : CompletionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "Message sent")
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(reason: ErrorInfo) {
                        val errMessage = "Message not sent, error occurred: " + reason.message
                        Log.e(TAG, errMessage)
                        if (continuation.isActive) continuation.resumeWithException(NetworkServiceException(reason.code, message = errMessage))
                    }
                })
            } catch (ex: Exception) {
                val errMessage = "Message not sent, error occurred: " + ex.message
                Log.e(TAG, errMessage)
                continuation.resumeWithException(NetworkServiceException.fromException(errMessage, ex))
            }
        }
    }

    override fun unsubscribe(key: String) = runBlocking {
        if (!isConnected()) return@runBlocking
        suspendCancellableCoroutine { continuation ->
            try {
                val channel: Channel = ably.getChannels()[key]
                channel.detach(object : CompletionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "UnSubscribe from $key success!")
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(reason: ErrorInfo) {
                        val errMessage = "UnSubscribe from $key failed! $reason"
                        Log.e(TAG, errMessage)
                        if (continuation.isActive) continuation.resumeWithException(NetworkServiceException(reason.code, message = errMessage))
                    }
                })
            } catch (ex: Exception) {
                val errMessage = "UnSubscribe from $key failed! ${ex.message}"
                Log.e(TAG, errMessage)
                continuation.resumeWithException(NetworkServiceException.fromException(errMessage, ex))
            }
        }
    }

    override fun disconnect() = safeCall(DEFAULT_DISCONNECT_TIMEOUT_ERROR_MESSAGE, DEFAULT_DISCONNECT_ERROR_MESSAGE) {
        runBlocking {
            mutex.withLock {
                if (connectionState.value.isCloseable()) {
                    ably.getConnection().close()
                    withTimeout(config.disconnectTimeout) {
                        suspendCancellableCoroutine { continuation ->
                            ably.getConnection().on(ConnectionState.closed) {
                                Log.d(TAG, "disconnected!")
                                connectionState.value = ConnectionState.closed
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Can not disconnect, current state: ${connectionState.value}")
                    Unit
                }
            }
        }
    }

    private fun initService(token: String) {
        ablyToken = token
        val options = ClientOptions()
        //options.clientId = config.clientId
        options.echoMessages = true
        options.authCallback = Auth.TokenCallback {
            Auth.TokenDetails().apply {
                this.token = token
            }
        }
        options.autoConnect = false
        ably = AblyRealtimeWrapper(options)
    }

    private fun ensureConnected() {
        if (!isConnected()) {
            safeCall(DEFAULT_CONNECT_TIMEOUT_ERROR_MESSAGE, DEFAULT_CONNECT_ERROR_MESSAGE) {
                connect()
            }
        }
    }

    private fun connect() = runBlocking {
        if (isConnected()) return@runBlocking
        ably.getConnection().off() // will miss the disconnect event, clean the listeners before new connection
        ably.connect()
        withTimeout(config.connectTimeout) {
            suspendCancellableCoroutine { continuation ->
                ably.getConnection().on(ConnectionStateListener { change ->
                    Log.d(TAG, "New state is " + change.current.name)
                    connectionState.value = change.current
                    when (change.current) {
                        ConnectionState.connected -> {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        ConnectionState.failed -> {
                            if (continuation.isActive) continuation.resumeWithException(NetworkServiceException(message = DEFAULT_CONNECT_ERROR_MESSAGE))
                        }

                        else -> {} // Time out
                    }
                })

            }
        }
    }

    private fun safeCall(defaultTimeoutMessage: String, defaultExceptionMessage: String, action: () -> Unit) {
        try {
            action()
        } catch (timeoutException: TimeoutCancellationException) {
            Log.e(TAG, defaultTimeoutMessage, timeoutException)
            throw NetworkServiceException(message = timeoutException.message ?: defaultTimeoutMessage, cause = timeoutException)
        } catch (ex: Exception) {
            Log.e(TAG, defaultExceptionMessage, ex)
            throw NetworkServiceException.fromException(errMessage = ex.message ?: defaultExceptionMessage, ex = ex)
        }
    }

    private fun ConnectionState.isCloseable() =
        this != ConnectionState.closed &&
                this != ConnectionState.failed

    @VisibleForTesting
    internal fun setAbly(ablyRealtimeWrapper: AblyRealtimeWrapper) {
        ably = ablyRealtimeWrapper
    }

    companion object {
        private const val TAG = "AblyClientManager"
    }
}