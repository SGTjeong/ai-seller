package com.ai.seller.external.naver.datalab

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class NaverDatalabClient {
    private val restClient = RestClient.builder()
        .baseUrl("https://datalab.naver.com")
        .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
        .defaultHeader(HttpHeaders.REFERER, "https://datalab.naver.com/shoppingInsight/sCategory.naver")
        .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
        .build()

    companion object {
        // 대분류 카테고리
        val CATEGORIES = mapOf(
            "가구/인테리어" to "50000004",
            "생활/건강" to "50000008",
            "스포츠/레저" to "50000007",
            "디지털/가전" to "50000003"
        )

        // 2분류 서브카테고리 (대분류 -> 중분류)
        val SUB_CATEGORIES = mapOf(
            // 가구/인테리어
            "가구/인테리어>침실가구" to "50000100",
            "가구/인테리어>거실가구" to "50000101",
            "가구/인테리어>주방가구" to "50000102",
            "가구/인테리어>수납가구" to "50000103",
            "가구/인테리어>아동/주니어가구" to "50000104",
            "가구/인테리어>서재/사무용가구" to "50000105",
            "가구/인테리어>아웃도어가구" to "50000106",
            "가구/인테리어>DIY자재/용품" to "50000107",
            "가구/인테리어>인테리어소품" to "50000108",
            "가구/인테리어>침구단품" to "50000109",
            "가구/인테리어>침구세트" to "50000110",
            "가구/인테리어>솜류" to "50000111",
            "가구/인테리어>카페트/러그" to "50000112",
            "가구/인테리어>커튼/블라인드" to "50000113",
            "가구/인테리어>홈데코" to "50000154",
            "가구/인테리어>수예" to "50000114",
            "가구/인테리어>베개" to "50016860"
        )

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    /**
     * 카테고리별 인기 키워드 조회
     */
    fun getKeywordRank(
        cid: String,
        startDate: LocalDate = LocalDate.now().minusMonths(1),
        endDate: LocalDate = LocalDate.now(),
        age: String = "",
        gender: String = "",
        device: String = "",
        page: Int = 1,
        count: Int = 20
    ): KeywordRankResult {
        return try {
            val response = restClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/shoppingInsight/getCategoryKeywordRank.naver")
                        .queryParam("cid", cid)
                        .queryParam("timeUnit", "date")
                        .queryParam("startDate", startDate.format(DATE_FORMATTER))
                        .queryParam("endDate", endDate.format(DATE_FORMATTER))
                        .queryParam("age", age)
                        .queryParam("gender", gender)
                        .queryParam("device", device)
                        .queryParam("page", page)
                        .queryParam("count", count)
                        .build()
                }
                .exchange { _, response ->
                    when {
                        response.statusCode == HttpStatusCode.valueOf(429) ->
                            KeywordRankResult.RateLimited
                        response.statusCode.is2xxSuccessful -> {
                            val body = response.bodyTo(KeywordRankResponse::class.java)
                            if (body == null || body.ranks.isEmpty()) {
                                KeywordRankResult.Empty
                            } else {
                                KeywordRankResult.Success(body)
                            }
                        }
                        else ->
                            KeywordRankResult.Error("HTTP ${response.statusCode}")
                    }
                }
            response
        } catch (e: Exception) {
            KeywordRankResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 카테고리 목록 조회
     */
    fun getCategories(cid: String = "0"): CategoryResponse? {
        return try {
            restClient.get()
                .uri("/shoppingInsight/getCategory.naver?cid=$cid")
                .retrieve()
                .body(CategoryResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 4개 주요 카테고리의 키워드 조회
     */
    fun getKeywordsForTargetCategories(
        startDate: LocalDate = LocalDate.now().minusMonths(1),
        endDate: LocalDate = LocalDate.now(),
        count: Int = 20
    ): Map<String, KeywordRankResult> {
        return CATEGORIES.mapValues { (_, cid) ->
            getKeywordRank(
                cid = cid,
                startDate = startDate,
                endDate = endDate,
                count = count
            )
        }
    }
}

sealed class KeywordRankResult {
    data class Success(val data: KeywordRankResponse) : KeywordRankResult()
    data object Empty : KeywordRankResult()
    data object RateLimited : KeywordRankResult()
    data class Error(val message: String) : KeywordRankResult()
}

data class KeywordRankResponse(
    val statusCode: Int,
    val returnCode: Int,
    val range: String?,
    val ranks: List<KeywordRank>
)

data class KeywordRank(
    val rank: Int,
    val keyword: String,
    val linkId: String
)

data class CategoryResponse(
    val cid: Int,
    val name: String,
    val childList: List<CategoryChild>?
)

data class CategoryChild(
    val cid: Int,
    val name: String
)
