package com.ritam.azureload.core.network

import java.io.IOException

class FatalDownloadException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

class TransientDownloadException(
    message: String,
    val statusCode: Int? = null,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null
) : IOException(message, cause)

class RangeNotSupportedException(
    message: String
) : IOException(message)
