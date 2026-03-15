package com.ai.seller.external.supply

import com.ai.seller.domain.Order
import com.ai.seller.domain.SupplySource
import com.ai.seller.domain.SupplySourceSelector
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 소싱처 선택 API 클라이언트
 *
 * Tool 서버에서 구현해야 할 API 스펙:
 *
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * POST /api/select-supply-source
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 마켓에서 주문된 상품 정보를 받아, 마진이 가장 높은 소싱처 상품을 찾아 반환합니다.
 *
 * ■ Request
 *   Content-Type: application/json
 *
 *   {
 *     "productName": string,       // (필수) 마켓 상품명
 *     "productOption": string?,    // (선택) 상품 옵션 (예: "색상: 블랙, 사이즈: L")
 *     "imageUrls": string[]?,      // (선택) 상품 이미지 URL 목록 (이미지 검색용)
 *     "quantity": number           // (필수) 주문 수량
 *   }
 *
 * ■ Response (성공)
 *   HTTP 200 OK
 *   Content-Type: application/json
 *
 *   {
 *     "success": true,
 *     "data": {
 *       "supplier": string,        // 소싱처 구분 ("TAOBAO")
 *       "productName": string,     // 소싱 상품명
 *       "productOption": string,   // 소싱 상품 옵션
 *       "productUrl": string,      // 소싱 상품 URL
 *       "quantity": number,        // 구매 수량
 *       "unitPrice": number        // 단가 (원화, KRW)
 *     },
 *     "error": null
 *   }
 *
 * ■ Response (실패)
 *   HTTP 200 OK
 *   Content-Type: application/json
 *
 *   {
 *     "success": false,
 *     "data": null,
 *     "error": string              // 에러 메시지 (예: "상품을 찾을 수 없습니다")
 *   }
 *
 * ■ 구현 가이드
 *   1. productName과 imageUrls를 활용해 타오바오에서 동일/유사 상품 검색
 *   2. 검색 결과 중 마진이 가장 높은 상품 선택 (마켓 판매가 - 소싱 단가)
 *   3. productOption이 있는 경우 해당 옵션과 매칭되는 소싱 옵션 선택
 *   4. unitPrice는 원화(KRW)로 환산하여 반환
 *
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 */
@Component
class SupplySourceSelectorImpl : SupplySourceSelector {
    private val restClient = RestClient.builder()
        .baseUrl("http://localhost:3001")
        .build()

    override fun select(product: Order.Product): SupplySource {
        val request = SelectRequest(
            productName = product.name ?: throw IllegalArgumentException("Product name is required"),
            productOption = product.option,
            imageUrls = product.imageUrls,
            quantity = product.quantity,
        )

        val response = restClient.post()
            .uri("/api/select-supply-source")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(SelectResponse::class.java)
            ?: throw RuntimeException("Failed to get response from tool server")

        if (!response.success || response.data == null) {
            throw RuntimeException(response.error ?: "Unknown error from tool server")
        }

        return SupplySource(
            supplier = SupplySource.Supplier.valueOf(response.data.supplier),
            product = SupplySource.Product(
                productName = response.data.productName,
                productOption = response.data.productOption,
                productUrl = response.data.productUrl,
                quantity = response.data.quantity,
                unitPrice = response.data.unitPrice,
            ),
        )
    }

    data class SelectRequest(
        val productName: String,
        val productOption: String?,
        val imageUrls: List<String>?,
        val quantity: Int,
    )

    data class SelectResponse(
        val success: Boolean,
        val data: SelectData?,
        val error: String?,
    )

    data class SelectData(
        val supplier: String,
        val productName: String,
        val productOption: String,
        val productUrl: String,
        val quantity: Int,
        val unitPrice: Int,
    )
}
