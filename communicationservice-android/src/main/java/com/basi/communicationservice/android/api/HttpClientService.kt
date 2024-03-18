package com.basi.communicationservice.android.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url


interface HttpClientService {

    @Headers("Content-Type: application/json")
    @POST
    suspend fun post(
        @Url url: String,
        @Header("Authorization") authToken: String,
        @Body body: String
    ): String

}