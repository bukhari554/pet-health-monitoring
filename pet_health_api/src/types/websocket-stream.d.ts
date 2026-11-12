declare module 'websocket-stream' {
  import type { Server as HttpServer, IncomingMessage } from 'http';
  import type { Duplex } from 'stream';

  interface CreateServerOptions {
    server: HttpServer;
    path?: string;
  }

  type ConnectionHandler = (stream: Duplex, request: IncomingMessage) => void;

  const websocketStream: {
    createServer: (opts: CreateServerOptions, handle: ConnectionHandler) => void;
  };

  export default websocketStream;
}
