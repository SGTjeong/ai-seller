package com.ai.seller.external.naver.shopping

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class BrightDataKeywordCrawlTest {

    @Test
    fun `furniture_keywords 전체상품수 및 직구상품수 조회`() = runBlocking {
        val token = System.getenv("BRIGHTDATA_TOKEN") ?: run {
            println("BRIGHTDATA_TOKEN 환경변수가 설정되지 않음")
            return@runBlocking
        }

        val crawler = BrightDataNaverShoppingCrawler(token = token)

        // 키워드 파일 읽기
        val inputFile = File("src/main/resources/furniture_keywords2.csv")
        val keywords = inputFile.readLines().filter { it.isNotBlank() }

        println("=== ${keywords.size}개 키워드 크롤링 시작 (병렬 20개) ===")

        val results = mutableListOf<Triple<String, Long?, Long?>>()
        val processedCount = AtomicInteger(0)

        // 병렬 처리 (동시 20개)
        val chunks = keywords.chunked(20)

        for (chunk in chunks) {
            val deferreds = chunk.map { keyword ->
                async(Dispatchers.IO) {
                    val result = crawler.getProductCountWithForeign(keyword)
                    val total = (result.total as? ProductCountResult.Success)?.count
                    val foreign = (result.foreign as? ProductCountResult.Success)?.count
                    val count = processedCount.incrementAndGet()
                    println("[$count/${keywords.size}] $keyword: 전체=$total, 직구=$foreign")
                    Triple(keyword, total, foreign)
                }
            }
            results.addAll(deferreds.awaitAll())
        }

        // CSV 파일로 저장
        val outputFile = File("src/main/resources/furniture_keywords2_result.csv")
        outputFile.writeText("키워드,상품수,직구상품수\n")
        outputFile.appendText(results.joinToString("\n") { (keyword, total, foreign) ->
            "$keyword,${total ?: ""},${foreign ?: ""}"
        })

        println("\n=== 완료: ${outputFile.absolutePath} ===")
        println("성공: ${results.count { it.second != null }}개")
        println("실패: ${results.count { it.second == null }}개")
    }
}
