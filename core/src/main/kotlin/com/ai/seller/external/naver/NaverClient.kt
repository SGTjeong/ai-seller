package com.ai.seller.external.naver

import org.mindrot.jbcrypt.BCrypt
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Component
class NaverClient {
    private val restClient = RestClient.builder()
        .baseUrl("https://api.commerce.naver.com")
        .build()

    fun getToken(clientId: String, clientSecret: String): NaverTokenResponse {
        val timestamp = System.currentTimeMillis()

        val signature = run {
            val password = "${clientId}_${timestamp}"
            val hashed = BCrypt.hashpw(password, clientSecret)
            Base64.getUrlEncoder().encodeToString(hashed.toByteArray())
        }

        val formData = LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("timestamp", timestamp.toString())
            add("grant_type", "client_credentials")
            add("client_secret_sign", signature)
            add("type", "SELF")
        }

        return restClient.post()
            .uri("/external/v1/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formData)
            .retrieve()
            .body(NaverTokenResponse::class.java)!!
    }

    fun getOrders(
        accessToken: String,
        from: Instant,
        to: Instant? = null,
        productOrderStatuses: List<String>? = null,
        pageSize: Int = 300,
        page: Int = 1,
    ): List<NaverOrder> {
        val queryParams = mutableListOf("from=${from.toNaverFormat()}")
        to?.let { queryParams.add("to=${it.toNaverFormat()}") }
        productOrderStatuses?.let { queryParams.add("productOrderStatuses=${it.joinToString(",")}") }
        queryParams.add("pageSize=$pageSize")
        queryParams.add("page=$page")

        val uri = URI.create("/external/v1/pay-order/seller/product-orders?${queryParams.joinToString("&")}")

        return restClient.get()
            .uri(uri)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(NaverOrderListResponse::class.java)!!
            .data
            .contents
            .map { it.content }
    }

    fun getProduct(accessToken: String, originProductNo: Long): NaverProductResponse {
        return restClient.get()
            .uri("/external/v2/products/origin-products/$originProductNo")
            .header("Accept", "application/json;charset=UTF-8")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(NaverProductResponse::class.java)!!
    }

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    private fun Instant.toNaverFormat(): String =
        ZonedDateTime.ofInstant(this, ZoneOffset.UTC).format(dateTimeFormatter)
}

data class NaverTokenResponse(
    val access_token: String,
    val expires_in: Int,
    val token_type: String,
)

private data class NaverOrderListResponse(
    val timestamp: Instant?,
    val traceId: String?,
    val data: NaverOrderDataWrapper,
)

private data class NaverOrderDataWrapper(
    val contents: List<NaverOrderItem>,
    val pagination: NaverPagination,
)

private data class NaverOrderItem(
    val productOrderId: String,
    val content: NaverOrder,
)

private data class NaverPagination(
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
)

data class NaverProductResponse(
    val originProduct: NaverOriginProduct,
    val smartstoreChannelProduct: NaverSmartstoreChannelProduct? = null,
    val windowChannelProduct: NaverWindowChannelProduct? = null,
    val groupProduct: NaverGroupProduct? = null,
)

data class NaverOriginProduct(
    val statusType: String,
    val saleType: String? = null,
    val leafCategoryId: String? = null,
    val name: String,
    val detailContent: String,
    val images: NaverProductImages,
    val saleStartDate: String? = null,
    val saleEndDate: String? = null,
    val salePrice: Long,
    val stockQuantity: Int? = null,
    val deliveryInfo: NaverDeliveryInfo? = null,
    val productLogistics: List<NaverProductLogistics>? = null,
    val detailAttribute: NaverDetailAttribute? = null,
    val customerBenefit: NaverCustomerBenefit? = null,
)

data class NaverProductImages(
    val representativeImage: NaverImage? = null,
    val optionalImages: List<NaverImage>? = null,
)

data class NaverImage(
    val url: String? = null,
)

data class NaverDeliveryInfo(
    val deliveryType: String? = null,
    val deliveryAttributeType: String? = null,
    val deliveryFee: NaverDeliveryFee? = null,
    val claimDeliveryInfo: NaverClaimDeliveryInfo? = null,
)

