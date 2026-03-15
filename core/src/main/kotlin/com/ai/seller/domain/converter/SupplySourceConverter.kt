package com.ai.seller.domain.converter

import com.ai.seller.domain.SupplySource
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

@Converter
class SupplySourceConverter : AttributeConverter<SupplySource?, String?> {
    private val mapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: SupplySource?): String? {
        return attribute?.let { mapper.writeValueAsString(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): SupplySource? {
        if (dbData.isNullOrBlank()) return null
        return mapper.readValue<SupplySource>(dbData)
    }
}
