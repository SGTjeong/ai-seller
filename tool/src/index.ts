import express, { Request, Response } from 'express';
import { closeBrowser } from './services/browser.js';
import type { SelectRequest, SelectResponse } from './types/supplySource.js';

const app = express();
const PORT = process.env.PORT || 3001;

app.use(express.json());

// Health check
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
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

app.listen(PORT, () => {
  console.log(`Tool server running on http://localhost:${PORT}`);
  console.log('Available endpoints:');
  console.log('  GET  /health');
  console.log('  POST /api/select-supply-source');
});
