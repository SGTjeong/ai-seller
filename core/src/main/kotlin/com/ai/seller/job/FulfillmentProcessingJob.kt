package com.ai.seller.job

import com.ai.seller.domain.*
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class FulfillmentProcessingJob(
    private val fulfillmentRepository: FulfillmentRepository,
    private val transactionTemplate: TransactionTemplate,
    private val supplySourceSelector: SupplySourceSelector,
    private val approvalRequester: ApprovalRequester,
) {

    @Scheduled(fixedDelay = 60_000) // 1분마다
    fun process() {
        val fulfillments = fulfillmentRepository.findByStatusIn(
            listOf(
                FulfillmentStatus.STARTED,
                FulfillmentStatus.SUPPLIER_SELECTED,
                FulfillmentStatus.APPROVED,
                FulfillmentStatus.PAYMENT_COMPLETED,
            )
        )

        fulfillments.forEach { fulfillment ->
            transactionTemplate.execute {
                val locked = fulfillmentRepository.findByIdForUpdate(fulfillment.id) ?: return@execute

                when (locked.status) {
                    FulfillmentStatus.STARTED -> {
                        val supplySource = supplySourceSelector.select(locked.order.product)

                        locked.status = FulfillmentStatus.SUPPLIER_SELECTED

                        locked.events += FulfillmentEvent.SupplierSelected(supplySource)
                    }

                    FulfillmentStatus.SUPPLIER_SELECTED -> {
                        val supplySource = locked.events.filterIsInstance<FulfillmentEvent.SupplierSelected>()
                            .last().supplySource

                        approvalRequester.request(
                            Approval(
                                userId = locked.userId,
                                orderedProduct = locked.order.product,
                                supplySource = supplySource
                            )
                        )

                        locked.status = FulfillmentStatus.APPROVAL_REQUESTED

                        locked.events += FulfillmentEvent.ApprovalRequested(supplySource)
                    }

                    FulfillmentStatus.APPROVED -> {
                        TODO()
                    }

                    FulfillmentStatus.PAYMENT_COMPLETED -> {
                        TODO()
                    }

                    else -> {}
                }
            }
        }
    }
}
