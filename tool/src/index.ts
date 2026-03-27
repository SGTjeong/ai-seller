import express, { Request, Response } from 'express';
import { createServer } from 'http';
import path from 'path';
import { Page } from 'playwright';
import { closeBrowser, createPage } from './services/browser.js';
import { RemoteControl } from './services/remote-control.js';
import type { SelectRequest, SelectResponse } from './types/supplySource.js';

// public 폴더 경로 (dist/public 또는 src/public)
const publicPath = path.join(process.cwd(), 'src', 'public');

const app = express();
const httpServer = createServer(app);
const PORT = process.env.PORT || 3001;

// 현재 활성 페이지
let currentPage: Page | null = null;

// Socket.IO 원격 제어 초기화
const remoteControl = new RemoteControl(httpServer);

app.use(express.json());

// Static files for remote control UI
app.use(express.static(publicPath));

// Health check
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Remote control UI
app.get('/remote', (_req, res) => {
  res.sendFile(path.join(publicPath, 'remote.html'));
});

// 테스트용: 타오바오 페이지 열기
app.post('/api/open-taobao', async (_req: Request, res: Response) => {
  try {
    const { page } = await createPage();
    await page.goto('https://www.taobao.com');

    // 페이지 저장
    currentPage = page;

    // 원격 제어에 페이지 연결
    remoteControl.setPage(page);
    remoteControl.notify('Taobao page opened', 'info');

    res.json({
      success: true,
      message: 'Taobao opened. Go to /remote to control the browser.',
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      error: String(error),
    });
  }
});

// 타오바오 SMS 로그인 → 캡차까지 한번에
app.post('/api/taobao-sms-login', async (req: Request, res: Response) => {
  const { phone } = req.body;

  try {
    // 1. 브라우저 열기
    const { page } = await createPage();
    currentPage = page;
    remoteControl.setPage(page);

    // 2. 로그인 페이지로 이동
    await page.goto('https://login.taobao.com/member/login.jhtml');
    await page.waitForTimeout(3000);

    // 3. SMS 로그인 탭 클릭
    await page.locator('text=短信登录').first().click();
    await page.waitForTimeout(2000);

    // 4. 전화번호 입력
    await page.locator('input').first().fill(phone);
    await page.waitForTimeout(1000);

    // 5. 검증번호 획득 클릭
    await page.locator('text=获取验证码').first().click();
    await page.waitForTimeout(2000);

    // 6. 동의 다이얼로그 - 오른쪽 버튼 (index 2) 클릭
    const buttons = page.locator('button');
    await buttons.nth(2).click();

    remoteControl.notify('캡차 등장! 슬라이더 드래그하세요', 'info');

    res.json({
      success: true,
      message: 'SMS 로그인 완료. 캡차 나왔으면 /remote에서 슬라이더 드래그하세요.',
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      error: String(error),
    });
  }
});

// 타오바오 전화번호 로그인
app.post('/api/taobao-login', async (req: Request, res: Response) => {
  const { phone } = req.body;

  if (!currentPage) {
    return res.status(400).json({ success: false, error: 'No page open. Call /api/open-taobao first.' });
  }

  try {
    // 로그인 페이지로 이동
    await currentPage.goto('https://login.taobao.com/member/login.jhtml');
    await currentPage.waitForTimeout(3000);

    // 스크린샷으로 현재 상태 확인
    console.log('Current URL:', currentPage.url());

    // 모든 input 필드 찾기
    const inputs = await currentPage.locator('input').all();
    console.log('Found inputs:', inputs.length);

    // 전화번호 입력 필드 찾기 (다양한 셀렉터 시도)
    const phoneInput = currentPage.locator('input[type="tel"], input[name="mobile"], input[id*="mobile"], input[id*="phone"], input[placeholder*="手机"]').first();

    if (await phoneInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await phoneInput.click();
      await phoneInput.fill(phone);
      console.log('Phone entered:', phone);
      await currentPage.waitForTimeout(1000);

      // 인증코드 버튼 찾기
      const sendBtn = currentPage.locator('button').filter({ hasText: /验证码|发送|获取/ }).first();
      if (await sendBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await sendBtn.click();
        remoteControl.notify('인증코드 요청 중...', 'info');
      }
    } else {
      // 입력 필드 못 찾으면 원격 제어로 안내
      remoteControl.notify('로그인 페이지 로드됨 - 원격에서 확인하세요', 'warning');
    }

    res.json({
      success: true,
      message: 'Login page loaded. Check /remote',
      url: currentPage.url(),
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      error: String(error),
    });
  }
});

