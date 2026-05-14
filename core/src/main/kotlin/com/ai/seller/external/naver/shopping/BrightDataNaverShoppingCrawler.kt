package com.ai.seller.external.naver.shopping

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * BrightData Datasets API를 통해 네이버 쇼핑 페이지 HTML을 받아오는 크롤러.
 * (BrightDataCoupangCrawler.ts 참고)
 *
 * 동작:
 *  - 검색페이지: dataset_id 호출 → page_html 받아 Jsoup으로 상품수 추출
 *  - 일시적 실패(API 오류, 차단, 추출 실패) 시 최대 3회까지 재시도
 */
@Component
class BrightDataNaverShoppingCrawler(
    @Value("\${brightdata.token:}") private val token: String = "",
    @Value("\${brightdata.dataset-id:gd_m6gjtfmeh43we6cqc}") private val datasetId: String = "gd_m6gjtfmeh43we6cqc",
    @Value("\${brightdata.timeout-seconds:120}") private val timeoutSeconds: Int = 120,
    @Value("\${brightdata.max-attempts:3}") private val maxAttempts: Int = 3,
    @Value("\${brightdata.retry-base-delay-ms:5000}") private val retryBaseDelayMs: Long = 5000,
    @Value("\${brightdata.poll-interval-ms:5000}") private val pollIntervalMs: Long = 5000,
    @Value("\${brightdata.poll-max-attempts:6}") private val pollMaxAttempts: Int = 6
) {
    private val logger = LoggerFactory.getLogger(BrightDataNaverShoppingCrawler::class.java)

    // RestClient에 connect/read timeout을 명시적으로 설정.
    // 미설정 시 BrightData 서버 응답이 끊겼을 때(예: 502 후 hang) 무한 대기로 polling이 멈춤.
    private val restClient = RestClient.builder()
        .baseUrl("https://api.brightdata.com")
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(10_000)
                setReadTimeout(90_000)
            }
        )
        .build()

    /**
     * 키워드로 검색하여 전체 상품수 조회
     */
    fun getProductCount(keyword: String): ProductCountResult {
        if (token.isBlank()) {
            return ProductCountResult.Error(keyword, "BrightData token not configured")
        }
        val searchUrl = buildSearchUrl(keyword)
        return crawlWithRetry(keyword, searchUrl, label = "total")
    }

    /**
     * 여러 키워드의 전체 상품수 조회
     */
    fun getProductCounts(keywords: List<String>): Map<String, ProductCountResult> {
        return keywords.associateWith { getProductCount(it) }
    }

    /**
     * 전체 + 해외직구 상품수 동시 조회 (두 번의 API 호출)
     * 해외직구 상품수는 productSet=overseas 파라미터를 사용해야 정확히 가져올 수 있음
     */
    fun getProductCountWithForeign(keyword: String): ProductCountWithForeignResult {
        if (token.isBlank()) {
            val error = ProductCountResult.Error(keyword, "BrightData token not configured")
            return ProductCountWithForeignResult(keyword, error, error)
        }

        val totalResult = crawlWithRetry(keyword, buildSearchUrl(keyword), label = "total")
        val foreignResult = crawlWithRetry(keyword, buildSearchUrl(keyword, overseas = true), label = "foreign")
        return ProductCountWithForeignResult(keyword, totalResult, foreignResult)
    }

    /**
     * 단일 URL 크롤링 + 최대 maxAttempts 회 재시도.
     * Success가 나오면 즉시 반환, 그 외(Error/Blocked/NotFound)는 백오프 후 재시도.
     */
    private fun crawlWithRetry(keyword: String, url: String, label: String): ProductCountResult {
        var last: ProductCountResult = ProductCountResult.Error(keyword, "no attempts made")
        for (attempt in 1..maxAttempts) {
            last = crawlOnce(keyword, url, label, attempt)
            if (last is ProductCountResult.Success) return last
            if (attempt < maxAttempts) {
                // exponential backoff: base, base*2, base*4, ...
                val delay = retryBaseDelayMs * (1L shl (attempt - 1))
                logger.warn("Retrying $label '$keyword' (attempt ${attempt + 1}/$maxAttempts) after ${delay}ms — last=$last")
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return last
                }
            }
        }
        logger.warn("Exhausted $maxAttempts attempts for $label '$keyword' — final=$last")
        return last
    }

    private fun crawlOnce(keyword: String, url: String, label: String, attempt: Int): ProductCountResult {
        return try {
            logger.info("Fetching $label count for keyword: $keyword (attempt $attempt/$maxAttempts), url: $url")
            val html = fetchPageHtml(url)
            when {
                html == null -> {
                    logger.warn("Failed to fetch page HTML ($label) for keyword: $keyword")
                    ProductCountResult.Error(keyword, "Failed to fetch page")
                }
                html.contains("접속이 일시적으로 제한") || html.contains("보안 확인") -> {
                    logger.warn("Access blocked ($label) for keyword: $keyword")
                    ProductCountResult.Blocked(keyword)
                }
                else -> {
                    val count = extractProductCount(html)
                    if (count != null) {
                        logger.info("$label product count for '$keyword': $count")
                        ProductCountResult.Success(keyword, count)
                    } else {
                        logger.warn("Product count not found ($label) for keyword: $keyword")
                        ProductCountResult.NotFound(keyword)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error crawling $label keyword: $keyword (attempt $attempt)", e)
            ProductCountResult.Error(keyword, e.message ?: "Unknown error")
        }
    }

    /**
     * BrightData Datasets API를 통해 페이지 HTML 조회
     */
    private fun fetchPageHtml(targetUrl: String): String? {
        val apiUrl = "/datasets/v3/scrape?dataset_id=$datasetId&include_errors=true"
        val payload = """{"input": [{"url": "$targetUrl"}]}"""

        return try {
            val response = restClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String::class.java)

            if (response.isNullOrBlank()) {
                logger.warn("Empty response from BrightData API")
                return null
            }

            logger.debug("BrightData response length: ${response.length}")

            // sync 응답에 page_html이 있으면 즉시 추출
            if (response.contains("\"page_html\":\"")) {
                return extractPageHtmlFromJson(response)
            }

            // sync timeout 시 BrightData가 snapshot_id만 반환하고 비동기 모드로 전환됨.
            // → progress polling 후 snapshot 다운로드.
            val snapshotId = extractStringField(response, "snapshot_id")
            if (snapshotId != null) {
                logger.info("Sync timeout — polling snapshot $snapshotId")
                return pollSnapshotForHtml(snapshotId)
            }

            // 그 외(에러 응답 등) — 진단 로그 남기고 null
            extractPageHtmlFromJson(response)
        } catch (e: Exception) {
            logger.error("BrightData API call failed", e)
            null
        }
    }

    /**
     * 비동기 모드 응답을 받았을 때 progress polling → snapshot 다운로드.
     */
    private fun pollSnapshotForHtml(snapshotId: String): String? {
        for (attempt in 1..pollMaxAttempts) {
            try {
                Thread.sleep(pollIntervalMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }

            val progress = try {
                restClient.get()
                    .uri("/datasets/v3/progress/$snapshotId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .retrieve()
                    .body(String::class.java)
            } catch (e: Exception) {
                logger.warn("Progress poll failed for $snapshotId: ${e.message}")
                null
            } ?: continue

            when (val status = extractStringField(progress, "status")) {
                "ready" -> return downloadSnapshot(snapshotId)
                "failed" -> {
                    logger.warn("Snapshot $snapshotId failed: ${progress.take(200)}")
                    return null
                }
                else -> logger.debug("Snapshot $snapshotId status=$status (poll $attempt/$pollMaxAttempts)")
            }
        }
        logger.warn("Snapshot $snapshotId polling timed out after ${pollMaxAttempts * pollIntervalMs}ms")
        return null
    }

    private fun downloadSnapshot(snapshotId: String): String? {
        return try {
            val response = restClient.get()
                .uri("/datasets/v3/snapshot/$snapshotId?format=json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .retrieve()
                .body(String::class.java)

            if (response.isNullOrBlank()) {
                logger.warn("Empty snapshot $snapshotId download response")
                null
            } else {
                extractPageHtmlFromJson(response)
            }
        } catch (e: Exception) {
            logger.error("Snapshot $snapshotId download failed", e)
            null
        }
    }

    private fun extractStringField(json: String, field: String): String? {
        val marker = "\"$field\":\""
        val start = json.indexOf(marker)
        if (start == -1) return null
        val contentStart = start + marker.length
        val end = json.indexOf("\"", contentStart)
        if (end == -1) return null
        return json.substring(contentStart, end)
    }

    /**
     * JSON 응답에서 page_html 필드 추출
     * BrightData API는 application/octet-stream으로 응답하므로 수동 파싱
     */
    private fun extractPageHtmlFromJson(json: String): String? {
        // "page_html": "..." 패턴 찾기
        val startMarker = "\"page_html\":\""
        val startIndex = json.indexOf(startMarker)
        if (startIndex == -1) {
            // 거부 사유 진단을 위해 응답 본문 앞부분을 함께 남김
            val sample = json.take(500).replace('\n', ' ').replace('\r', ' ')
            logger.warn("page_html field not found in response (len=${json.length}): $sample")
            return null
        }

        val contentStart = startIndex + startMarker.length
        val sb = StringBuilder()
        var i = contentStart
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                // escape sequence 처리
                val next = json[i + 1]
                when (next) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'u' -> {
                        // unicode escape: \uXXXX
                        if (i + 5 < json.length) {
                            val hex = json.substring(i + 2, i + 6)
                            try {
                                sb.append(hex.toInt(16).toChar())
                                i += 5
                                continue
                            } catch (_: Exception) {
                                sb.append("\\u")
                            }
                        }
                    }
                    else -> {
                        sb.append('\\')
                        sb.append(next)
                    }
                }
                i += 2
            } else if (c == '"') {
                // 문자열 끝
                break
            } else {
                sb.append(c)
                i++
            }
        }

        val html = sb.toString()
        logger.debug("Extracted HTML length: ${html.length}")
        return if (html.isNotBlank()) html else null
    }

    /**
     * HTML에서 상품수 추출
     */
    private fun extractProductCount(html: String): Long? {
        val doc = Jsoup.parse(html)

        // 1. 셀렉터로 먼저 시도 (더 정확함)
        // 네이버 쇼핑 검색 결과의 "전체 X개" 부분
        val selectors = listOf(
            "[class*='subFilter_num']",   // 전체 상품수 표시 영역 (가장 정확)
            "span.subFilter_num__S9sle",
            "[class*='productCount']",
            "[class*='totalCount']"
        )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val text = element.text()
                logger.debug("Selector '$selector' found: $text")
                val numberStr = text.replace(Regex("[^0-9]"), "")
                val count = numberStr.toLongOrNull()
                if (count != null && count > 0) {
                    logger.debug("Extracted count from selector: $count")
                    return count
                }
            }
        }

        // 2. 텍스트에서 "전체 123,456개" 패턴 찾기
        val bodyText = doc.body().text()

        // "전체" 키워드 주변의 숫자만 추출 (더 정확한 패턴)
        val exactPattern = """전체\s*([\d,]+)\s*개""".toRegex()
        val exactMatch = exactPattern.find(bodyText)
        if (exactMatch != null) {
            val numberStr = exactMatch.groupValues[1].replace(",", "")
            val count = numberStr.toLongOrNull()
            if (count != null && count > 0) {
                logger.debug("Extracted count from '전체 X개' pattern: $count")
                return count
            }
        }

        // 3. 마지막 fallback - 첫 번째 큰 숫자+개 패턴은 위험하므로 제거
        logger.warn("Could not extract product count from HTML")
        return null
    }

    private fun buildSearchUrl(keyword: String, overseas: Boolean = false, sort: String? = null): String {
        val encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8)
        val sb = StringBuilder("https://search.shopping.naver.com/search/all?query=$encodedKeyword&score=true")
        if (overseas) sb.append("&productSet=overseas")
        if (sort != null) sb.append("&sort=").append(sort)
        return sb.toString()
    }

    /**
     * 키워드 → 상위 N개 상품 리스트 (리뷰 많은 순).
     * 정렬: sort=review.
     * 추출 항목: 상품명, 가격, 판매처, 링크, 리뷰수, 이미지URL, 상품ID (없으면 null).
     */
    fun getTopProducts(keyword: String, limit: Int = 20): TopProductsResult {
        if (token.isBlank()) {
            return TopProductsResult.Error(keyword, "BrightData token not configured")
        }
        val url = buildSearchUrl(keyword, sort = "review")
        var last: TopProductsResult = TopProductsResult.Error(keyword, "no attempts made")
        for (attempt in 1..maxAttempts) {
            last = crawlProductsOnce(keyword, url, limit, attempt)
            if (last is TopProductsResult.Success && last.products.isNotEmpty()) return last
            if (attempt < maxAttempts) {
                val delay = retryBaseDelayMs * (1L shl (attempt - 1))
                logger.warn("Retrying top products '$keyword' (attempt ${attempt + 1}/$maxAttempts) after ${delay}ms — last=$last")
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return last
                }
            }
        }
        return last
    }

    private fun crawlProductsOnce(keyword: String, url: String, limit: Int, attempt: Int): TopProductsResult {
        return try {
            logger.info("Fetching top products for '$keyword' (attempt $attempt/$maxAttempts), url: $url")
            val html = fetchPageHtml(url)
            when {
                html == null -> TopProductsResult.Error(keyword, "Failed to fetch page")
                html.contains("접속이 일시적으로 제한") || html.contains("보안 확인") -> TopProductsResult.Blocked(keyword)
                else -> {
                    val products = extractProducts(html, limit)
                    if (products.isEmpty()) {
                        logger.warn("No products extracted for '$keyword'. HTML length=${html.length}, sample=${html.take(300).replace('\n', ' ')}")
                        TopProductsResult.NotFound(keyword)
                    } else {
                        logger.info("Extracted ${products.size} products for '$keyword'")
                        TopProductsResult.Success(keyword, products)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error crawling top products for '$keyword'", e)
            TopProductsResult.Error(keyword, e.message ?: "Unknown error")
        }
    }

    /**
     * 네이버 쇼핑 검색결과 HTML에서 상품 카드 추출.
     * 마크업이 자주 바뀌므로 다양한 셀렉터를 폴백으로 시도.
     */
    private fun extractProducts(html: String, limit: Int): List<NaverProduct> {
        val doc = Jsoup.parse(html)
        // 카드 컨테이너 후보 (네이버는 CSS 모듈 해시를 쓰므로 prefix 매칭)
        val cardSelectors = listOf(
            "[class*='basicList_item']",
            "[class*='product_item']",
            "[class*='adProduct_item']",
            "li[class*='item']"
        )
        val cards = cardSelectors
            .asSequence()
            .map { sel -> doc.select(sel) }
            .firstOrNull { it.isNotEmpty() }
            ?: return emptyList()
        return cards.asSequence()
            .mapNotNull { parseProductCard(it) }
            .distinctBy { it.link ?: it.title }
            .take(limit)
            .toList()
    }

    private fun parseProductCard(card: Element): NaverProduct? {
        val title = card.selectFirst("[class*='title']")?.text()?.trim()
            ?: card.selectFirst("a[title]")?.attr("title")?.takeIf { it.isNotBlank() }
            ?: card.selectFirst("img[alt]")?.attr("alt")?.takeIf { it.isNotBlank() }

        val link = listOf(
            "a[class*='link']",
            "a[href*='smartstore.naver.com']",
            "a[href*='shopping.naver.com']",
            "a[href]"
        ).firstNotNullOfOrNull { sel ->
            card.selectFirst(sel)?.attr("href")?.takeIf { it.isNotBlank() }
        }

        val priceText = card.selectFirst("[class*='price_num']")?.text()
            ?: card.selectFirst("[class*='price'] em")?.text()
            ?: card.selectFirst("[class*='price']")?.text()
        val price = priceText?.replace(Regex("[^0-9]"), "")?.toLongOrNull()

        val mall = card.selectFirst("[class*='mall_title'], [class*='mallName'], [class*='mall']")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }

        val reviewText = card.selectFirst("[class*='etc_num_review'], [class*='review']")?.text()
        val reviewCount = reviewText?.replace(Regex("[^0-9]"), "")?.toLongOrNull()

        val imageUrl = card.selectFirst("img")?.let { img ->
            img.attr("src").ifBlank { img.attr("data-src") }
        }?.takeIf { it.isNotBlank() }

        // 상품 id: 링크에서 추출 (smartstore.naver.com/.../products/{id} or .../catalog/{id})
        val productId = link?.let { url ->
            Regex("""/products/(\d+)|/catalog/(\d+)""").find(url)
                ?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        }

        if (title == null && link == null) return null
        return NaverProduct(
            title = title,
            price = price,
            mall = mall,
            link = link,
            reviewCount = reviewCount,
            imageUrl = imageUrl,
            productId = productId
        )
    }
}

data class NaverProduct(
    val title: String?,
    val price: Long?,
    val mall: String?,
    val link: String?,
    val reviewCount: Long?,
    val imageUrl: String?,
    val productId: String?
)

sealed class TopProductsResult {
    abstract val keyword: String
    data class Success(override val keyword: String, val products: List<NaverProduct>) : TopProductsResult()
    data class NotFound(override val keyword: String) : TopProductsResult()
    data class Blocked(override val keyword: String) : TopProductsResult()
    data class Error(override val keyword: String, val message: String) : TopProductsResult()
}

data class ProductCountWithForeignResult(
    val keyword: String,
    val total: ProductCountResult,
    val foreign: ProductCountResult
)
