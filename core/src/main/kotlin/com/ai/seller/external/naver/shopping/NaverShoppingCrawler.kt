package com.ai.seller.external.naver.shopping

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class NaverShoppingCrawler(
    private val proxyServer: String? = null,
    private val proxyUsername: String? = null,
    private val proxyPassword: String? = null
) {
    private val logger = LoggerFactory.getLogger(NaverShoppingCrawler::class.java)

    companion object {
        // User-Agent 풀 (다양한 Chrome 버전)
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        )

        // 브라우저 자동화 탐지 우회 스크립트
        private val ANTI_DETECTION_SCRIPT = """
            (function() {
                // 1. Hide webdriver flag
                Object.defineProperty(navigator, 'webdriver', { get: function() { return undefined; } });

                // 2. Spoof plugins (real Chrome has these)
                Object.defineProperty(navigator, 'plugins', {
                    get: function() {
                        var plugins = [
                            { name: 'Chrome PDF Plugin', description: 'Portable Document Format', filename: 'internal-pdf-viewer' },
                            { name: 'Chrome PDF Viewer', description: '', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai' },
                            { name: 'Native Client', description: '', filename: 'internal-nacl-plugin' },
                        ];
                        plugins.length = 3;
                        plugins.item = function(i) { return plugins[i]; };
                        plugins.namedItem = function(name) { return plugins.find(function(p) { return p.name === name; }) || null; };
                        plugins.refresh = function() {};
                        return plugins;
                    },
                });

                // 3. Consistent languages
                Object.defineProperty(navigator, 'languages', { get: function() { return ['ko-KR', 'ko', 'en-US', 'en']; } });

                // 4. Randomize hardware concurrency (4 or 8 cores)
                var cores = Math.random() < 0.5 ? 4 : 8;
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return cores; } });

                // 5. Device memory (4 or 8 GB)
                Object.defineProperty(navigator, 'deviceMemory', { get: function() { return Math.random() < 0.5 ? 4 : 8; } });

                // 6. Randomize performance.now() offset (prevents timing-based detection)
                var perfOffset = Math.random() * 1000;
                var origNow = performance.now.bind(performance);
                performance.now = function() { return origNow() + perfOffset; };

                // 7. Hide Chrome automation flags
                if (window.chrome) {
                    window.chrome.runtime = { connect: function() {}, sendMessage: function() {} };
                } else {
                    window.chrome = { runtime: { connect: function() {}, sendMessage: function() {} } };
                }

                // 8. WebGL vendor/renderer (avoid "Google SwiftShader" which signals headless)
                try {
                    var getParam = WebGLRenderingContext.prototype.getParameter;
                    WebGLRenderingContext.prototype.getParameter = function(param) {
                        if (param === 37445) return 'Intel Inc.';
                        if (param === 37446) return 'Intel(R) Iris(TM) Plus Graphics';
                        return getParam.call(this, param);
                    };
                } catch(e) {}
            })()
        """.trimIndent()
    }

    private fun randomUserAgent(): String = USER_AGENTS.random()

    private fun humanDelay(minMs: Long = 500, maxMs: Long = 1500) {
        Thread.sleep(Random.nextLong(minMs, maxMs + 1))
    }

    private fun createBrowserContext(browser: Browser): BrowserContext {
        val context = browser.newContext(
            Browser.NewContextOptions()
                .setUserAgent(randomUserAgent())
                .setViewportSize(1440, 900)
                .setLocale("ko-KR")
                .setTimezoneId("Asia/Seoul")
                .setColorScheme(com.microsoft.playwright.options.ColorScheme.LIGHT)
                .setIgnoreHTTPSErrors(true)
        )
        // 자동화 탐지 우회 스크립트 주입
        context.addInitScript(ANTI_DETECTION_SCRIPT)
        return context
    }

    /**
     * 키워드로 검색하여 전체 상품수 조회
     */
    fun getProductCount(keyword: String): ProductCountResult {
        return try {
            Playwright.create().use { playwright ->
                val launchOptions = BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(listOf(
                        "--disable-blink-features=AutomationControlled",
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--disable-infobars",
                        "--disable-dev-shm-usage"
                    ))

                // 프록시 설정 (BrightData 등)
                if (proxyServer != null) {
                    logger.info("Using proxy: $proxyServer")
                    launchOptions.setProxy(com.microsoft.playwright.options.Proxy(proxyServer)
                        .setUsername(proxyUsername)
                        .setPassword(proxyPassword))
                }

                val browser = playwright.chromium().launch(launchOptions)
                browser.use {
                    val context = createBrowserContext(browser)
                    context.use {
                        val page = context.newPage()
                        page.setDefaultTimeout(30000.0)

                        // 워밍업: 네이버 메인 먼저 방문 (쿠키/세션 준비)
                        warmup(page)

                        val url = "https://search.shopping.naver.com/search/all?query=${keyword}"
                        page.navigate(url)

                        // 페이지 로딩 대기
                        page.waitForLoadState()
                        humanDelay(2500, 3500)

                        // CAPTCHA 또는 차단 감지
                        val bodyText = page.textContent("body") ?: ""
                        if (bodyText.contains("보안 확인") || bodyText.contains("접속이 일시적으로 제한")) {
                            logger.warn("Access blocked or CAPTCHA detected for keyword: $keyword")
                            return@use ProductCountResult.Blocked(keyword)
                        }

                        // 전체 상품수 추출 (여러 셀렉터 시도)
                        val totalCount = extractProductCount(page)

                        if (totalCount != null) {
                            ProductCountResult.Success(keyword, totalCount)
                        } else {
                            ProductCountResult.NotFound(keyword)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error crawling keyword: $keyword", e)
            ProductCountResult.Error(keyword, e.message ?: "Unknown error")
        }
    }

    /**
     * 워밍업: 네이버 메인 페이지 방문으로 쿠키/세션 준비
     */
    private fun warmup(page: Page) {
        try {
            page.navigate("https://www.naver.com")
            page.waitForLoadState()
            humanDelay(1500, 2500)
            logger.debug("Browser warmed up")
        } catch (e: Exception) {
            logger.warn("Warmup failed: ${e.message}")
        }
    }

    /**
     * 여러 키워드의 전체 상품수 조회 (브라우저 재사용)
     */
    fun getProductCounts(keywords: List<String>, delayMs: Long = 2000): Map<String, ProductCountResult> {
        if (keywords.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, ProductCountResult>()

        try {
            Playwright.create().use { playwright ->
                val launchOptions = BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(listOf(
                        "--disable-blink-features=AutomationControlled",
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--disable-infobars",
                        "--disable-dev-shm-usage"
                    ))

                // 프록시 설정 (BrightData 등)
                if (proxyServer != null) {
                    logger.info("Using proxy: $proxyServer")
                    launchOptions.setProxy(com.microsoft.playwright.options.Proxy(proxyServer)
                        .setUsername(proxyUsername)
                        .setPassword(proxyPassword))
                }

                val browser = playwright.chromium().launch(launchOptions)
                browser.use {
                    val context = createBrowserContext(browser)
                    context.use {
                        val page = context.newPage()
                        page.setDefaultTimeout(30000.0)

                        // 워밍업
                        warmup(page)

                        keywords.forEach { keyword ->
                            try {
                                val url = "https://search.shopping.naver.com/search/all?query=${keyword}"
                                page.navigate(url)
                                page.waitForLoadState()
                                humanDelay(2500, 3500)

                                // CAPTCHA 또는 차단 감지
                                val bodyText = page.textContent("body") ?: ""
                                if (bodyText.contains("보안 확인") || bodyText.contains("접속이 일시적으로 제한")) {
                                    logger.warn("Access blocked for keyword: $keyword")
                                    results[keyword] = ProductCountResult.Blocked(keyword)
                                    // 차단된 경우 더 오래 대기
                                    humanDelay(8000, 12000)
                                    return@forEach
                                }

                                val totalCount = extractProductCount(page)

                                results[keyword] = if (totalCount != null) {
                                    ProductCountResult.Success(keyword, totalCount)
                                } else {
                                    ProductCountResult.NotFound(keyword)
                                }

                                // Rate limit 방지 (랜덤 딜레이)
                                humanDelay(delayMs, delayMs + 1000)
                            } catch (e: Exception) {
                                logger.error("Error crawling keyword: $keyword", e)
                                results[keyword] = ProductCountResult.Error(keyword, e.message ?: "Unknown error")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Browser init failed", e)
            // 브라우저 초기화 실패 시 모든 키워드에 에러 반환
            keywords.forEach { keyword ->
                if (!results.containsKey(keyword)) {
                    results[keyword] = ProductCountResult.Error(keyword, "Browser init failed: ${e.message}")
                }
            }
        }

        return results
    }

    private fun extractProductCount(page: Page): Long? {
        // 콘텐츠 로딩 대기 (JavaScript 렌더링)
        try {
            page.waitForSelector("[class*='filter']", Page.WaitForSelectorOptions().setTimeout(10000.0))
        } catch (_: Exception) {
            // 타임아웃 무시
        }

        // JavaScript로 직접 추출 시도
        try {
            val jsResult = page.evaluate("""
                () => {
                    // 전체 상품수 텍스트 찾기 (다양한 패턴)
                    const allText = document.body.innerText;

                    // "전체 123,456개" 패턴
                    let match = allText.match(/전체\s*([\d,]+)\s*개/);
                    if (match) return match[1];

                    // "123,456개의 상품" 패턴
                    match = allText.match(/([\d,]+)\s*개의\s*상품/);
                    if (match) return match[1];

                    // "상품 123,456개" 패턴
                    match = allText.match(/상품\s*([\d,]+)\s*개/);
                    if (match) return match[1];

                    // 숫자개 패턴 (첫 번째 발견)
                    match = allText.match(/([\d,]{3,})\s*개/);
                    if (match) return match[1];

                    return null;
                }
            """)
            if (jsResult != null) {
                return parseCount(jsResult.toString())
            }
        } catch (_: Exception) {
        }

        // 셀렉터 시도 목록
        val selectors = listOf(
            "span.subFilter_num__S9sle",
            "[class*='productCount']",
            "[class*='totalCount']",
            "span[class*='num']"
        )

        for (selector in selectors) {
            try {
                val element = page.querySelector(selector)
                if (element != null) {
                    val text = element.textContent() ?: continue
                    val count = parseCount(text)
                    if (count != null && count > 0) {
                        return count
                    }
                }
            } catch (_: Exception) {
                continue
            }
        }

        return null
    }

    private fun parseCount(text: String): Long? {
        // 숫자와 콤마만 추출
        val numberStr = text.replace(Regex("[^0-9]"), "")
        return numberStr.toLongOrNull()
    }
}

sealed class ProductCountResult {
    abstract val keyword: String

    data class Success(override val keyword: String, val count: Long) : ProductCountResult()
    data class NotFound(override val keyword: String) : ProductCountResult()
    data class Blocked(override val keyword: String) : ProductCountResult()  // CAPTCHA 또는 IP 차단
    data class Error(override val keyword: String, val message: String) : ProductCountResult()
}
