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

        // 기존 결과 파일 읽기 (있으면 이어서 진행 - 이미 성공한 row는 skip)
        val outputFile = File("src/main/resources/furniture_keywords2_result.csv")
        val existing: Map<String, Pair<Long?, Long?>> = if (outputFile.exists()) {
            outputFile.readLines()
                .drop(1) // header
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split(",", limit = 3)
                    if (parts.isEmpty()) null
                    else parts[0] to (parts.getOrNull(1)?.toLongOrNull() to parts.getOrNull(2)?.toLongOrNull())
                }
                .toMap()
        } else {
            emptyMap()
        }

        val toCrawl = keywords.filter { kw ->
            val prev = existing[kw]
            prev == null || prev.first == null || prev.second == null
        }

        println("=== 전체 ${keywords.size}개 / 재크롤 대상 ${toCrawl.size}개 (병렬 10개, 실패 시 retry 3회) ===")

        val processedCount = AtomicInteger(0)
        val freshResults = mutableMapOf<String, Pair<Long?, Long?>>()

        // 병렬 처리 (동시 10개)
        val chunks = toCrawl.chunked(10)
        for (chunk in chunks) {
            val deferreds = chunk.map { keyword ->
                async(Dispatchers.IO) {
                    val result = crawler.getProductCountWithForeign(keyword)
                    val total = (result.total as? ProductCountResult.Success)?.count
                    val foreign = (result.foreign as? ProductCountResult.Success)?.count
                    val count = processedCount.incrementAndGet()
                    println("[$count/${toCrawl.size}] $keyword: 전체=$total, 직구=$foreign")
                    keyword to (total to foreign)
                }
            }
            freshResults.putAll(deferreds.awaitAll())
        }

        // 기존 결과 + 새 결과 병합 (새 결과 우선, 단 새 결과가 null이면 기존 값 유지)
        val merged: List<Triple<String, Long?, Long?>> = keywords.map { kw ->
            val prev = existing[kw]
            val fresh = freshResults[kw]
            val total = fresh?.first ?: prev?.first
            val foreign = fresh?.second ?: prev?.second
            Triple(kw, total, foreign)
        }

        outputFile.writeText("키워드,상품수,직구상품수\n")
        outputFile.appendText(merged.joinToString("\n") { (keyword, total, foreign) ->
            "$keyword,${total ?: ""},${foreign ?: ""}"
        })

        println("\n=== 완료: ${outputFile.absolutePath} ===")
        println("성공(전체+직구 모두): ${merged.count { it.second != null && it.third != null }}개")
        println("실패(전체 누락): ${merged.count { it.second == null }}개")
        println("실패(직구 누락): ${merged.count { it.third == null }}개")
    }
}
