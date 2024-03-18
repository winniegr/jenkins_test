package com.basi.communicationservice.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.basi.communicationservice.android.api.SimpleHttpClient
import com.basi.communicationservice.android.createOTPCommunicationService
import com.basi.communicationservice.android.networkservice.AblyService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //testAblyService()
        testCommunicationService()
    }

    private fun testAblyService() {
        lifecycleScope.launch(Dispatchers.IO) {
            AblyService().run {
                connect(TEST_ABLY_TOKEN)
                publish(TEST_TOPIC_NAME, TEST_MESSAGE.toByteArray())
            }
        }
    }

    private fun testCommunicationService() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cs = createOTPCommunicationService(DEFAULT_NAME_SPACE, SimpleHttpClient())
            val otp = cs.authorizeAndGetOPT()
            Log.d(TAG, "OTP is $otp")
            cs.connect()
            val channel = cs.createSyncChanel(DEFAULT_TOPIC_NAME)
            try {
                val response = channel.send(TEST_MESSAGE.toByteArray())
                Log.d(TAG, "response is ${response.toString(Charsets.UTF_8)}")
            } catch (e: CancellationException) {
                Log.w(TAG, "Timeout for getting the response from communication service")
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_NAME_SPACE = "RP"
        private const val DEFAULT_TOPIC_NAME = "IPGCmd"
        private const val TEST_ABLY_TOKEN =
            "jFQuFw.KlugsdnAwdtll0duhbafRAgqgu64KHYEhOm7QqqWucowjj-pSwJqM9coFWi34yC-dzpDoYyzLnfVHNpDl4uPRuAbH6Wkjuppd3dUOQCD_7uPNXCTPaZ8b9A7C1LTJUI9mz9ngWbSpjWVSYxZ16oCIT00vwHpr0eLKtXRaUkG8iqBaSg5HYGrKGg7Jsf68PtZN6jCN_bMjUKY4ylt85LwUt8TbkSv55afmuA4BmbU3UsVSymz07_ZtHtMAOO81cN40muLhWO7PBdIJKLLq9HZcjw"
        private const val TEST_TOPIC_NAME = "$DEFAULT_NAME_SPACE:ed4ffdba-9137-4dbb-932b-b6584ee70892:$DEFAULT_TOPIC_NAME"
        private const val TEST_MESSAGE = "Hello world!"
    }
}