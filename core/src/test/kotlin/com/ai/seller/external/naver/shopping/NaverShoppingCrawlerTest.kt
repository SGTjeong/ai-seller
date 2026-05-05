package com.ai.seller.external.naver.shopping

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class NaverShoppingCrawlerTest {

    private val crawler = NaverShoppingCrawler()

    @Test
    fun `디버그 - 페이지 구조 확인`() {
        Playwright.create().use { playwright ->
            // 별도 프로필 디렉토리 (쿠키 유지)
            val userDataDir = System.getProperty("user.home") + "/.playwright-naver-profile"

            val context = playwright.chromium().launchPersistentContext(
                java.nio.file.Paths.get(userDataDir),
                BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(false)
                    .setArgs(listOf(
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox"
                    ))
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
            )
            context.use {
                val page = context.newPage()

                // 쇼핑 검색으로 바로 이동
                page.navigate("https://search.shopping.naver.com/search/all?query=냉장고")
                page.waitForLoadState()

                // 30초 대기 - 캡차가 나오면 수동으로 풀어주세요!
                println(">>> 캡차가 나오면 30초 안에 풀어주세요! <<<")
                Thread.sleep(30000)

                // 페이지 타이틀 확인
                println("=== 페이지 타이틀 ===")
                println(page.title())

                // 페이지 URL 확인
                println("\n=== 현재 URL ===")
                println(page.url())

                // 페이지 텍스트에서 "개" 포함된 부분 찾기
                val text = page.evaluate("""
                    () => {
                        const text = document.body.innerText;
                        const lines = text.split('\n').filter(l => l.includes('개'));
                        return lines.slice(0, 20).join('\n');
                    }
                """)
                println("\n=== '개' 포함된 텍스트 ===")
                println(text)

                // 숫자 패턴 찾기
                val numbers = page.evaluate("""
                    () => {
                        const text = document.body.innerText;
                        const matches = text.match(/[\d,]+\s*개/g);
                        return matches ? matches.slice(0, 10).join(', ') : 'none';
                    }
                """)
                println("\n=== 숫자+개 패턴 ===")
                println(numbers)

                // HTML 일부 확인
                val html = page.evaluate("() => document.body.innerHTML.substring(0, 2000)")
                println("\n=== HTML 일부 ===")
                println(html)
            }
        }
    }

    @Test
    fun `getProductCount - 냉장고 검색`() {
        val result = crawler.getProductCount("냉장고")

        println("=== 냉장고 검색 결과 ===")
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
    fun `getProductCount - 휴대폰거치대 검색`() {
        val result = crawler.getProductCount("휴대폰거치대")

        println("=== 휴대폰거치대 검색 결과 ===")
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
    fun `getProductCounts - 여러 키워드 검색`() {
        val keywords = listOf("냉장고", "노트북", "제습기")
        val results = crawler.getProductCounts(keywords, delayMs = 2000)

        println("=== 여러 키워드 검색 결과 ===")
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
    fun `getProductCount - BrightData 프록시로 테스트`() {
        // BrightData 프록시 설정
        // Zone: Residential Proxies
        // Host: brd.superproxy.io:33335
        // Username: brd-customer-{CUSTOMER_ID}-zone-{ZONE_NAME}
        // Password: {ZONE_PASSWORD}
        val proxyServer = System.getenv("BRIGHTDATA_PROXY_SERVER") ?: return
        val proxyUsername = System.getenv("BRIGHTDATA_PROXY_USERNAME") ?: return
        val proxyPassword = System.getenv("BRIGHTDATA_PROXY_PASSWORD") ?: return

        val proxyCrawler = NaverShoppingCrawler(proxyServer, proxyUsername, proxyPassword)
        val result = proxyCrawler.getProductCount("냉장고")

        println("=== BrightData 프록시 테스트 결과 ===")
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
}
