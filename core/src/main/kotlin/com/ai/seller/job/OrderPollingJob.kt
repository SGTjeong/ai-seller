package com.ai.seller.job

import com.ai.seller.domain.*
import com.ai.seller.external.naver.commerce.NaverCommerceClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class OrderPollingJob(
    private val userRepository: UserRepository,
    private val fulfillmentRepository: FulfillmentRepository,
    private val naverClient: NaverCommerceClient,
) {

    @Scheduled(fixedDelay = 60_000) // 1분마다
    fun poll() {
        val users = userRepository.findAll()

        users.forEach { user ->
            user.credentials.forEach { credential ->
                when (credential) {
                    is User.Credential.Naver -> {
                        pollNaverOrders(user, credential)

                        // TODO: 쿠팡, 지마켓 등등 오픈마켓들 추가
                    }
                }
            }
        }
    }

    private fun pollNaverOrders(user: User, credential: User.Credential.Naver) {
        val token = naverClient.getToken(credential.clientId, credential.clientSecret)
        val from = Instant.now().minus(6, ChronoUnit.HOURS)

        naverClient.getOrders(accessToken = token.access_token, from = from).forEach { naverOrder ->
            val order = naverOrder.toOrder(
                naverClient.getProduct(token.access_token, naverOrder.productOrder.originalProductId!!.toLong())
                    .originProduct
                    .images
                    .run {
                        buildList {
                            representativeImage?.url?.let { add(it) }
                            optionalImages?.forEach { it.url?.let { add(it) } }
                        }
                    }
            )

            // 이미 존재하면 스킵
            if (fulfillmentRepository.findByUserIdAndOrderId(user.id, order.orderId) != null) {
                return@forEach
            }

            val fulfillment = Fulfillment(
                userId = user.id,
                order = order,
                status = FulfillmentStatus.STARTED,
                events = listOf(FulfillmentEvent.Started()),
            )

            fulfillmentRepository.save(fulfillment)
        }
    }
}
