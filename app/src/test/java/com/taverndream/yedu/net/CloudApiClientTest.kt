package com.taverndream.yedu.net

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudApiClientTest {

    private val client = CloudApiClient()

    @Test
    fun bareDomainDefaultsToHttps() {
        assertEquals(
            "https://yedu.example.com",
            client.normalizeBaseUrl("yedu.example.com"),
        )
    }

    @Test
    fun bareLanIpDefaultsToHttp() {
        assertEquals(
            "http://192.168.1.10:8787",
            client.normalizeBaseUrl("192.168.1.10:8787"),
        )
        assertEquals(
            "http://172.16.34.123:8787",
            client.normalizeBaseUrl("172.16.34.123:8787"),
        )
    }

    @Test
    fun explicitSchemeIsPreserved() {
        assertEquals(
            "https://yedu.example.com",
            client.normalizeBaseUrl("https://yedu.example.com/"),
        )
        assertEquals(
            "http://yedu.example.com:8787",
            client.normalizeBaseUrl("http://yedu.example.com:8787"),
        )
    }

    @Test
    fun localhostDefaultsToHttp() {
        assertEquals(
            "http://localhost:8787",
            client.normalizeBaseUrl("localhost:8787"),
        )
    }
}