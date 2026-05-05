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
        val CATEGORIES = mapOf(
            "가구/인테리어" to "50000004",
            "생활/건강" to "50000008",
            "스포츠/레저" to "50000007",
            "디지털/가전" to "50000003"
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
