package com.ai.seller.domain

import java.time.Instant

data class Order(
    val market: Market,
    val orderId: String,
    val orderedAt: Instant,
    val buyer: Buyer,
    val product: Product,
    val payment: Payment,
) {
    enum class Market {
        NAVER,
        COUPANG,
    }

    data class Buyer(
        val name: String?,
        val phone: String?,
        val address: Address?,
    )

    data class Address(
        val receiverName: String?,
        val receiverPhone: String?,
        val zipCode: String?,
        val baseAddress: String?,
        val detailAddress: String?,
    )

    data class Product(
        val id: String?,
        val name: String?,
        val option: String?,
        val quantity: Int,
        val unitPrice: Int,
        val imageUrls: List<String>,
    )

    data class Payment(
        val totalAmount: Int,
        val productAmount: Int,
        val deliveryFee: Int,
        val discountAmount: Int,
    )
}
