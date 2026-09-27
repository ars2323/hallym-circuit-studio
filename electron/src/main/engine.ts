/* The main process's handle on the engine (docs/engine-api.md): one child
   process, JSON-RPC over its stdin and stdout.

   - start(): launches it and says hello; it is ready when hello is answered.
   - call(): a request with the next id; the answer with that id settles it.
     Calls made while it starts (or starts again) wait for it.  An error
     answer rejects with EngineError (its code and data kept).
   - Notifications (lines without an id) go out as 'notification' events;
     the main process forwards them to the window.
   - stderr is the engine's log: kept in memory (the last lines, for the
     dialog when it fails), never written to a file.
   - If it ends on its own, every call in flight is rejected with EngineGone,
     and it is started again -- at most `maxRestarts` times in
     `restartWindowMs`, then it is left failed.  Each start that answers
     hello is a new `generation`: files of an earlier one are gone (the
     window closes their tabs).

   Nothing here knows Electron: tests/unit/engine.test.ts runs it against
   the fake engine and against streams it writes by hand. */

import { EventEmitter } from 'node:events';
import type { Readable, Writable } from 'node:stream';

import type { EngineState, EngineStatus, Hello } from './protocol.ts';
import { encodeRequest, LineReader, parseLine, type RpcError } from './rpc.ts';

// What a launch gives back: a child process, or a test's stand-in.
export interface EngineProcess {
  stdin: Writable;
  stdout: Readable;
  stderr: Readable;
  pid?: number;
  kill(signal?: NodeJS.Signals): boolean;
  on(event: 'exit', listener: (code: number | null, signal: NodeJS.Signals | null) => void): this;
  on(event: 'error', listener: (error: Error) => void): this;
}
export type Launcher = () => EngineProcess;

// The engine answered the call with an error.
export class EngineError extends Error {
  readonly method: string;
  readonly code: number;
  readonly data: unknown;
  constructor(method: string, e: RpcError) {
    super(e.message);
    this.name = 'EngineError';
    this.method = method;
    this.code = e.code;
    this.data = e.data;
  }
}

// The engine ended (or was never there) before the call was answered.
export class EngineGone extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'EngineGone';
  }
}

export interface EngineOptions {
  launch: Launcher;
  client: { client: string; version: string };
  helloTimeoutMs?: number;      // how long a start may take (a JVM on a slow lab PC): default 30 s
  maxRestarts?: number;         // default 3 ...
  restartWindowMs?: number;     // ... in 60 s
  restartDelayMs?: number;      // before each restart: default 300 ms
  shutdownTimeoutMs?: number;   // engine.shutdown, then kill: default 3 s
  stderrLines?: number;         // how many of its last log lines are kept: default 40
}

interface Events {
  status: [EngineStatus];
  notification: [method: string, params: unknown];
  log: [line: string];
}

interface Pending {
  method: string;
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
}

export const MSG_CRASHED = '엔진이 멈췄습니다';
export const MSG_NOT_STARTED = '엔진을 시작하지 못했습니다';

export class EngineClient extends EventEmitter<Events> {
  private readonly opts: Required<Omit<EngineOptions, 'launch' | 'client'>> & Pick<EngineOptions, 'launch' | 'client'>;
  private proc: EngineProcess | null = null;
  private nextId = 1;
  private readonly pending = new Map<number, Pending>();
  private state: EngineState = 'stopped';
  private generation = 0;
  private hello: Hello | null = null;
  private error: string | null = null;
  private detail: string | null = null;
  private readonly stderrTail: string[] = [];
  private readonly crashes: number[] = [];   // times of the crashes in the window
  private ready: Promise<void> = Promise.resolve();
  private stopping = false;

  constructor(options: EngineOptions) {
    super();
    this.opts = {
      helloTimeoutMs: 30_000, maxRestarts: 3, restartWindowMs: 60_000, restartDelayMs: 300,
      shutdownTimeoutMs: 3_000, stderrLines: 40, ...options,
    };
  }

