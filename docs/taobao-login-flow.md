# 타오바오 SMS 로그인 + 원격 캡차 처리

## 개요

타오바오 자동 로그인 시 슬라이더 캡차가 나오면, 모바일에서 원격으로 수동 처리하는 시스템.

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                     서버 (Mac/Linux)                        │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   Express   │  │  Socket.IO  │  │  Playwright Browser │ │
│  │   :3001     │  │  실시간통신  │  │  (타오바오 페이지)   │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
│         │                │                     │            │
│         └────────────────┼─────────────────────┘            │
│                          │                                  │
└──────────────────────────┼──────────────────────────────────┘
                           │
                           │ Cloudflare Tunnel (안정적)
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    모바일 브라우저                          │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  https://xxx.trycloudflare.com/remote                 │ │
│  │                                                        │ │
│  │  - 브라우저 화면 실시간 스트리밍 (10 FPS)             │ │
│  │  - 터치/드래그 이벤트 → 서버로 전송                   │ │
│  │  - 슬라이더 캡차 수동 처리 가능                       │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 사용법

### 1. 서버 실행

```bash
cd tool
npm run dev
```

### 2. Cloudflare 터널 열기

```bash
cloudflared tunnel --url http://localhost:3001
```

출력에서 URL 확인:
```
https://xxx-xxx-xxx.trycloudflare.com
```

### 3. 타오바오 SMS 로그인 (캡차까지 한번에)

```bash
curl -X POST http://localhost:3001/api/taobao-sms-login \
  -H "Content-Type: application/json" \
  -d '{"phone": "01031138091"}'
```

### 4. 모바일에서 캡차 처리

```
https://xxx-xxx-xxx.trycloudflare.com/remote
```

슬라이더를 드래그하여 캡차 해결.

---

## 내부 동작 순서 (api/taobao-sms-login)

```
1. 브라우저 열기 (headless: false)
2. 로그인 페이지 이동
   → https://login.taobao.com/member/login.jhtml
3. "短信登录" 탭 클릭
4. 전화번호 입력
5. "获取验证码" 버튼 클릭
6. 동의 다이얼로그 → 버튼 index 2 클릭 (오른쪽 버튼)
7. 캡차 등장 → 원격에서 슬라이더 드래그
```

---

## API 목록

| Endpoint | Method | Body | 설명 |
|----------|--------|------|------|
| `/api/taobao-sms-login` | POST | `{phone}` | SMS 로그인 → 캡차까지 한번에 |
| `/api/open-taobao` | POST | - | 타오바오 메인 페이지 열기 |
| `/api/click` | POST | `{text}` | 텍스트로 요소 클릭 |
| `/api/type` | POST | `{selector, text}` | 입력 필드에 텍스트 입력 |
| `/api/click-button` | POST | `{index}` | 버튼 인덱스로 클릭 |
| `/remote` | GET | - | 원격 제어 UI |

---

## 파일 구조

```
tool/
├── src/
│   ├── index.ts              # Express 서버 + API
│   ├── services/
│   │   ├── browser.ts        # Playwright 브라우저 관리
│   │   └── remote-control.ts # Socket.IO 원격 제어
│   └── public/
│       └── remote.html       # 원격 제어 UI (모바일)
├── package.json
└── tsconfig.json
```

---

## 터널링 비교

| 서비스 | 안정성 | 속도 | 설치 |
|--------|--------|------|------|
| localtunnel | 낮음 (자주 끊김) | 느림 | `npx localtunnel --port 3001` |
| **cloudflared** | **높음 (권장)** | **빠름** | `brew install cloudflared` |
| ngrok | 높음 | 빠름 | 계정 등록 필요 |

---

## 알려진 이슈

### 캡차가 사람이 해도 실패하는 경우

Playwright 브라우저가 자동화 도구로 감지될 수 있음.

가능한 해결책:
1. `playwright-extra` + `stealth` 플러그인 사용
2. 실제 브라우저 프로필 사용
3. User-Agent, WebGL fingerprint 등 위장

```typescript
// stealth 플러그인 예시 (추후 적용)
import { chromium } from 'playwright-extra';
import stealth from 'puppeteer-extra-plugin-stealth';

chromium.use(stealth());
```

---

## 다음 단계

1. [ ] stealth 플러그인 적용하여 봇 감지 우회
2. [ ] 캡차 통과 후 인증코드 입력 → 로그인 완료
3. [ ] 로그인 세션 유지 (쿠키 저장)
4. [ ] 상품 검색 및 결제 자동화
