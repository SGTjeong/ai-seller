package com.ai.seller.domain

/**
 * Fulfillment 상태 플로우:
 *
 * STARTED ──→ SUPPLIER_SELECTED ──→ APPROVAL_REQUESTED ──┬──→ APPROVED ──→ PAYMENT_COMPLETED ──→ SHIPMENT_PREPARED (완료)
 *                                                        │              └──→ PAYMENT_FAILED
 *                                                        └──→ APPROVAL_REJECTED
 *
 * 언제든 발생 가능:
 * - CANCELLED_BY_BUYER: 구매자 취소
 * - CANCELLED_BY_SELLER: 판매자 취소
 */
enum class FulfillmentStatus {
    STARTED,
    SUPPLIER_SELECTED,
    APPROVAL_REQUESTED,
    APPROVED,
    APPROVAL_REJECTED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    SHIPMENT_PREPARED,
    CANCELLED_BY_BUYER,
    CANCELLED_BY_SELLER,
}
