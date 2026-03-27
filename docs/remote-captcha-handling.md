# 원격 캡차 처리 방법

타오바오 슬라이더 캡차를 원격에서 수동으로 처리하는 3가지 방법을 정리합니다.

---

## 방법 비교

| 방식 | 원격 접속 | 모바일 | 구현 난이도 | 지연 |
|------|----------|--------|------------|------|
| CDP 디버깅 | ✅ (ngrok) | ❌ | 쉬움 | 거의 없음 |
| page.pause() | ❌ 로컬만 | ❌ | 매우 쉬움 | 없음 |
| 웹 스트리밍 | ✅ | ✅ | 중간 | 약간 있음 |

---

## 1. Playwright CDP 원격 디버깅

### 동작 원리

```
┌─────────────────────────────────────────────────────────────┐
│                        서버 (tool)                          │
│  ┌──────────────┐      ┌──────────────────────────────┐    │
│  │   Express    │      │   Chromium Browser           │    │
│  │   Server     │      │   (headless: false)          │    │
│  │   :3001      │      │                              │    │
│  └──────────────┘      │   WebSocket Server :9222     │    │
│                        └───────────┬──────────────────┘    │
└────────────────────────────────────┼────────────────────────┘
                                     │ WebSocket (CDP 프로토콜)
                                     │
                    ┌────────────────┼────────────────┐
                    │              ngrok              │
                    │    로컬 :9222 → 공개 URL 터널링 │
                    └────────────────┼────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │       원격 컴퓨터/폰            │
                    │                                 │
                    │   Chrome DevTools 또는          │
                    │   chrome://inspect 에서 접속    │
                    │                                 │
                    │   → 브라우저 화면 실시간 보임   │
                    │   → 마우스/키보드 직접 조작     │
                    └─────────────────────────────────┘
```

### 코드 예시

```typescript
import { chromium } from 'playwright';

// 1. 브라우저를 WebSocket 서버 모드로 실행
const browserServer = await chromium.launchServer({
  headless: false,
  args: ['--remote-debugging-port=9222'],
});

console.log('WebSocket Endpoint:', browserServer.wsEndpoint());
// 출력: ws://127.0.0.1:9222/devtools/browser/xxxx-xxxx

// 2. 다른 곳에서 이 브라우저에 연결
const browser = await chromium.connect(browserServer.wsEndpoint());
const page = await browser.newPage();
await page.goto('https://taobao.com');

// 3. 캡차 감지 시 대기
if (await page.locator('.nc_wrapper').isVisible()) {
  console.log('캡차 나타남! 원격에서 처리해주세요...');
  await page.waitForSelector('.nc_wrapper', { state: 'hidden', timeout: 300000 });
  console.log('캡차 통과!');
}
```

### 원격 접속 방법

```bash
# 터미널 1: ngrok으로 터널 열기
ngrok tcp 9222

# 출력: tcp://0.tcp.ngrok.io:12345 -> localhost:9222
```

원격 Chrome에서:
1. `chrome://inspect` 접속
2. "Configure..." 클릭 → `0.tcp.ngrok.io:12345` 추가
3. 연결된 브라우저 보이면 "inspect" 클릭
4. 브라우저 화면이 그대로 보이고 직접 조작 가능

### 장단점

- ✅ 설정 간단
- ✅ Chrome 내장 기능 활용
- ✅ 모든 브라우저 기능 사용 가능
- ❌ Chrome 브라우저 필요
- ❌ 모바일에서 불편

---

## 2. page.pause() + Playwright Inspector

### 동작 원리

```
┌─────────────────────────────────────────────────────────────┐
│                     로컬 개발 환경                          │
│                                                             │
│   ┌─────────────┐     page.pause()     ┌────────────────┐  │
│   │             │ ──────────────────▶  │   Playwright   │  │
│   │   자동화    │                      │   Inspector    │  │
│   │   스크립트  │                      │   (별도 창)    │  │
│   │             │ ◀──────────────────  │                │  │
│   └─────────────┘     Resume 클릭      │  ┌──────────┐  │  │
│                                        │  │ 브라우저 │  │  │
│                                        │  │ 화면     │  │  │
│                                        │  │          │  │  │
│                                        │  │ 직접     │  │  │
│                                        │  │ 조작!    │  │  │
│                                        │  └──────────┘  │  │
│                                        └────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 코드 예시

```typescript
import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: false });
const page = await browser.newPage();

