package com.ai.seller.external.naver

import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.temporal.ChronoUnit

class NaverClientTest {
    private val client = NaverClient()

    private val clientId = "REDACTED"
    private val clientSecret = "REDACTED"

    @Test
//    @Disabled("실제 API 호출 테스트 - 필요시 활성화")
    fun `토큰 발급 테스트`() {
        val response = client.getToken(clientId, clientSecret)

        println("access_token: ${response.access_token}")
        println("expires_in: ${response.expires_in}")
        println("token_type: ${response.token_type}")
    }

    @Test
//    @Disabled("실제 API 호출 테스트 - 필요시 활성화")
    fun `주문 조회 테스트`() {
        val token = client.getToken(clientId, clientSecret)
        println("token: ${token.access_token}")

        val from = Instant.now().minus(12, ChronoUnit.HOURS)
        val response = client.getOrders(
            accessToken = token.access_token,
            from = from,
        )

        println(jacksonObjectMapper().writeValueAsString(response))
    }

    @Test
//    @Disabled("실제 API 호출 테스트 - 필요시 활성화")
    fun `원상품 조회 테스트`() {
        val token = client.getToken(clientId, clientSecret)
        println("token: ${token.access_token}")

        val originProductNo = 13146284844L
        val response = client.getProduct(
            accessToken = token.access_token,
            originProductNo = originProductNo,
        )

        println("상품명: ${response.originProduct.name}")
        println("판매가: ${response.originProduct.salePrice}")
        println("상태: ${response.originProduct.statusType}")
        println("재고: ${response.originProduct.stockQuantity}")
        println("전체 응답: ${jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(response)}")
    }
}
