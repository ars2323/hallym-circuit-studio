/* The engine's status at the window's start (D-146): the window asks for it
   (an invoke) while the main process may already be pushing a newer one
   (engine:status).  Electron does not keep an invoke's answer and a push in
   order, so an answer computed before the engine said hello can come after
   the push that says it is ready -- and the window would wait on "Engine
   starting" for good.  A status pushed while the question was on its way is
   the newer one: the answer is taken only when none came. */

import type { EngineStatus } from '../../../main/protocol.ts';

export class StatusKeeper {
  private pushes = 0;
  status: EngineStatus;

  constructor(first: EngineStatus) { this.status = first; }

  // A push from the main process: always the newest.
  pushed(s: EngineStatus): void {
    this.pushes++;
    this.status = s;
  }

  // Asks (the invoke) and takes the answer unless a push came meanwhile.
  async ask(get: () => Promise<EngineStatus>): Promise<EngineStatus> {
    const before = this.pushes;
    const s = await get();
    if (this.pushes === before) this.status = s;
    return this.status;
  }
}
