import { Server as SocketIOServer, Socket } from 'socket.io';
import { Server as HTTPServer } from 'http';
import { Page } from 'playwright';

interface MouseEvent {
  type: 'move' | 'down' | 'up' | 'click';
  x: number;
  y: number;
}

interface KeyEvent {
  key: string;
}

export class RemoteControl {
  private io: SocketIOServer;
  private page: Page | null = null;
  private streamingInterval: NodeJS.Timeout | null = null;
  private connectedSockets: Set<Socket> = new Set();

  constructor(httpServer: HTTPServer) {
    this.io = new SocketIOServer(httpServer, {
      cors: { origin: '*' },
    });

    this.setupSocketHandlers();
  }

  private setupSocketHandlers() {
    this.io.on('connection', (socket: Socket) => {
      console.log(`[RemoteControl] Client connected: ${socket.id}`);
      this.connectedSockets.add(socket);

      // 페이지가 있으면 스트리밍 시작
      if (this.page) {
        this.startStreamingToSocket(socket);
      }

      // 마우스 이벤트 처리
      socket.on('mouse', async (data: MouseEvent) => {
        await this.handleMouseEvent(data);
      });

      // 키보드 이벤트 처리
      socket.on('key', async (data: KeyEvent) => {
        await this.handleKeyEvent(data);
      });

      // 스크롤 이벤트 처리
      socket.on('scroll', async (data: { deltaX: number; deltaY: number }) => {
        await this.handleScrollEvent(data);
      });

      socket.on('disconnect', () => {
        console.log(`[RemoteControl] Client disconnected: ${socket.id}`);
        this.connectedSockets.delete(socket);
      });
    });
  }

  /**
   * 제어할 페이지 설정 및 스트리밍 시작
   */
  setPage(page: Page) {
    this.page = page;
    console.log('[RemoteControl] Page set, starting stream to all clients');

    // 기존 연결된 클라이언트들에게 스트리밍 시작
    this.connectedSockets.forEach((socket) => {
      this.startStreamingToSocket(socket);
    });
  }

  /**
   * 페이지 연결 해제
   */
  clearPage() {
    this.page = null;
    if (this.streamingInterval) {
      clearInterval(this.streamingInterval);
      this.streamingInterval = null;
    }
  }

  /**
   * 특정 소켓에 스크린샷 스트리밍 시작
   */
  private startStreamingToSocket(socket: Socket) {
    const sendFrame = async () => {
      if (!this.page) return;

      try {
        const screenshot = await this.page.screenshot({
          type: 'jpeg',
          quality: 60,
          timeout: 5000,
        });
        socket.emit('frame', screenshot.toString('base64'));
      } catch (e) {
        // 페이지가 닫혔거나 에러 발생 시 무시
      }
    };

    // 10 FPS로 스트리밍
    const interval = setInterval(sendFrame, 100);

    socket.on('disconnect', () => {
      clearInterval(interval);
    });

    // 첫 프레임 즉시 전송
    sendFrame();
  }

  /**
   * 마우스 이벤트 처리
   */
  private async handleMouseEvent(data: MouseEvent) {
    if (!this.page) return;

    try {
      switch (data.type) {
        case 'move':
          await this.page.mouse.move(data.x, data.y);
          break;
        case 'down':
          await this.page.mouse.move(data.x, data.y);
          await this.page.mouse.down();
          break;
        case 'up':
          await this.page.mouse.up();
          break;
        case 'click':
          await this.page.mouse.click(data.x, data.y);
          break;
      }
    } catch (e) {
      console.error('[RemoteControl] Mouse event error:', e);
    }
  }

  /**
   * 키보드 이벤트 처리
   */
  private async handleKeyEvent(data: KeyEvent) {
    if (!this.page) return;

    try {
      await this.page.keyboard.press(data.key);
    } catch (e) {
      console.error('[RemoteControl] Key event error:', e);
    }
  }

  /**
   * 스크롤 이벤트 처리
   */
  private async handleScrollEvent(data: { deltaX: number; deltaY: number }) {
    if (!this.page) return;

    try {
      await this.page.mouse.wheel(data.deltaX, data.deltaY);
    } catch (e) {
      console.error('[RemoteControl] Scroll event error:', e);
    }
  }

  /**
   * 모든 클라이언트에게 알림 전송
   */
  notify(message: string, type: 'info' | 'warning' | 'error' = 'info') {
    this.io.emit('notification', { message, type });
  }

  /**
   * 연결된 클라이언트 수
   */
  getConnectedCount(): number {
    return this.connectedSockets.size;
  }
}