  status(): EngineStatus {
    return { state: this.state, generation: this.generation, hello: this.hello, error: this.error, detail: this.detail };
  }

  get pid(): number | undefined { return this.proc?.pid; }
  log(): string[] { return [...this.stderrTail]; }

  // Launches and says hello.  Resolves with the hello; a failed start
  // leaves it failed (status().error says why) and rejects with EngineGone.
  start(): Promise<Hello> {
    this.stopping = false;
    this.crashes.length = 0;
    return this.launch('starting');
  }

  // A request.  While it starts, it waits for the start; a failed or
  // stopped engine rejects at once.
  async call<T = unknown>(method: string, params: unknown = {}): Promise<T> {
    if (this.state === 'starting' || this.state === 'restarting') await this.ready.catch(() => {});
    if (this.state !== 'ready' || !this.proc) throw new EngineGone(this.error ?? MSG_NOT_STARTED);
    return this.send<T>(this.proc, method, params);
  }

  // engine.shutdown, then its end; killed if it does not end in time.
  async shutdown(): Promise<void> {
    this.stopping = true;
    const proc = this.proc;
    if (!proc) { this.setState('stopped'); return; }
    const ended = new Promise<void>((done) => proc.on('exit', () => done()));
    if (this.state === 'ready') this.send(proc, 'engine.shutdown', {}).catch(() => {});
    let timer: NodeJS.Timeout | undefined;
    const late = new Promise<'late'>((done) => { timer = setTimeout(() => done('late'), this.opts.shutdownTimeoutMs); });
    const how = await Promise.race([ended.then(() => 'ended' as const), late]);
    clearTimeout(timer);
    if (how === 'late') {
      proc.kill('SIGKILL');
      await ended;
    }
    this.setState('stopped');
  }

  // Ends the process as a crash would (tests; the e2e test of a crash).
  kill(): void { this.proc?.kill('SIGKILL'); }

  // ---------------------------------------------------------------------------

  private setState(state: EngineState): void {
    this.state = state;
    this.emit('status', this.status());
  }

  private send<T>(proc: EngineProcess, method: string, params: unknown): Promise<T> {
    const id = this.nextId++;
    return new Promise<T>((resolve, reject) => {
      this.pending.set(id, { method, resolve: resolve as (v: unknown) => void, reject });
      proc.stdin.write(encodeRequest(id, method, params));
    });
  }

  private launch(state: 'starting' | 'restarting'): Promise<Hello> {
    this.hello = null;
    this.error = null;
    this.detail = null;
    this.stderrTail.length = 0;
    this.setState(state);
    const started = this.spawnAndGreet();
    this.ready = started.then(() => {}, () => {});
    return started;
  }

