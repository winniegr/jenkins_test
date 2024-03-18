package com.basi.communicationservice.android.networkservice

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Connection
import io.ably.lib.types.ClientOptions

// CreateForTesting
// Mockk is incapable of mocking the public field, so create this AblyRealtime Wrapper for mocking the connection and channels
class AblyRealtimeWrapper(options: ClientOptions) : AblyRealtime(options) {
    fun getConnection(): Connection = connection
    fun getChannels(): Channels = channels
}