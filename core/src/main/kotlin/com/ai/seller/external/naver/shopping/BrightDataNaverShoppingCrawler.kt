package com.ai.seller.external.naver.shopping

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
 */
@Component
class BrightDataNaverShoppingCrawler(
    @Value("\${brightdata.token:}") private val token: String = "",
    @Value("\${brightdata.dataset-id:gd_m6gjtfmeh43we6cqc}") private val datasetId: String = "gd_m6gjtfmeh43we6cqc",
    @Value("\${brightdata.timeout-seconds:120}") private val timeoutSeconds: Int = 120
) {
    private val logger = LoggerFactory.getLogger(BrightDataNaverShoppingCrawler::class.java)

    private val restClient = RestClient.builder()
        .baseUrl("https://api.brightdata.com")
        .build()

    /**
     * 키워드로 검색하여 전체 상품수 조회
     */
    fun getProductCount(keyword: String): ProductCountResult {
        if (token.isBlank()) {
            return ProductCountResult.Error(keyword, "BrightData token not configured")
        }

        return try {
            val searchUrl = buildSearchUrl(keyword)
            logger.info("Fetching product count for keyword: $keyword, url: $searchUrl")

            val html = fetchPageHtml(searchUrl)
            if (html == null) {
                logger.warn("Failed to fetch page HTML for keyword: $keyword")
                return ProductCountResult.Error(keyword, "Failed to fetch page")
            }

            // 차단 감지
            if (html.contains("접속이 일시적으로 제한") || html.contains("보안 확인")) {
                logger.warn("Access blocked for keyword: $keyword")
                return ProductCountResult.Blocked(keyword)
            }

            val count = extractProductCount(html)
            if (count != null) {
                logger.info("Product count for '$keyword': $count")
                ProductCountResult.Success(keyword, count)
            } else {
                logger.warn("Product count not found for keyword: $keyword")
                ProductCountResult.NotFound(keyword)
            }
        } catch (e: Exception) {
            logger.error("Error crawling keyword: $keyword", e)
            ProductCountResult.Error(keyword, e.message ?: "Unknown error")
        }
    }

    /**
     * 여러 키워드의 전체 상품수 조회
     */
    fun getProductCounts(keywords: List<String>): Map<String, ProductCountResult> {
        return keywords.associateWith { getProductCount(it) }
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

            // 응답 형식: [{"page_html": "...", ...}]
            // page_html 필드 추출 (JSON 파서 대신 문자열 처리)
            extractPageHtmlFromJson(response)
        } catch (e: Exception) {
            logger.error("BrightData API call failed", e)
            null
        }
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
            logger.warn("page_html field not found in response")
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

    /**
     * 전체 + 해외직구 상품수 동시 조회 (두 번의 API 호출)
     * 해외직구 상품수는 productSet=overseas 파라미터를 사용해야 정확히 가져올 수 있음
     */
    fun getProductCountWithForeign(keyword: String): ProductCountWithForeignResult {
        if (token.isBlank()) {
            val error = ProductCountResult.Error(keyword, "BrightData token not configured")
            return ProductCountWithForeignResult(keyword, error, error)
        }

        // 1. 전체 상품수 조회
        val totalResult = try {
            val searchUrl = buildSearchUrl(keyword)
            logger.info("Fetching total product count for keyword: $keyword, url: $searchUrl")

            val html = fetchPageHtml(searchUrl)
            if (html == null) {
                logger.warn("Failed to fetch page HTML for keyword: $keyword")
                ProductCountResult.Error(keyword, "Failed to fetch page")
            } else if (html.contains("접속이 일시적으로 제한") || html.contains("보안 확인")) {
                logger.warn("Access blocked for keyword: $keyword")
                ProductCountResult.Blocked(keyword)
            } else {
                val totalCount = extractProductCount(html)
                if (totalCount != null) {
                    logger.info("Total product count for '$keyword': $totalCount")
                    ProductCountResult.Success(keyword, totalCount)
                } else {
                    ProductCountResult.NotFound(keyword)
                }
            }
        } catch (e: Exception) {
            logger.error("Error crawling total count for keyword: $keyword", e)
            ProductCountResult.Error(keyword, e.message ?: "Unknown error")
        }

        // 2. 해외직구 상품수 조회 (productSet=overseas 파라미터 사용)
        val foreignResult = try {
            val foreignUrl = buildSearchUrl(keyword, overseas = true)
            logger.info("Fetching foreign product count for keyword: $keyword, url: $foreignUrl")

            val html = fetchPageHtml(foreignUrl)
            if (html == null) {
                logger.warn("Failed to fetch foreign page HTML for keyword: $keyword")
                ProductCountResult.Error(keyword, "Failed to fetch page")
            } else if (html.contains("접속이 일시적으로 제한") || html.contains("보안 확인")) {
                logger.warn("Access blocked for foreign keyword: $keyword")
                ProductCountResult.Blocked(keyword)
            } else {
                val foreignCount = extractProductCount(html)
                if (foreignCount != null) {
                    logger.info("Foreign product count for '$keyword': $foreignCount")
                    ProductCountResult.Success(keyword, foreignCount)
                } else {
                    ProductCountResult.NotFound(keyword)
                }
            }
        } catch (e: Exception) {
            logger.error("Error crawling foreign count for keyword: $keyword", e)
            ProductCountResult.Error(keyword, e.message ?: "Unknown error")
        }

        return ProductCountWithForeignResult(keyword, totalResult, foreignResult)
    }

    private fun buildSearchUrl(keyword: String, overseas: Boolean = false): String {
        val encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8)
        val baseUrl = "https://search.shopping.naver.com/search/all?query=$encodedKeyword&score=true"
        return if (overseas) {
            "$baseUrl&productSet=overseas"
        } else {
            baseUrl
        }
    }
}

data class ProductCountWithForeignResult(
    val keyword: String,
    val total: ProductCountResult,
    val foreign: ProductCountResult
)
