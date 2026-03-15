package com.ai.seller.domain.converter

import com.ai.seller.domain.Order
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

@Converter
class OrderConverter : AttributeConverter<Order, String> {
    private val mapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: Order?): String {
        return mapper.writeValueAsString(attribute)
    }

    override fun convertToEntityAttribute(dbData: String?): Order {
        return mapper.readValue<Order>(dbData!!)
    }
}