await page.goto('https://taobao.com');
await page.fill('#q', '검색어');
await page.click('.btn-search');

// 캡차 감지
const captcha = page.locator('.nc_wrapper, #nc_1_wrapper');
if (await captcha.isVisible({ timeout: 3000 }).catch(() => false)) {
  console.log('========================================');
  console.log('🚨 캡차 감지! Inspector에서 슬라이더 드래그 후');
  console.log('   상단의 "Resume" 버튼을 클릭하세요');
  console.log('========================================');

  await page.pause();  // ← 여기서 멈춤, Inspector UI 열림

  console.log('캡차 처리 완료, 계속 진행...');
}
```

### 실행 방법

```bash
# 반드시 PWDEBUG=1 환경변수와 함께 실행
PWDEBUG=1 npx tsx src/index.ts
```

### Inspector UI 구성

```
┌─────────────────────────────────────────────────────┐
│ Playwright Inspector                           [X] │
├─────────────────────────────────────────────────────┤
│ [▶ Resume] [Step Over] [Record]                    │
├─────────────────────────────────────────────────────┤
│   ┌─────────────────────────────────────────────┐  │
│   │                                             │  │
│   │     타오바오 페이지가 여기 보임             │  │
│   │                                             │  │
│   │     [═══════●────────────────]              │  │
│   │        ↑ 슬라이더를 마우스로 직접 드래그    │  │
│   │                                             │  │
│   └─────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 장단점

- ✅ 가장 간단
- ✅ 별도 설정 불필요
- ✅ Playwright 내장 기능
- ❌ **로컬에서만 가능**
- ❌ 원격 접속 불가

---

## 3. 웹 기반 실시간 제어 (커스텀 구현)

### 동작 원리

```
┌────────────────────────────────────────────────────────────────┐
│                         서버 (tool)                            │
│                                                                │
│  ┌──────────────┐    ┌─────────────────┐    ┌──────────────┐  │
│  │   Express    │    │    Playwright   │    │   Chromium   │  │
│  │   + Socket.io│    │    Controller   │    │   Browser    │  │
│  │   :3001      │    │                 │    │              │  │
│  └──────┬───────┘    └────────┬────────┘    └──────┬───────┘  │
│         │                     │                     │          │
│         │ ◀─── 스크린샷 ─────┤◀── screenshot() ───┤          │
│         │      (초당 10회)    │                     │          │
│         │                     │                     │          │
│         │ ──── 마우스 ───────▶│── mouse.move() ───▶│          │
│         │      좌표 전송      │   mouse.down()      │          │
│         │                     │   mouse.up()        │          │
└─────────┼─────────────────────┼─────────────────────┼──────────┘
          │ WebSocket           │                     │
          ▼                     │                     │
┌─────────────────────────────────────────────────────────────────┐
│                    원격 웹 브라우저 (PC/모바일)                 │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │                        Canvas                              │ │
│  │  ┌─────────────────────────────────────────────────────┐  │ │
│  │  │                                                     │  │ │
│  │  │      서버 브라우저 화면이 실시간으로 보임           │  │ │
│  │  │                                                     │  │ │
│  │  │      [═══════●────────────────]                     │  │ │
│  │  │              ↑                                      │  │ │
│  │  │      드래그하면 서버로 좌표 전송                    │  │ │
│  │  │      → Playwright가 실제로 드래그 실행             │  │ │
│  │  │                                                     │  │ │
│  │  └─────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 데이터 흐름

```
[스크린샷 스트리밍: 서버 → 클라이언트]

1. 서버: page.screenshot({ type: 'jpeg', quality: 50 })
2. 서버: Base64 인코딩
3. 서버: socket.emit('screenshot', base64Data)
4. 클라이언트: Canvas에 이미지 렌더링
5. 반복 (100ms 간격 = 10 FPS)

[마우스 조작: 클라이언트 → 서버]

1. 클라이언트: Canvas에서 mousedown
2. 클라이언트: socket.emit('mouse', { type: 'down', x, y })
3. 서버: page.mouse.move(x, y) → page.mouse.down()

4. 클라이언트: mousemove (드래그 중)
5. 클라이언트: socket.emit('mouse', { type: 'move', x, y })
6. 서버: page.mouse.move(x, y)

7. 클라이언트: mouseup
8. 클라이언트: socket.emit('mouse', { type: 'up', x, y })
9. 서버: page.mouse.up()
```

### 서버 코드 (server.ts)

```typescript
import express from 'express';
import { createServer } from 'http';
import { Server } from 'socket.io';
import { chromium, Page } from 'playwright';

