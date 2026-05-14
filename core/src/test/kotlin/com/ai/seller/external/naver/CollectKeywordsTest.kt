package com.ai.seller.external.naver

import com.ai.seller.external.naver.datalab.KeywordRankResult
import com.ai.seller.external.naver.datalab.NaverDatalabClient
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Step 1만 실행: 데이터랩에서 대분류 4개 × 카테고리당 500개 키워드 수집 → CSV 저장.
 *
 * 출력: src/main/resources/keywords.csv  (키워드,카테고리)
 *
 * 이후 BrightData 크롤은 별도 테스트에서 이 CSV를 입력으로 사용.
 */
class CollectKeywordsTest {

    companion object {
        private const val KEYWORDS_PER_CATEGORY = 500
        private const val PAGE_SIZE = 20
        private const val MAX_RATE_LIMIT_RETRIES = 3
        private const val RATE_LIMIT_WAIT_MS = 60_000L
        private const val OUTPUT_PATH = "src/main/resources/keywords.csv"
    }

    private val datalab = NaverDatalabClient()

    @Test
    fun `대분류 4개 키워드 수집 → CSV 저장`() {
        println("=== 데이터랩에서 키워드 수집 ===")
        val rows = mutableListOf<Pair<String, String>>()  // keyword, category
        val seen = mutableSetOf<String>()

        for ((name, cid) in NaverDatalabClient.CATEGORIES) {
            val kws = collectKeywords(name, cid)
            println("  [$name] ${kws.size}개 수집")
            for (kw in kws) {
                if (seen.add(kw)) rows.add(kw to name)
            }
        }

        val file = File(OUTPUT_PATH)
        file.writeText("키워드,카테고리\n")
        file.appendText(rows.joinToString("\n") { (kw, cat) -> "$kw,$cat" })

        println("\n=== 완료 ===")
        println("전체 카테고리별 합계(중복 포함): ${NaverDatalabClient.CATEGORIES.size * KEYWORDS_PER_CATEGORY}")
        println("고유 키워드: ${rows.size}")
        println("저장: ${file.absolutePath}")
    }

    private fun collectKeywords(categoryName: String, cid: String): List<String> {
        val out = LinkedHashSet<String>()
        var page = 1
        var rateLimitRetry = 0
        while (out.size < KEYWORDS_PER_CATEGORY) {
            when (val res = datalab.getKeywordRank(cid = cid, count = PAGE_SIZE, page = page)) {
                is KeywordRankResult.Success -> {
                    if (res.data.ranks.isEmpty()) break
                    res.data.ranks.forEach { out.add(it.keyword) }
                    page++
                    rateLimitRetry = 0
                }
                is KeywordRankResult.Empty -> break
                is KeywordRankResult.RateLimited -> {
                    rateLimitRetry++
                    if (rateLimitRetry > MAX_RATE_LIMIT_RETRIES) {
                        println("  [$categoryName] page $page rate limit 재시도 초과")
                        break
                    }
                    println("  [$categoryName] page $page rate limited — 1분 대기 ($rateLimitRetry/$MAX_RATE_LIMIT_RETRIES)")
                    Thread.sleep(RATE_LIMIT_WAIT_MS)
                }
                is KeywordRankResult.Error -> {
                    println("  [$categoryName] page $page error: ${res.message}")
                    break
                }
            }
            Thread.sleep(100)
        }
        return out.take(KEYWORDS_PER_CATEGORY)
    }
}
