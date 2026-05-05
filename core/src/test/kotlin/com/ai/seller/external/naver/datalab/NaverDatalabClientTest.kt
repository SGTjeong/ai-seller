package com.ai.seller.external.naver.datalab

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NaverDatalabClientTest {

    private val client = NaverDatalabClient()

    @Test
    fun `getKeywordRank - 가구인테리어 카테고리 키워드 조회`() {
        val cid = NaverDatalabClient.CATEGORIES["가구/인테리어"]!!
        val endDate = LocalDate.now()
        val startDate = endDate.minusMonths(1)

        val result = client.getKeywordRank(
            cid = cid,
            startDate = startDate,
            endDate = endDate,
            count = 20
        )

        println("=== 가구/인테리어 인기 키워드 ===")
        when (result) {
            is KeywordRankResult.Success -> {
                result.data.ranks.forEach { rank ->
                    println("${rank.rank}. ${rank.keyword}")
                }
                assertTrue(result.data.ranks.isNotEmpty())
            }
            is KeywordRankResult.Empty -> println("데이터 없음")
            is KeywordRankResult.RateLimited -> println("Rate Limited (429)")
            is KeywordRankResult.Error -> println("에러: ${result.message}")
        }
    }

    @Test
    fun `getKeywordRank - 생활건강 카테고리 키워드 조회`() {
        val cid = NaverDatalabClient.CATEGORIES["생활/건강"]!!

        when (val result = client.getKeywordRank(cid = cid, count = 20)) {
            is KeywordRankResult.Success -> {
                println("=== 생활/건강 인기 키워드 ===")
                result.data.ranks.forEach { rank ->
                    println("${rank.rank}. ${rank.keyword}")
                }
            }
            is KeywordRankResult.Empty -> println("데이터 없음")
            is KeywordRankResult.RateLimited -> println("Rate Limited (429)")
            is KeywordRankResult.Error -> println("에러: ${result.message}")
        }
    }

    @Test
    fun `getKeywordRank - 스포츠레저 카테고리 키워드 조회`() {
        val cid = NaverDatalabClient.CATEGORIES["스포츠/레저"]!!

        when (val result = client.getKeywordRank(cid = cid, count = 20)) {
            is KeywordRankResult.Success -> {
                println("=== 스포츠/레저 인기 키워드 ===")
                result.data.ranks.forEach { rank ->
                    println("${rank.rank}. ${rank.keyword}")
                }
            }
            is KeywordRankResult.Empty -> println("데이터 없음")
            is KeywordRankResult.RateLimited -> println("Rate Limited (429)")
            is KeywordRankResult.Error -> println("에러: ${result.message}")
        }
    }

    @Test
    fun `getKeywordRank - 디지털가전 카테고리 키워드 조회`() {
        val cid = NaverDatalabClient.CATEGORIES["디지털/가전"]!!

        when (val result = client.getKeywordRank(cid = cid, count = 20)) {
            is KeywordRankResult.Success -> {
                println("=== 디지털/가전 인기 키워드 ===")
                result.data.ranks.forEach { rank ->
                    println("${rank.rank}. ${rank.keyword}")
                }
            }
            is KeywordRankResult.Empty -> println("데이터 없음")
            is KeywordRankResult.RateLimited -> println("Rate Limited (429)")
            is KeywordRankResult.Error -> println("에러: ${result.message}")
        }
    }

    @Test
    fun `getKeywordRank - 디지털가전 500개 키워드 조회`() {
        val cid = NaverDatalabClient.CATEGORIES["디지털/가전"]!!
        val allKeywords = mutableListOf<KeywordRank>()
        val pageSize = 100
        var page = 1
        var rateLimitRetryCount = 0
        val maxRateLimitRetries = 3

        println("=== 디지털/가전 키워드 500개 조회 시작 ===")

        while (allKeywords.size < 500) {
            when (val result = client.getKeywordRank(cid = cid, count = pageSize, page = page)) {
                is KeywordRankResult.Success -> {
                    allKeywords.addAll(result.data.ranks)
                    println("페이지 $page: ${result.data.ranks.size}개 조회 (누적: ${allKeywords.size}개)")
                    page++
                    rateLimitRetryCount = 0  // 성공하면 재시도 카운트 리셋
                }
                is KeywordRankResult.Empty -> {
                    println("페이지 $page: 더 이상 데이터 없음 (Empty)")
                    break
                }
                is KeywordRankResult.RateLimited -> {
                    rateLimitRetryCount++
                    if (rateLimitRetryCount > maxRateLimitRetries) {
                        println("페이지 $page: Rate Limit 재시도 횟수 초과 (${maxRateLimitRetries}회)")
                        break
                    }
                    println("페이지 $page: Rate Limited (429) - 1분 대기 후 재시도 ($rateLimitRetryCount/$maxRateLimitRetries)")
                    Thread.sleep(60_000)  // 1분 대기
                    continue  // 같은 페이지 재시도
                }
                is KeywordRankResult.Error -> {
                    println("페이지 $page: 에러 - ${result.message}")
                    break
                }
            }

            // Rate limit 방지
            Thread.sleep(100)
        }

        println("\n=== 총 ${allKeywords.size}개 키워드 ===")
        allKeywords.forEach { rank ->
            println("${rank.rank}. ${rank.keyword}")
        }
    }

    @Test
    fun `getKeywordsForTargetCategories - 4개 카테고리 키워드 한번에 조회`() {
        val results = client.getKeywordsForTargetCategories(count = 10)

        results.forEach { (category, result) ->
            println("\n=== $category ===")
            when (result) {
                is KeywordRankResult.Success -> {
                    result.data.ranks.forEach { rank ->
                        println("${rank.rank}. ${rank.keyword}")
                    }
                }
                is KeywordRankResult.Empty -> println("데이터 없음")
                is KeywordRankResult.RateLimited -> println("Rate Limited (429)")
                is KeywordRankResult.Error -> println("에러: ${result.message}")
            }
        }
    }

    @Test
    fun `getCategories - 최상위 카테고리 목록 조회`() {
        val response = client.getCategories("0")

        println("=== 카테고리 목록 ===")
        response?.childList?.forEach { child ->
            println("${child.cid}: ${child.name}")
        }
    }

    @Test
    fun `CATEGORIES - 타겟 카테고리 ID 확인`() {
        println("=== 타겟 카테고리 ===")
        NaverDatalabClient.CATEGORIES.forEach { (name, cid) ->
            println("$name: $cid")
        }

        assertTrue(NaverDatalabClient.CATEGORIES.size == 4)
        assertNotNull(NaverDatalabClient.CATEGORIES["가구/인테리어"])
        assertNotNull(NaverDatalabClient.CATEGORIES["생활/건강"])
        assertNotNull(NaverDatalabClient.CATEGORIES["스포츠/레저"])
        assertNotNull(NaverDatalabClient.CATEGORIES["디지털/가전"])
    }
}
