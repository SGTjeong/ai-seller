package com.ai.seller.domain.converter

import com.ai.seller.domain.FulfillmentEvent
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

@Converter
class FulfillmentEventListConverter : AttributeConverter<List<FulfillmentEvent>, String> {
    private val mapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: List<FulfillmentEvent>?): String {
        return mapper.writeValueAsString(attribute ?: emptyList<FulfillmentEvent>())
    }

    override fun convertToEntityAttribute(dbData: String?): List<FulfillmentEvent> {
        if (dbData.isNullOrBlank()) return emptyList()
        return mapper.readValue<List<FulfillmentEvent>>(dbData)
    }
}
