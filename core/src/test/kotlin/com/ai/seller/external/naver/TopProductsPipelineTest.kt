package com.ai.seller.external.naver

import com.ai.seller.external.naver.shopping.BrightDataNaverShoppingCrawler
import com.ai.seller.external.naver.shopping.NaverProduct
import com.ai.seller.external.naver.shopping.TopProductsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase B: filtered_keywords.csv 의 키워드들로 키워드당 상위 20개 상품 추출.
 *
 * 입력:  filtered_keywords.csv (KeywordPipelineTest 출력)
 * 출력:  top_products.csv  (키워드,순위,제목,가격,판매처,리뷰수,링크,이미지,상품ID)
 *
 * 정렬: sort=review (리뷰 많은 순)
 * 재실행 시: 이미 (키워드별 1행 이상) 결과 있는 키워드는 skip.
 */
class TopProductsPipelineTest {

    companion object {
        private const val FILTERED_PATH = "src/main/resources/filtered_keywords.csv"
        private const val OUTPUT_PATH = "src/main/resources/top_products.csv"
        private const val PRODUCTS_PER_KEYWORD = 20
        private const val PARALLEL_CHUNK = 10
    }

    @Test
    fun `필터링된 키워드 → 상위 20개 상품 추출`() = runBlocking {
        val token = System.getenv("BRIGHTDATA_TOKEN") ?: run {
            println("BRIGHTDATA_TOKEN 환경변수가 설정되지 않음")
            return@runBlocking
        }

        val filteredFile = File(FILTERED_PATH)
        if (!filteredFile.exists()) {
            println("$FILTERED_PATH 가 없음 — 먼저 KeywordPipelineTest 실행 필요")
            return@runBlocking
        }

        val crawler = BrightDataNaverShoppingCrawler(token = token)

        // === filtered_keywords.csv 로드 ===
        val keywords: List<String> = filteredFile.readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { it.split(",", limit = 2).firstOrNull()?.takeIf { kw -> kw.isNotBlank() } }
        println("필터 통과 키워드 ${keywords.size}개 로드")

        // === 기존 결과(이어서 진행) ===
        val outputFile = File(OUTPUT_PATH)
        val alreadyDone: Set<String> = if (outputFile.exists()) {
            outputFile.readLines()
                .drop(1)
                .mapNotNull { it.split(",", limit = 2).firstOrNull() }
                .filter { it.isNotBlank() }
                .toSet()
        } else {
            emptySet()
        }
        val toCrawl = keywords.filter { it !in alreadyDone }
        println("재크롤 대상 ${toCrawl.size}개 (병렬 $PARALLEL_CHUNK 개)")

        // === 헤더 (덮어쓰지 않고 append 모드로 안전하게) ===
        if (!outputFile.exists()) {
            outputFile.writeText("키워드,순위,제목,가격,판매처,리뷰수,링크,이미지,상품ID\n")
        }

        val processed = AtomicInteger(0)

        for (chunk in toCrawl.chunked(PARALLEL_CHUNK)) {
            val deferreds = chunk.map { kw ->
                async(Dispatchers.IO) {
                    val res = crawler.getTopProducts(kw, limit = PRODUCTS_PER_KEYWORD)
                    val n = processed.incrementAndGet()
                    val products: List<NaverProduct> = when (res) {
                        is TopProductsResult.Success -> res.products
                        else -> emptyList()
                    }
                    println("[$n/${toCrawl.size}] $kw → ${products.size}개 (${res::class.simpleName})")
                    kw to products
                }
            }
            val batch = deferreds.awaitAll()
            // append (chunk 단위로 저장해서 도중에 죽어도 진행분은 남음)
            outputFile.appendText(
                batch.flatMap { (kw, products) ->
                    products.mapIndexed { idx, p -> toCsvRow(kw, idx + 1, p) }
                }.joinToString("\n", postfix = if (batch.any { it.second.isNotEmpty() }) "\n" else "")
            )
        }

        println("\n=== 완료: ${outputFile.absolutePath} ===")
    }

    private fun toCsvRow(keyword: String, rank: Int, p: NaverProduct): String {
        return listOf(
            csv(keyword),
            rank.toString(),
            csv(p.title),
            p.price?.toString() ?: "",
            csv(p.mall),
            p.reviewCount?.toString() ?: "",
            csv(p.link),
            csv(p.imageUrl),
            csv(p.productId)
        ).joinToString(",")
    }

    /** CSV 값 escape: 콤마/따옴표/개행 포함 시 따옴표로 감싸고 내부 따옴표는 ""로 escape. */
    private fun csv(v: String?): String {
        if (v.isNullOrBlank()) return ""
        val needsQuote = v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r')
        return if (needsQuote) "\"${v.replace("\"", "\"\"")}\"" else v
    }
}