const app = express();
const httpServer = createServer(app);
const io = new Server(httpServer, { cors: { origin: '*' } });

let page: Page;

async function startBrowser() {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 720 }
  });
  page = await context.newPage();
  await page.goto('https://taobao.com');
}

// 스크린샷 스트리밍
async function streamScreenshots(socket: any) {
  const sendFrame = async () => {
    if (!page) return;
    try {
      const screenshot = await page.screenshot({
        type: 'jpeg',
        quality: 60,
        timeout: 5000
      });
      socket.emit('frame', screenshot.toString('base64'));
    } catch (e) {}
  };

  const interval = setInterval(sendFrame, 100); // 10 FPS
  socket.on('disconnect', () => clearInterval(interval));
}

io.on('connection', (socket) => {
  console.log('원격 클라이언트 연결됨');
  streamScreenshots(socket);

  // 마우스 이벤트 처리
  socket.on('mouse', async (data: { type: string; x: number; y: number }) => {
    if (!page) return;

    switch (data.type) {
      case 'move':
        await page.mouse.move(data.x, data.y);
        break;
      case 'down':
        await page.mouse.move(data.x, data.y);
        await page.mouse.down();
        break;
      case 'up':
        await page.mouse.up();
        break;
      case 'click':
        await page.mouse.click(data.x, data.y);
        break;
    }
  });

  // 키보드 이벤트
  socket.on('key', async (data: { key: string }) => {
    await page.keyboard.press(data.key);
  });
});

// 원격 제어 UI
app.get('/', (req, res) => {
  res.sendFile(__dirname + '/remote-control.html');
});

startBrowser().then(() => {
  httpServer.listen(3001, () => {
    console.log('원격 제어 서버: http://localhost:3001');
  });
});
```

### 클라이언트 코드 (remote-control.html)

```html
<!DOCTYPE html>
<html>
<head>
  <title>원격 브라우저 제어</title>
  <script src="/socket.io/socket.io.js"></script>
  <style>
    body { margin: 0; background: #1a1a1a; }
    canvas { display: block; margin: 20px auto; border: 2px solid #333; }
    .status { color: #0f0; text-align: center; font-family: monospace; }
  </style>
</head>
<body>
  <p class="status">🟢 연결됨 - 아래 화면에서 직접 조작하세요</p>
  <canvas id="screen" width="1280" height="720"></canvas>

  <script>
    const socket = io();
    const canvas = document.getElementById('screen');
    const ctx = canvas.getContext('2d');

    // 스크린샷 수신 및 렌더링
    socket.on('frame', (base64) => {
      const img = new Image();
      img.onload = () => ctx.drawImage(img, 0, 0);
      img.src = 'data:image/jpeg;base64,' + base64;
    });

    // 마우스 이벤트 전송
    let isDragging = false;

    canvas.addEventListener('mousedown', (e) => {
      isDragging = true;
      const rect = canvas.getBoundingClientRect();
      socket.emit('mouse', {
        type: 'down',
        x: e.clientX - rect.left,
        y: e.clientY - rect.top
      });
    });

    canvas.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      const rect = canvas.getBoundingClientRect();
      socket.emit('mouse', {
        type: 'move',
        x: e.clientX - rect.left,
        y: e.clientY - rect.top
      });
    });

    canvas.addEventListener('mouseup', (e) => {
      isDragging = false;
      const rect = canvas.getBoundingClientRect();
      socket.emit('mouse', {
        type: 'up',
        x: e.clientX - rect.left,
        y: e.clientY - rect.top
      });
    });
  </script>
</body>
</html>
```

### 외부 접속 설정

```bash
# ngrok으로 외부 공개
ngrok http 3001

# 출력: https://xxxx.ngrok.io
# → 이 URL로 어디서든 접속 가능 (모바일 포함)
```

### 장단점

- ✅ 어디서든 웹브라우저로 접속 가능
- ✅ 모바일도 OK
- ✅ 완전한 커스터마이징 가능
- ❌ 직접 구현 필요
- ❌ 약간의 지연(latency) 존재

---

## 추천

| 상황 | 추천 방식 |
|------|----------|
| 로컬에서 개발/테스트 중 가끔 캡차 처리 | `page.pause()` |
| 서버에서 돌리고 PC로 원격 접속 | CDP + ngrok |
| 외출 중 모바일로 처리해야 함 | 웹 스트리밍 |
