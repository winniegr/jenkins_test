package com.basi.communicationservice.android.networkservice

import io.ably.lib.types.AblyException

data class NetworkServiceException(
    var errCode: Int? = null,
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {
    companion object {
        internal const val DEFAULT_CONNECT_TIMEOUT_ERROR_MESSAGE = "Network service connect timeout"
        internal const val DEFAULT_CONNECT_ERROR_MESSAGE = "Network service connect failed"
        internal const val DEFAULT_DISCONNECT_TIMEOUT_ERROR_MESSAGE = "Network service disconnect timeout"
        internal const val DEFAULT_DISCONNECT_ERROR_MESSAGE = "Network service disconnect failed"

        fun fromException(errMessage: String, ex: Exception) =
            NetworkServiceException(errCode = if (ex is AblyException) ex.errorInfo.code else null, message = errMessage, cause = ex)
    }
}