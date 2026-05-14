package com.ai.seller.external.naver

import com.ai.seller.external.naver.datalab.KeywordRankResult
import com.ai.seller.external.naver.datalab.NaverDatalabClient
import com.ai.seller.external.naver.shopping.BrightDataNaverShoppingCrawler
import com.ai.seller.external.naver.shopping.ProductCountResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase A: 대분류 4개 × 카테고리당 500키워드 수집 → 전체/직구 상품수 크롤 → 직구비율 30%↑ 필터.
 *
 * 입력: NaverDatalabClient.CATEGORIES (대분류 4개)
 * 출력:
 *  - keyword_metrics.csv      : 카테고리,키워드,전체,직구,비율  (재크롤 시 resume 용)
 *  - filtered_keywords.csv    : 비율 ≥ 30% 통과한 키워드만 (Phase B 입력)
 */
class KeywordPipelineTest {

    companion object {
        private const val KEYWORDS_PER_CATEGORY = 500
        private const val PAGE_SIZE = 20
        private const val FOREIGN_RATIO_THRESHOLD = 0.30
        private const val PARALLEL_CHUNK = 10
        private const val MAX_RATE_LIMIT_RETRIES = 3
        private const val RATE_LIMIT_WAIT_MS = 60_000L
        private const val METRICS_PATH = "src/main/resources/keyword_metrics.csv"
        private const val FILTERED_PATH = "src/main/resources/filtered_keywords.csv"
    }

    private val datalab = NaverDatalabClient()

    @Test
    fun `대분류 4개 키워드 수집 → 상품수 크롤 → 30프로 필터`() = runBlocking {
        val token = System.getenv("BRIGHTDATA_TOKEN") ?: run {
            println("BRIGHTDATA_TOKEN 환경변수가 설정되지 않음")
            return@runBlocking
        }
        val crawler = BrightDataNaverShoppingCrawler(token = token)

        // === Step 1: 키워드 수집 ===
        println("=== Step 1: 데이터랩에서 키워드 수집 ===")
        val keywordsByCategory: Map<String, List<String>> = NaverDatalabClient.CATEGORIES
            .mapValues { (name, cid) ->
                val kws = collectKeywords(name, cid)
                println("  [$name] ${kws.size}개 수집")
                kws
            }
        val categoryByKeyword: Map<String, String> = keywordsByCategory.flatMap { (cat, kws) ->
            kws.map { it to cat }
        }.toMap() // 중복 키워드 시 마지막 카테고리만 유지 (대개 무해)
        val allKeywords = categoryByKeyword.keys.toList()
        println("Step 1 완료: 전체 고유 키워드 ${allKeywords.size}개\n")

        // === Step 2: 상품수 크롤 (resume) ===
        println("=== Step 2: 전체/직구 상품수 크롤 ===")
        val metricsFile = File(METRICS_PATH)
        val existing = readMetricsCsv(metricsFile)
        val toCrawl = allKeywords.filter { kw ->
            val prev = existing[kw]
            prev == null || prev.total == null || prev.foreign == null
        }
        println("전체 ${allKeywords.size}개 / 재크롤 대상 ${toCrawl.size}개 (병렬 $PARALLEL_CHUNK 개)")

        val processed = AtomicInteger(0)
        val fresh = mutableMapOf<String, MetricRow>()
        for (chunk in toCrawl.chunked(PARALLEL_CHUNK)) {
            val deferreds = chunk.map { kw ->
                async(Dispatchers.IO) {
                    val r = crawler.getProductCountWithForeign(kw)
                    val total = (r.total as? ProductCountResult.Success)?.count
                    val foreign = (r.foreign as? ProductCountResult.Success)?.count
                    val n = processed.incrementAndGet()
                    val ratio = ratio(total, foreign)
                    println("[$n/${toCrawl.size}] $kw → 전체=$total, 직구=$foreign, 비율=${pct(ratio)}")
                    kw to MetricRow(categoryByKeyword[kw] ?: "", total, foreign)
                }
            }
            fresh.putAll(deferreds.awaitAll())
        }

        // 기존 + 새 결과 병합 (새 값 우선, null이면 기존 유지)
        val merged: Map<String, MetricRow> = allKeywords.associateWith { kw ->
            val cat = categoryByKeyword[kw] ?: ""
            val prev = existing[kw]
            val now = fresh[kw]
            MetricRow(
                category = cat,
                total = now?.total ?: prev?.total,
                foreign = now?.foreign ?: prev?.foreign
            )
        }
        writeMetricsCsv(metricsFile, merged)
        println("Step 2 완료: $METRICS_PATH 저장\n")

        // === Step 3: 30%↑ 필터 ===
        println("=== Step 3: 직구비율 ≥ ${pct(FOREIGN_RATIO_THRESHOLD)} 필터 ===")
        val filtered: List<Pair<String, MetricRow>> = merged.entries
            .mapNotNull { (kw, row) ->
                val r = ratio(row.total, row.foreign) ?: return@mapNotNull null
                if (r >= FOREIGN_RATIO_THRESHOLD) kw to row else null
            }
            .sortedByDescending { (_, r) -> ratio(r.total, r.foreign) ?: 0.0 }

        writeFilteredCsv(File(FILTERED_PATH), filtered)
        println("Step 3 완료: ${filtered.size}개 통과 → $FILTERED_PATH 저장")
        filtered.take(20).forEach { (kw, r) ->
            println("  $kw  (전체=${r.total}, 직구=${r.foreign}, 비율=${pct(ratio(r.total, r.foreign))})")
        }
    }

    /** 데이터랩에서 카테고리 인기키워드 페이지네이션으로 수집 */
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

    private data class MetricRow(val category: String, val total: Long?, val foreign: Long?)

    private fun ratio(total: Long?, foreign: Long?): Double? {
        if (total == null || foreign == null || total == 0L) return null
        return foreign.toDouble() / total.toDouble()
    }

    private fun pct(r: Double?): String = if (r == null) "-" else String.format("%.1f%%", r * 100)

    private fun readMetricsCsv(file: File): Map<String, MetricRow> {
        if (!file.exists()) return emptyMap()
        return file.readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(",", limit = 4)
                if (parts.size < 2) return@mapNotNull null
                val kw = parts[0]
                val cat = parts.getOrNull(1) ?: ""
                val total = parts.getOrNull(2)?.toLongOrNull()
                val foreign = parts.getOrNull(3)?.toLongOrNull()
                kw to MetricRow(cat, total, foreign)
            }
            .toMap()
    }

    private fun writeMetricsCsv(file: File, rows: Map<String, MetricRow>) {
        file.writeText("키워드,카테고리,전체,직구\n")
        file.appendText(rows.entries.joinToString("\n") { (kw, r) ->
            "$kw,${r.category},${r.total ?: ""},${r.foreign ?: ""}"
        })
    }

    private fun writeFilteredCsv(file: File, rows: List<Pair<String, MetricRow>>) {
        file.writeText("키워드,카테고리,전체,직구,비율\n")
        file.appendText(rows.joinToString("\n") { (kw, r) ->
            val ratio = ratio(r.total, r.foreign)
            "$kw,${r.category},${r.total ?: ""},${r.foreign ?: ""},${ratio?.let { String.format("%.4f", it) } ?: ""}"
        })
    }
}
