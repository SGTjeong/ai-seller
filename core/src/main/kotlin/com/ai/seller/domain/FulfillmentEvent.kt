package com.ai.seller.domain

import java.time.Instant

sealed class FulfillmentEvent {
    abstract val createdAt: Instant

    data class Started(
        override val createdAt: Instant = Instant.now(),
    ) : FulfillmentEvent()

    data class SupplierSelected(
        val supplySource: SupplySource,
        override val createdAt: Instant = Instant.now(),
    ) : FulfillmentEvent()

    data class ApprovalRequested(
        val supplySource: SupplySource,
        override val createdAt: Instant = Instant.now(),
    ) : FulfillmentEvent()

    data class Approved(
        override val createdAt: Instant = Instant.now(),
    ) : FulfillmentEvent()

    data class ApprovalRejected(
        val reason: String,
        override val createdAt: Instant = Instant.now(),
    ) : FulfillmentEvent()

    data class PaymentCompleted(
        override val createdAt: Instant = Instant.now(),
    ) : FulfillmentEvent()

    data class PaymentFailed(
        val reason: String,
        override val createdAt: Instant = Instant.now(),
    ) : FulfillmentEvent()

    data class ShipmentPrepared(
        override val createdAt: Instant = Instant.now(),
    ) : FulfillmentEvent()
}