data class NaverDeliveryFee(
    val deliveryFeeType: String? = null,
    val baseFee: Int? = null,
    val freeConditionalAmount: Int? = null,
)

data class NaverClaimDeliveryInfo(
    val returnDeliveryFee: Int? = null,
    val exchangeDeliveryFee: Int? = null,
)

data class NaverProductLogistics(
    val logisticsCompanyId: String? = null,
    val logisticsCompanyName: String? = null,
)

data class NaverDetailAttribute(
    val naverShoppingSearchInfo: NaverShoppingSearchInfo? = null,
    val afterServiceInfo: NaverAfterServiceInfo? = null,
    val originAreaInfo: NaverOriginAreaInfo? = null,
    val optionInfo: NaverOptionInfo? = null,
    val supplementProductInfo: NaverSupplementProductInfo? = null,
    val purchaseQuantityInfo: NaverPurchaseQuantityInfo? = null,
    val taxType: String? = null,
    val productCertificationInfos: List<NaverProductCertificationInfo>? = null,
    val certificationTargetExcludeContent: NaverCertificationTargetExcludeContent? = null,
    val sellerCodeInfo: NaverSellerCodeInfo? = null,
    val minorPurchasable: Boolean? = null,
    val productInfoProvidedNotice: NaverProductInfoProvidedNotice? = null,
    val productAttributes: List<NaverProductAttribute>? = null,
    val itselfProductionProductYn: Boolean? = null,
    val brandCertificationYn: Boolean? = null,
)

data class NaverShoppingSearchInfo(
    val modelId: Long? = null,
    val modelName: String? = null,
    val manufacturerName: String? = null,
    val brandId: Long? = null,
    val brandName: String? = null,
)

data class NaverAfterServiceInfo(
    val afterServiceTelephoneNumber: String? = null,
    val afterServiceGuideContent: String? = null,
)

data class NaverOriginAreaInfo(
    val originAreaCode: String? = null,
    val content: String? = null,
    val plural: Boolean? = null,
)

data class NaverOptionInfo(
    val simpleOptionSortType: String? = null,
    val optionSimple: List<NaverOptionSimple>? = null,
    val optionCustom: List<NaverOptionCustom>? = null,
    val optionCombinationSortType: String? = null,
    val optionCombinationGroupNames: NaverOptionCombinationGroupNames? = null,
    val optionCombinations: List<NaverOptionCombination>? = null,
    val standardOptionGroups: List<NaverStandardOptionGroup>? = null,
    val useStockManagement: Boolean? = null,
    val optionDeliveryAttributes: List<NaverOptionDeliveryAttribute>? = null,
)

data class NaverOptionSimple(
    val id: Long? = null,
    val groupName: String? = null,
    val name: String? = null,
    val usable: Boolean? = null,
)

data class NaverOptionCustom(
    val id: Long? = null,
    val groupName: String? = null,
    val name: String? = null,
    val usable: Boolean? = null,
)

data class NaverOptionCombinationGroupNames(
    val optionGroupName1: String? = null,
    val optionGroupName2: String? = null,
    val optionGroupName3: String? = null,
    val optionGroupName4: String? = null,
)

data class NaverOptionCombination(
    val id: Long? = null,
    val optionName1: String? = null,
    val optionName2: String? = null,
    val optionName3: String? = null,
    val optionName4: String? = null,
    val stockQuantity: Int? = null,
    val price: Int? = null,
    val usable: Boolean? = null,
    val sellerManagerCode: String? = null,
)

data class NaverStandardOptionGroup(
    val groupName: String? = null,
    val standardOptionAttributes: List<NaverStandardOptionAttribute>? = null,
)

data class NaverStandardOptionAttribute(
    val attributeId: Long? = null,
    val attributeValueId: Long? = null,
    val attributeValueName: String? = null,
    val imageUrls: List<String>? = null,
)

data class NaverOptionDeliveryAttribute(
    val id: Long? = null,
    val groupName: String? = null,
    val name: String? = null,
    val price: Int? = null,
    val usable: Boolean? = null,
)

data class NaverSupplementProductInfo(
    val sortType: String? = null,
    val supplementProducts: List<NaverSupplementProduct>? = null,
)

