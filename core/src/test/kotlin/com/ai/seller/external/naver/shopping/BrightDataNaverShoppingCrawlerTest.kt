package com.ai.seller.external.naver.shopping

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class BrightDataNaverShoppingCrawlerTest {

    @Test
    fun `getProductCount - BrightData로 냉장고 검색`() {
        // 환경변수에서 토큰 가져오기
        val token = System.getenv("BRIGHTDATA_TOKEN") ?: run {
            println("BRIGHTDATA_TOKEN 환경변수가 설정되지 않음. 테스트 스킵.")
            return
        }

        val crawler = BrightDataNaverShoppingCrawler(token = token)
        val result = crawler.getProductCount("냉장고")

        println("=== BrightData 냉장고 검색 결과 ===")
        when (result) {
            is ProductCountResult.Success -> {
                println("전체 상품수: ${result.count}개")
                assertTrue(result.count > 0)
            }
            is ProductCountResult.NotFound -> println("상품수를 찾을 수 없음")
            is ProductCountResult.Blocked -> println("IP 차단 또는 CAPTCHA 발생")
            is ProductCountResult.Error -> println("에러: ${result.message}")
        }
    }

    @Test
    fun `getProductCount - BrightData로 노트북 검색`() {
        val token = System.getenv("BRIGHTDATA_TOKEN") ?: run {
            println("BRIGHTDATA_TOKEN 환경변수가 설정되지 않음. 테스트 스킵.")
            return
        }

        val crawler = BrightDataNaverShoppingCrawler(token = token)
        val result = crawler.getProductCount("노트북")

        println("=== BrightData 노트북 검색 결과 ===")
        when (result) {
            is ProductCountResult.Success -> {
                println("전체 상품수: ${result.count}개")
                // 사용자가 확인한 값: 11,759,992
                assertTrue(result.count > 10_000_000, "노트북 상품수는 천만개 이상이어야 함")
            }
            is ProductCountResult.NotFound -> println("상품수를 찾을 수 없음")
            is ProductCountResult.Blocked -> println("IP 차단 또는 CAPTCHA 발생")
            is ProductCountResult.Error -> println("에러: ${result.message}")
        }
    }

    @Test
    fun `getProductCounts - BrightData로 여러 키워드 검색`() {
        val token = System.getenv("BRIGHTDATA_TOKEN") ?: run {
            println("BRIGHTDATA_TOKEN 환경변수가 설정되지 않음. 테스트 스킵.")
            return
        }

        val crawler = BrightDataNaverShoppingCrawler(token = token)
        val keywords = listOf("냉장고", "노트북", "제습기")
        val results = crawler.getProductCounts(keywords)

        println("=== BrightData 여러 키워드 검색 결과 ===")
        results.forEach { (keyword, result) ->
            when (result) {
                is ProductCountResult.Success -> println("$keyword: ${result.count}개")
                is ProductCountResult.NotFound -> println("$keyword: 상품수를 찾을 수 없음")
                is ProductCountResult.Blocked -> println("$keyword: IP 차단 또는 CAPTCHA")
                is ProductCountResult.Error -> println("$keyword: 에러 - ${result.message}")
            }
        }
    }

    @Test
    fun `getProductCountWithForeign - 전체 + 해외직구 동시 조회`() {
        val token = System.getenv("BRIGHTDATA_TOKEN") ?: run {
            println("BRIGHTDATA_TOKEN 환경변수가 설정되지 않음. 테스트 스킵.")
            return
        }

        val crawler = BrightDataNaverShoppingCrawler(token = token)
        val result = crawler.getProductCountWithForeign("노트북")

        println("=== 노트북 전체 + 해외직구 검색 결과 ===")

        print("전체: ")
        when (val total = result.total) {
            is ProductCountResult.Success -> println("${total.count}개")
            is ProductCountResult.NotFound -> println("찾을 수 없음")
            is ProductCountResult.Blocked -> println("차단됨")
            is ProductCountResult.Error -> println("에러: ${total.message}")
        }

        print("해외직구: ")
        when (val foreign = result.foreign) {
            is ProductCountResult.Success -> println("${foreign.count}개")
            is ProductCountResult.NotFound -> println("찾을 수 없음")
            is ProductCountResult.Blocked -> println("차단됨")
            is ProductCountResult.Error -> println("에러: ${foreign.message}")
        }
    }

    @Test
    fun `extractProductCount - HTML 파싱 테스트`() {
        val html = """
            <html>
            <body>
                <div class="filter">
                    <span class="subFilter_num__S9sle">전체 123,456개</span>
                </div>
            </body>
            </html>
        """.trimIndent()

        // private 메서드 테스트를 위해 리플렉션 사용
        val crawler = BrightDataNaverShoppingCrawler(token = "test")
        val method = crawler.javaClass.getDeclaredMethod("extractProductCount", String::class.java)
        method.isAccessible = true

        val result = method.invoke(crawler, html) as Long?
        println("추출된 상품수: $result")
        assertTrue(result == 123456L)
    }
}
