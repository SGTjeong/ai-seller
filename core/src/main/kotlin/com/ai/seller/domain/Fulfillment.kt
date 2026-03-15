package com.ai.seller.domain

import com.ai.seller.domain.converter.FulfillmentEventListConverter
import com.ai.seller.domain.converter.OrderConverter
import com.ai.seller.domain.converter.SupplySourceConverter
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

@Entity
@Table(name = "fulfillments")
class Fulfillment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val userId: Long,

    @Column(columnDefinition = "json", nullable = false)
    @Convert(converter = OrderConverter::class)
    val order: Order,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FulfillmentStatus = FulfillmentStatus.STARTED,

    @Column(columnDefinition = "json")
    @Convert(converter = FulfillmentEventListConverter::class)
    var events: List<FulfillmentEvent> = emptyList(),

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)

interface FulfillmentRepository : JpaRepository<Fulfillment, Long> {
    @Query("SELECT f FROM Fulfillment f WHERE f.userId = :userId AND JSON_EXTRACT(f.order, '$.orderId') = :orderId")
    fun findByUserIdAndOrderId(userId: Long, orderId: String): Fulfillment?

    fun findByStatusIn(statuses: List<FulfillmentStatus>): List<Fulfillment>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Fulfillment f WHERE f.id = :id")
    fun findByIdForUpdate(id: Long): Fulfillment?
}