  private async spawnAndGreet(): Promise<Hello> {
    let proc: EngineProcess;
    try {
      proc = this.opts.launch();
    } catch (e) {
      return this.failStart((e as Error).message);
    }
    this.proc = proc;
    // It could not be started at all (no such program): 'error'.  (Its end
    // after a start comes as the hello call's rejection: exited().)
    const unstarted = new Promise<string>((done) => proc.on('error', (e) => done(e.message)));
    this.wire(proc);
    let timer: NodeJS.Timeout | undefined;
    const late = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error(`engine.hello: ${Math.round(this.opts.helloTimeoutMs / 1000)}초 안에 응답이 없습니다`)),
        this.opts.helloTimeoutMs);
    });
    try {
      const hello = await Promise.race([
        this.send<Hello>(proc, 'engine.hello', this.opts.client),
        unstarted.then((why) => { throw new Error(`엔진을 실행하지 못했습니다 (${why})`); }),
        late,
      ]);
      clearTimeout(timer);
      this.hello = hello;
      this.generation += 1;
      this.setState('ready');
      return hello;
    } catch (e) {
      clearTimeout(timer);
      if (this.proc === proc) this.proc = null;
      proc.kill('SIGKILL');
      return this.failStart((e as Error).message);
    }
  }

  private failStart(reason: string): never {
    this.error = MSG_NOT_STARTED;
    this.detail = [reason, ...this.stderrTail.slice(-8)].join('\n');
    this.rejectAll(new EngineGone(MSG_NOT_STARTED));
    this.setState('failed');
    throw new EngineGone(`${MSG_NOT_STARTED}: ${reason}`);
  }

  private wire(proc: EngineProcess): void {
    const out = new LineReader();
    proc.stdout.on('data', (chunk: Uint8Array) => { for (const line of out.push(chunk)) this.receive(line); });
    proc.stdout.on('end', () => { for (const line of out.end()) this.receive(line); });
    const err = new LineReader();
    const keep = (line: string) => {
      this.stderrTail.push(line);
      if (this.stderrTail.length > this.opts.stderrLines) this.stderrTail.shift();
      this.emit('log', line);
    };
    proc.stderr.on('data', (chunk: Uint8Array) => err.push(chunk).forEach(keep));
    proc.stderr.on('end', () => err.end().forEach(keep));
    // Writing to an engine that has just ended: the exit below says so.
    proc.stdin.on('error', () => {});
    // Its end is taken once what it wrote last has been read (its last
    // stderr lines say why), or 200 ms after it exited, whichever is first.
    let streamsOpen = 2;
    let ended: { code: number | null; signal: NodeJS.Signals | null } | null = null;
    let reported = false;
    const report = () => {
      if (reported || !ended) return;
      reported = true;
      this.exited(proc, ended.code, ended.signal);
    };
    const closed = () => { streamsOpen -= 1; if (streamsOpen === 0) report(); };
    proc.stdout.once('end', closed);
    proc.stderr.once('end', closed);
    proc.on('exit', (code, signal) => {
      ended = { code, signal };
      if (streamsOpen <= 0) report(); else setTimeout(report, 200);
    });
  }

  private receive(line: string): void {
    const m = parseLine(line);
    if (m.kind === 'notification') { this.emit('notification', m.method, m.params); return; }
    if (m.kind === 'invalid') { this.emit('log', `(stdout, ${m.reason}) ${line}`); return; }
    if (m.id === null) { this.emit('log', `(error without id) ${m.kind === 'error' ? m.error.message : ''}`); return; }
    const p = this.pending.get(m.id);
    if (!p) { this.emit('log', `(answer to no call: id ${m.id}) ${line}`); return; }
    this.pending.delete(m.id);
    if (m.kind === 'response') p.resolve(m.result);
    else p.reject(new EngineError(p.method, m.error));
  }

  private rejectAll(error: Error): void {
    const all = [...this.pending.values()];
    this.pending.clear();
    for (const p of all) p.reject(error);
  }

  private exited(proc: EngineProcess, code: number | null, signal: NodeJS.Signals | null): void {
    if (this.proc !== proc) return; // an earlier one, already given up
    this.proc = null;
    const how = signal ? `signal ${signal}` : `exit code ${code}`;
    if (this.stopping || this.state !== 'ready') {
      // A shutdown; or a start, which reports it (spawnAndGreet).
      this.rejectAll(new EngineGone(this.stopping ? MSG_CRASHED : `엔진 프로세스가 끝났습니다 (${how})`));
      return;
    }
    this.rejectAll(new EngineGone(MSG_CRASHED));
    const now = Date.now();
    this.crashes.push(now);
    while (this.crashes.length && now - this.crashes[0] > this.opts.restartWindowMs) this.crashes.shift();
    if (this.crashes.length > this.opts.maxRestarts) {
      this.error = MSG_CRASHED;
      this.detail = [`${MSG_CRASHED} (${how}) — ${Math.round(this.opts.restartWindowMs / 1000)}초 안에 ${this.crashes.length}번`, ...this.stderrTail.slice(-8)].join('\n');
      this.setState('failed');
      return;
    }
    this.setState('restarting');
    const again = new Promise<void>((done) => setTimeout(done, this.opts.restartDelayMs));
    this.ready = again.then(() => this.launch('restarting')).then(() => {}, () => {});
  }
}