// 다이얼로그(alert) 수락
app.post('/api/accept-dialog', async (_req: Request, res: Response) => {
  if (!currentPage) {
    return res.status(400).json({ success: false, error: 'No page open' });
  }

  try {
    // 다이얼로그 핸들러 등록
    currentPage.once('dialog', async (dialog) => {
      await dialog.accept();
    });

    // 다이얼로그 내의 버튼 클릭 시도
    const dialogBtn = currentPage.locator('button:has-text("同意"), button:has-text("确定"), button:has-text("确认"), .dialog-button, [class*="dialog"] button').first();
    if (await dialogBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await dialogBtn.click();
    }

    res.json({ success: true, message: 'Dialog accepted' });
  } catch (error) {
    res.status(500).json({ success: false, error: String(error) });
  }
});

// 버튼 인덱스로 클릭 (0부터 시작)
app.post('/api/click-button', async (req: Request, res: Response) => {
  const { index } = req.body;

  if (!currentPage) {
    return res.status(400).json({ success: false, error: 'No page open' });
  }

  try {
    const buttons = currentPage.locator('button');
    const count = await buttons.count();
    await buttons.nth(index).click();
    res.json({ success: true, message: `Clicked button ${index} of ${count}` });
  } catch (error) {
    res.status(500).json({ success: false, error: String(error) });
  }
});

// 페이지에서 텍스트로 요소 클릭
app.post('/api/click', async (req: Request, res: Response) => {
  const { text } = req.body;

  if (!currentPage) {
    return res.status(400).json({ success: false, error: 'No page open' });
  }

  try {
    await currentPage.locator(`text=${text}`).first().click();
    res.json({ success: true, message: `Clicked: ${text}` });
  } catch (error) {
    res.status(500).json({ success: false, error: String(error) });
  }
});

// 입력 필드에 텍스트 입력
app.post('/api/type', async (req: Request, res: Response) => {
  const { selector, text } = req.body;

  if (!currentPage) {
    return res.status(400).json({ success: false, error: 'No page open' });
  }

  try {
    await currentPage.locator(selector).first().fill(text);
    res.json({ success: true, message: `Typed: ${text}` });
  } catch (error) {
    res.status(500).json({ success: false, error: String(error) });
  }
});

/**
 * POST /api/select-supply-source
 *
 * 마켓에서 주문된 상품 정보를 받아, 마진이 가장 높은 소싱처 상품을 찾아 반환합니다.
 */
app.post('/api/select-supply-source', async (req: Request, res: Response) => {
  const { productName, productOption, imageUrls, quantity } = req.body as SelectRequest;

  // TODO: 구현 필요
  // 1. productName 혹은 imageUrls를 활용해 타오바오에서 동일/유사 상품 검색
  // 2. 검색 결과 중 마진이 가장 높은 상품 선택
  // 3. productOption이 있는 경우 해당 옵션과 매칭되는 소싱 옵션 선택
  // 4. unitPrice는 원화(KRW)로 환산하여 반환

  const response: SelectResponse = {
    success: false,
    error: 'Not implemented',
  };

  res.status(501).json(response);
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('\nShutting down...');
  await closeBrowser();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  console.log('\nShutting down...');
  await closeBrowser();
  process.exit(0);
});

httpServer.listen(PORT, () => {
  console.log(`Tool server running on http://localhost:${PORT}`);
  console.log('');
  console.log('Available endpoints:');
  console.log('  GET  /health              - Health check');
  console.log('  GET  /remote              - Remote browser control UI');
  console.log('  POST /api/open-taobao     - Open Taobao and enable remote control');
  console.log('  POST /api/select-supply-source');
});
