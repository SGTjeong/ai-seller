# AI Seller

오픈마켓(네이버, 쿠팡 등)에서 발생한 주문을 자동으로 수집하고, AI 기반으로 최적의 소싱처(타오바오 등)를 찾아 결제 및 배송까지 자동화하는 드롭쉬핑/위탁판매 플랫폼입니다.

## 처리 흐름

```
오픈마켓 주문 발생 → 주문 자동 수집 → AI 소싱처 선택 → 승인 요청 → 결제 → 배송 처리
```

## 도메인 모델

### Fulfillment

시스템의 핵심 엔티티로, 하나의 마켓 주문에 대한 전체 처리 과정을 관리합니다.

| 필드 | 설명 |
|------|------|
| userId | 사용자 ID |
| order | 마켓 주문 정보 (Order) |
| status | 현재 처리 상태 (FulfillmentStatus) |
| events | 상태 변경 이력 (List\<FulfillmentEvent\>) |
| createdAt | 생성 시각 |

### FulfillmentStatus

```
STARTED                 # 주문 수집 완료
    ↓
SUPPLIER_SELECTED       # 소싱처 선택 완료
    ↓
APPROVAL_REQUESTED      # 승인 요청 완료
    ↓
APPROVED → PAYMENT_COMPLETED → SHIPMENT_PREPARED
    or
APPROVAL_REJECTED

* CANCELLED_BY_BUYER    # 구매자 취소
* CANCELLED_BY_SELLER   # 판매자 취소
```

### FulfillmentEvent

주문 처리 과정의 모든 상태 변경을 이벤트로 기록합니다.

| 이벤트 | 설명 |
|--------|------|
| Started | 주문 수집됨 |
| SupplierSelected | 소싱처 선택됨 |
| ApprovalRequested | 승인 요청됨 |
| Approved | 승인됨 |
| ApprovalRejected | 승인 거절됨 |
| PaymentCompleted | 결제 완료됨 |
| PaymentFailed | 결제 실패 |
| ShipmentPrepared | 배송 준비됨 |

### Order

마켓플레이스에서 수집한 주문 정보입니다.

| 필드 | 설명 |
|------|------|
| market | 마켓 구분 (NAVER, COUPANG) |
| orderId | 주문 ID |
| orderedAt | 주문 시각 |
| buyer | 구매자 정보 (이름, 연락처, 주소) |
| product | 상품 정보 (상품명, 옵션, 수량, 이미지) |
| payment | 결제 정보 (결제금액) |

### SupplySource

AI가 선택한 소싱처 상품 정보입니다.

| 필드 | 설명 |
|------|------|
| supplier | 소싱처 구분 (TAOBAO) |
| product | 소싱 상품 정보 (상품명, 옵션, URL, 단가) |

### User

사용자 및 마켓플레이스 API 인증정보를 관리합니다.

| 필드 | 설명 |
|------|------|
| id | 사용자 ID |
| credentials | 마켓 인증정보 목록 (Naver: clientId, clientSecret) |