data class NaverSupplementProduct(
    val id: Long? = null,
    val groupName: String? = null,
    val name: String? = null,
    val price: Int? = null,
    val stockQuantity: Int? = null,
    val usable: Boolean? = null,
    val sellerManagerCode: String? = null,
)

data class NaverPurchaseQuantityInfo(
    val minPurchaseQuantity: Int? = null,
    val maxPurchaseQuantityPerId: Int? = null,
    val maxPurchaseQuantityPerOrder: Int? = null,
)

data class NaverProductCertificationInfo(
    val certificationInfoId: Long? = null,
    val certificationKindType: String? = null,
    val name: String? = null,
    val certificationNumber: String? = null,
    val certificationMark: Boolean? = null,
    val certificationCompanyName: String? = null,
    val certificationDate: String? = null,
)

data class NaverCertificationTargetExcludeContent(
    val childCertifiedProductExclusionYn: Boolean? = null,
    val kcExemptionType: String? = null,
    val kcCertifiedProductExclusionYn: String? = null,
    val greenCertifiedProductExclusionYn: Boolean? = null,
)

data class NaverSellerCodeInfo(
    val sellerManagementCode: String? = null,
    val sellerBarcode: String? = null,
    val sellerCustomCode1: String? = null,
    val sellerCustomCode2: String? = null,
)

data class NaverProductInfoProvidedNotice(
    val productInfoProvidedNoticeType: String? = null,
    val productInfoProvidedNoticeContent: Map<String, Any>? = null,
)

data class NaverProductAttribute(
    val attributeSeq: Long? = null,
    val attributeId: Long? = null,
    val attributeName: String? = null,
    val attributeValueId: Long? = null,
    val attributeValueName: String? = null,
)

data class NaverCustomerBenefit(
    val immediateDiscountPolicy: NaverImmediateDiscountPolicy? = null,
    val purchasePointPolicy: NaverPurchasePointPolicy? = null,
    val reviewPointPolicy: NaverReviewPointPolicy? = null,
    val freeInterestPolicy: NaverFreeInterestPolicy? = null,
    val giftPolicy: NaverGiftPolicy? = null,
    val multiPurchaseDiscountPolicy: NaverMultiPurchaseDiscountPolicy? = null,
)

data class NaverImmediateDiscountPolicy(
    val discountMethod: NaverDiscountMethod? = null,
    val mobileDiscountMethod: NaverDiscountMethod? = null,
)

data class NaverDiscountMethod(
    val value: Int? = null,
    val unitType: String? = null,
)

data class NaverPurchasePointPolicy(
    val value: Int? = null,
    val unitType: String? = null,
)

data class NaverReviewPointPolicy(
    val textReviewPoint: Int? = null,
    val photoVideoReviewPoint: Int? = null,
    val afterUseTextReviewPoint: Int? = null,
    val afterUsePhotoVideoReviewPoint: Int? = null,
    val storeMemberReviewPoint: Int? = null,
)

data class NaverFreeInterestPolicy(
    val freeInterestMonths: List<Int>? = null,
)

data class NaverGiftPolicy(
    val presentContent: String? = null,
)

data class NaverMultiPurchaseDiscountPolicy(
    val discountMethod: NaverDiscountMethod? = null,
    val orderValue: Int? = null,
    val orderValueUnitType: String? = null,
)

data class NaverSmartstoreChannelProduct(
    val channelProductName: String? = null,
    val bbsSeq: Long? = null,
    val storeKeepExclusiveProduct: Boolean? = null,
    val naverShoppingRegistration: Boolean,
    val channelProductDisplayStatusType: String,
)

data class NaverWindowChannelProduct(
    val channelProductName: String? = null,
    val bbsSeq: Long? = null,
    val storeKeepExclusiveProduct: Boolean? = null,
    val naverShoppingRegistration: Boolean,
    val channelNo: Long,
    val best: Boolean? = null,
    val channelProductDisplayStatusType: String? = null,
)

data class NaverGroupProduct(
    val groupProductNo: Long? = null,
    val leafCategoryId: String? = null,
    val groupProductName: String? = null,
)
