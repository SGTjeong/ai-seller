package com.ai.seller.domain

data class SupplySource(
    val supplier: Supplier,
    val product: Product,
) {
    enum class Supplier {
        TAOBAO;
    }

    data class Product(
        val productName: String,
        val productOption: String,
        val productUrl: String,
        val quantity: Int,
        val unitPrice: Int,
    )
}

interface SupplySourceSelector {
    fun select(product: Order.Product): SupplySource
}
