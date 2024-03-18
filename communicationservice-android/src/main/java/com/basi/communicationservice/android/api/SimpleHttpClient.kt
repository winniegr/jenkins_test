package com.basi.communicationservice.android.api

import com.bsci.communication.authorization.HttpClient
import com.bsci.communication.authorization.HttpClientRequest
import com.bsci.communication.authorization.HttpClientResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory


private const val BASE_URL = "https://dev.bscdv.com:443/platform-communication-service/session/"

class SimpleHttpClient : HttpClient {

    override fun execute(request: HttpClientRequest): HttpClientResponse = runBlocking {
        val response = getRetrofit().create(HttpClientService::class.java).post(
            url = BASE_URL + request.path,
            authToken = BEARER + " " + getAuthHeader()[BEARER]!!,
            body = request.body
        )
        object : HttpClientResponse {
            override val body = response
            override val code = 200u
            override val request = request
        }
    }

    override fun getAuthHeader(): Map<String, String> {
        return mutableMapOf(BEARER to "FpFFueQHe4fnc8cKd0P8Cy7qz-o")
    }

    companion object {
        private const val BEARER = "Bearer"

        fun getRetrofit(): Retrofit {
            val httpClient = OkHttpClient.Builder().addInterceptor(
                HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
            ).build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .build()

        }
    }
}