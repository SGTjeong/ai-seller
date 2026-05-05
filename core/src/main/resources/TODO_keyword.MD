# 네이버 데이터랩 API

## 인기검색어 조회

```
GET https://datalab.naver.com/shoppingInsight/getCategoryKeywordRank.naver
```

### Query Parameters

| 파라미터  | 값         | 설명                |
|-----------|------------|---------------------|
| cid       | 50000000   | 카테고리 ID         |
| timeUnit  | date       | 시간 단위           |
| startDate | 2026-04-05 | 시작일 (YYYY-MM-DD) |
| endDate   | 2026-05-05 | 종료일 (YYYY-MM-DD) |
| age       | (빈값)     | 연령대 (빈값=전체)  |
| gender    | (빈값)     | 성별 (빈값=전체)    |
| device    | (빈값)     | 기기 (빈값=전체)    |
| page      | 1          | 페이지 번호         |
| count     | 20         | 페이지당 개수       |

### Response

```json
{
  "statusCode": 200,
  "returnCode": 0,
  "range": "2026.04.05. ~ 2026.05.05.",
  "ranks": [
    { "rank": 1, "keyword": "원피스", "linkId": "원피스" },
    { "rank": 2, "keyword": "바람막이", "linkId": "바람막이" }
  ]
}
```

---

## 카테고리 목록 조회

```
GET https://datalab.naver.com/shoppingInsight/getCategory.naver?cid=0
```

### Response

```json
{
  "cid": 0,
  "name": "전체",
  "childList": [
    { "cid": 50000000, "name": "패션의류" },
    { "cid": 50000001, "name": "패션잡화" }
  ]
}
```

---

## 주의사항

- 브라우저 세션 쿠키 필요 (먼저 페이지 방문 필요)
- Rate limit 있음 (연속 호출 시 빈 응답)
- 카테고리당 최대 ~140개 정도만 제공
