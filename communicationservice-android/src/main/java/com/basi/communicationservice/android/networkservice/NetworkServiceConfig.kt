package com.basi.communicationservice.android.networkservice

private const val TIME_OUT_CONNECT = 10_000L
private const val TIME_OUT_DISCONNECT = 10_000L

const val NETWORK_SERVICE_PROVIDER_ABLY = "ABLY"
const val NETWORK_SERVICE_PROVIDER_AWS_IOT = "AWSIot"

data class NetworkServiceConfig(
    val clientId: String = "",
    val connectTimeout: Long = TIME_OUT_CONNECT,
    val disconnectTimeout: Long = TIME_OUT_DISCONNECT
)