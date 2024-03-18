package com.basi.communicationservice.android

import com.basi.communicationservice.android.networkservice.AblyService
import com.bsci.communication.OTPCommunicationService
import com.basi.communicationservice.android.networkservice.NETWORK_SERVICE_PROVIDER_ABLY
import com.bsci.communication.authorization.HttpClient


fun createOTPCommunicationService(nameSpace:String, httpClient: HttpClient): OTPCommunicationService = OTPCommunicationService(nameSpace, AblyService(), httpClient)
fun OTPCommunicationService.usingAbly(): Boolean = network.provider == NETWORK_SERVICE_PROVIDER_ABLY
fun OTPCommunicationService.usingAWSIot(): Boolean = network.provider == NETWORK_SERVICE_PROVIDER_ABLY