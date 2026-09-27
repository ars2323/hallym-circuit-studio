/* JSON-RPC 2.0 over the engine's stdin and stdout: one JSON object per
   line, UTF-8, "\n" at the end (docs/engine-api.md 2).  Pure: no process,
   no Electron -- tests/unit/rpc.test.ts feeds it bytes.

   A line that is not a JSON-RPC message is not an error of the call in
   flight: it is reported as `invalid` and left out (a stray print in the
   engine must not take a call's answer with it). */

export interface RpcError {
  code: number;
  message: string;
  data?: unknown;
}

export type Incoming =
  | { kind: 'response'; id: number; result: unknown }
  | { kind: 'error'; id: number | null; error: RpcError }
  | { kind: 'notification'; method: string; params: unknown }
  | { kind: 'invalid'; line: string; reason: string };

export function encodeRequest(id: number, method: string, params: unknown): string {
  return `${JSON.stringify({ jsonrpc: '2.0', id, method, params: params ?? {} })}\n`;
}

const isObject = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);

export function parseLine(line: string): Incoming {
  let msg: unknown;
  try {
    msg = JSON.parse(line);
  } catch {
    return { kind: 'invalid', line, reason: 'not JSON' };
  }
  if (!isObject(msg) || msg.jsonrpc !== '2.0') return { kind: 'invalid', line, reason: 'not a JSON-RPC 2.0 message' };
  const hasId = 'id' in msg && msg.id !== undefined;
  if (typeof msg.method === 'string') {
    if (hasId) return { kind: 'invalid', line, reason: 'a request from the engine (the engine only answers and notifies)' };
    return { kind: 'notification', method: msg.method, params: msg.params ?? {} };
  }
  if ('error' in msg) {
    const e = msg.error;
    if (!isObject(e) || typeof e.code !== 'number' || typeof e.message !== 'string') return { kind: 'invalid', line, reason: 'malformed error' };
    const id = typeof msg.id === 'number' ? msg.id : null;
    return { kind: 'error', id, error: { code: e.code, message: e.message, ...('data' in e ? { data: e.data } : {}) } };
  }
  if ('result' in msg) {
    if (typeof msg.id !== 'number') return { kind: 'invalid', line, reason: 'a response without a numeric id' };
    return { kind: 'response', id: msg.id, result: msg.result };
  }
  return { kind: 'invalid', line, reason: 'neither a response nor a notification' };
}

/* The engine's stdout as lines.  Bytes arrive in chunks of any size: a line
   may take several, and a chunk may end inside a character (a Hangul name
   is three bytes) -- the decoder keeps the unfinished bytes for the next
   one.  "\r\n" ends a line as "\n" does; empty lines are nothing. */
export class LineReader {
  private readonly decoder = new TextDecoder('utf-8');
  private rest = '';

  push(chunk: Uint8Array): string[] {
    const text = this.rest + this.decoder.decode(chunk, { stream: true });
    const parts = text.split('\n');
    this.rest = parts.pop() ?? '';
    return parts.map((p) => (p.endsWith('\r') ? p.slice(0, -1) : p)).filter((p) => p.trim() !== '');
  }

  // The stream ended: what is left, if anything, is a last line without its "\n".
  end(): string[] {
    const last = (this.rest + this.decoder.decode()).replace(/\r$/, '');
    this.rest = '';
    return last.trim() === '' ? [] : [last];
  }
}
