package com.basi.communicationservice.android.networkservice

import java.text.SimpleDateFormat
import java.util.Date

internal fun Long.convertLongToTime(): String {
    val date = Date(this)
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return format.format(date)
}