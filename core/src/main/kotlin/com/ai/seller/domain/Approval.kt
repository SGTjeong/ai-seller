package com.ai.seller.domain

data class Approval(
    val userId: Long,
    val orderedProduct: Order.Product,
    val supplySource: SupplySource,
)

interface ApprovalRequester {
    fun request(approval: Approval)
}
