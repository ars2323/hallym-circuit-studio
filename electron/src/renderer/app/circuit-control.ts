/* What the window does for circuits, appearances, libraries and other files
   (N-11, D-153): the Circuits panel's and the Canvas's commands, turned
   into the engine's intents (docs/engine-api.md) with the dialogs in
   circuit-dialogs.ts.  The engine is the authority: every change comes back
   as file.changed, model.changed and model.appearance; this only asks, and
   says in the status bar what came of it (Korean sentences, English names). */

import type { CircuitRef, EditResult, Impact, LibrariesInfo, PortsInfo, RecoveryAsk, SaveCut } from '../../main/protocol.ts';
import type { AppApi, CallError, Opened } from './api.ts';
import { askName, chooseCircuits, chooseLibraries, confirmImpact, confirmPlan, confirmSaveCuts, portOrder } from './circuit-dialogs.ts';
import type { CircuitCommand, FileCommand } from './circuits.ts';
import { commandError } from './logic/errors.ts';
import { createRefusal, importedText, loadRefusal, nameProblem, removeRefusal, unloadRefusal } from './logic/circuits.ts';
import type { OpenFile } from './logic/files.ts';

export interface CircuitHost {
  api: AppApi;
  ready(): boolean;
  file(fileId: string): OpenFile | undefined;
  active(): OpenFile | null;
  note(cls: '' | 'err' | 'ok', text: string | null): void;
  // a circuit's tab: its layout, or its appearance
  show(fileId: string, circuitId: string, appearance: boolean): void;
  // a file the engine opened (Edit Original File), after its recovery file's question if it has one (N-19)
  opened(r: Opened | RecoveryAsk): Promise<Opened | null>;
  librariesChanged(fileId: string): void;           // the Components list asks again
}

type Result = EditResult & { impact?: Impact; lib?: string };

export class CircuitControl {
  private readonly host: CircuitHost;
  constructor(host: CircuitHost) { this.host = host; }

  private async call<T>(method: Parameters<AppApi['call']>[0], params: Record<string, unknown>, name: string): Promise<T | null> {
    if (!this.host.ready()) return null;
    try {
      return await this.host.api.call<T>(method, params);
    } catch (e) {
      this.host.note('err', this.refusal(name, e as CallError) ?? commandError(name, e as CallError));
      return null;
    }
  }

  // The engine's refusals of these commands, in the window's words.
  private refusal(name: string, e: CallError): string | null {
    const data = (e.data ?? {}) as { reason?: string; circuit?: string };
    if (name === 'Load Library') return loadRefusal(data.reason);
    if (name === 'Unload Library') return unloadRefusal(data.reason, data.circuit);
    return null;
  }

  private circuit(f: OpenFile, circuitId: string): CircuitRef | undefined { return f.circuits.find((c) => c.circuitId === circuitId); }

  // ---- the Circuits panel ----

  async command(cmd: CircuitCommand, circuitId: string): Promise<void> {
    const f = this.host.active();
    if (!f) return;
    const c = this.circuit(f, circuitId);
    if (!c) return;
    switch (cmd) {
      case 'open': this.host.show(f.fileId, circuitId, false); break;
      case 'layout': this.host.show(f.fileId, circuitId, false); break;
      case 'appearance': this.host.show(f.fileId, circuitId, true); break;
      case 'rename': await this.rename(f, c); break;
      case 'main': await this.call('edit.setMainCircuit', { fileId: f.fileId, circuitId }, 'Set As Main Circuit'); break;
      case 'portOrder': await this.portOrder(f, c); break;
      case 'autoAppearance': await this.autoAppearance(f, c); break;
      case 'up': case 'down': {
        const i = f.circuits.findIndex((k) => k.circuitId === circuitId);
        await this.moveTo(circuitId, cmd === 'up' ? i - 1 : i + 1);
        break;
      }
      case 'remove': await this.remove(f, c); break;
    }
  }

  async moveTo(circuitId: string, to: number): Promise<void> {
    const f = this.host.active();
    if (!f || to < 0 || to >= f.circuits.length) return;
    await this.call('edit.moveCircuit', { fileId: f.fileId, circuitId, to }, 'Move Circuit');
  }

  async add(): Promise<void> {
    const f = this.host.active();
    if (!f || !this.host.ready()) return;
    const name = await askName({ title: 'Add Circuit', sentence: '새 회로의 이름을 적으세요. 이름은 부품 목록과 서브회로 인스턴스에 나옵니다.', value: '', ok: 'Add', check: (n) => nameProblem(n, f.circuits) });
    if (name === null) return;
    try {
      const r = await this.host.api.call<Result>('edit.createCircuit', { fileId: f.fileId, name });
      if (r.circuitId) this.host.show(f.fileId, r.circuitId, false);
      this.host.note('', null);
    } catch (e) {
      this.host.note('err', createRefusal(((e as CallError).data as { reason?: string } | undefined)?.reason) ?? commandError('Add Circuit', e as CallError));
    }
  }

  private async rename(f: OpenFile, c: CircuitRef): Promise<void> {
    const name = await askName({ title: 'Rename Circuit', sentence: '회로의 새 이름을 적으세요. 이 회로의 인스턴스는 새 이름을 따라갑니다.', value: c.name, ok: 'Rename', check: (n) => nameProblem(n, f.circuits, c.circuitId) });
    if (name === null || name === c.name) return;
    await this.call('edit.setCircuitAttr', { fileId: f.fileId, circuitId: c.circuitId, attr: 'circuit', value: name }, 'Rename');
  }

  private async remove(f: OpenFile, c: CircuitRef): Promise<void> {
    if (!this.host.ready()) return;
    try {
      await this.host.api.call('edit.deleteCircuit', { fileId: f.fileId, circuitId: c.circuitId });
      this.host.note('ok', `Remove Circuit · ${c.name}`);
    } catch (e) {
      const reason = ((e as CallError).data as { reason?: string } | undefined)?.reason;
      this.host.note('err', removeRefusal(reason, c.name) ?? commandError('Remove Circuit', e as CallError));
    }
  }

  // Port Order… (v1 P-04): the dialog, then the engine; if connections break, the question first.
  async portOrder(f: OpenFile, c: CircuitRef): Promise<void> {
    const ports = await this.call<PortsInfo>('model.ports', { fileId: f.fileId, circuitId: c.circuitId }, 'Port Order');
    if (!ports) return;
    const order = await portOrder(ports);
    if (!order) return;
    await this.appearanceChange('edit.portOrder', { fileId: f.fileId, circuitId: c.circuitId, order }, 'Port Order');
  }

  async autoAppearance(f: OpenFile, c: CircuitRef): Promise<void> {
    await this.appearanceChange('edit.autoAppearance', { fileId: f.fileId, circuitId: c.circuitId }, 'Auto Appearance');
  }

  // v1 AutoAppearance.run: ask the engine without breaking anything; if it would, the question, then again.
  private async appearanceChange(method: 'edit.portOrder' | 'edit.autoAppearance', params: Record<string, unknown>, name: string): Promise<void> {
    const r = await this.call<Result>(method, { ...params, confirm: false }, name);
    if (!r) return;
    if (r.outcome === 'noPorts') { this.host.note('err', `${name}: 이 회로에는 핀이 없어 모양을 만들 포트가 없습니다`); return; }
    if (r.outcome !== 'needsConfirm' || !r.impact) { this.host.note('', null); return; }
    if (!(await confirmImpact(name, r.impact))) return;
    await this.call<Result>(method, { ...params, confirm: true }, name);
  }

  // ---- the head row: Import, Load/Unload Library ----

  async fileCommand(cmd: FileCommand): Promise<void> {
    switch (cmd) {
      case 'add': await this.add(); break;
      case 'import': await this.importCircuits(); break;
      case 'loadBuiltin': await this.loadBuiltin(); break;
      case 'loadCirc': await this.loadFile('circ'); break;
      case 'loadJar': await this.loadFile('jar'); break;
      case 'unload': await this.unloadSome(); break;
    }
  }

  private async importCircuits(): Promise<void> {
    const f = this.host.active();
    if (!f || !this.host.ready()) return;
    try {
      const peek = await this.host.api.importChoose(f.fileId);
      if (!peek) return;
      const chosen = await chooseCircuits(peek);
      if (!chosen || chosen.length === 0) return;
      const plan = await this.host.api.importPlan(f.fileId, chosen);
      if (!(await confirmPlan(peek.name, plan))) return;
      const r = await this.host.api.importApply(f.fileId, chosen);
      this.host.note('ok', importedText(r.plan?.order.length ?? plan.order.length, peek.name));
    } catch (e) {
      const reason = ((e as CallError).data as { reason?: string } | undefined)?.reason;
      this.host.note('err', reason === 'sameFile' ? 'Import Subcircuits: 지금 열린 이 파일 자신입니다' : commandError('Import Subcircuits', e as CallError));
    }
  }

  private async libraries(f: OpenFile): Promise<LibrariesInfo | null> {
    return this.call<LibrariesInfo>('model.libraries', { fileId: f.fileId }, 'Load Library');
  }

  private async loadBuiltin(): Promise<void> {
    const f = this.host.active();
    if (!f) return;
    const info = await this.libraries(f);
    if (!info) return;
    const names = await chooseLibraries({
      title: 'Load Built-in Library', ok: 'Load',
      sentence: info.builtins.length ? '넣을 기본 라이브러리를 고르세요. 넣은 라이브러리는 목록 끝에 붙습니다.' : '이 파일에는 기본 라이브러리가 모두 들어 있습니다.',
      items: info.builtins.map((b) => ({ name: b.name, display: b.display })),
    });
    for (const name of names ?? []) {
      try {
        await this.host.api.loadLibrary(f.fileId, 'builtin', name);
      } catch (e) {
        this.host.note('err', this.refusal('Load Library', e as CallError) ?? commandError('Load Library', e as CallError));
        break;
      }
    }
    if (names?.length) this.host.librariesChanged(f.fileId);
  }

  private async loadFile(kind: 'circ' | 'jar'): Promise<void> {
    const f = this.host.active();
    if (!f || !this.host.ready()) return;
    try {
      const r = await this.host.api.loadLibrary(f.fileId, kind);
      if (!r) return;
      this.host.note(r.outcome === 'already' ? '' : 'ok', r.outcome === 'already' ? 'Load Library: 이미 이 파일의 라이브러리입니다' : `Load Library · ${r.lib ?? ''}`);
      this.host.librariesChanged(f.fileId);
    } catch (e) {
      this.host.note('err', this.refusal('Load Library', e as CallError) ?? commandError('Load Library', e as CallError));
    }
  }

  private async unloadSome(): Promise<void> {
    const f = this.host.active();
    if (!f) return;
    const info = await this.libraries(f);
    if (!info) return;
    const names = await chooseLibraries({
      title: 'Unload Libraries', ok: 'Unload',
      sentence: '뺄 라이브러리를 고르세요. 이 파일의 회로가 부품을 쓰는 라이브러리는 뺄 수 없습니다.',
      items: info.loaded.map((l) => ({ name: l.name, display: l.display, note: l.usedIn ? `used in ${l.usedIn}` : null, disabled: l.usedIn !== null })),
    });
    for (const name of names ?? []) if (!(await this.unload(name))) break;
  }

  // Unload Library (a library's right click, the dialog): true when it went.
  async unload(lib: string): Promise<boolean> {
    const f = this.host.active();
    if (!f) return false;
    const r = await this.call<Result>('edit.unloadLibrary', { fileId: f.fileId, name: lib }, 'Unload Library');
    if (r) this.host.librariesChanged(f.fileId);
    return r !== null;
  }

  // Another open file's circuit (P-03): its file as a library, then the circuit is what the Components list picks.
  async useOpenFile(otherFileId: string): Promise<string | null> {
    const f = this.host.active();
    if (!f || !this.host.ready()) return null;
    const other = this.host.file(otherFileId);
    if (!other) return null;
    if (other.path === null) { this.host.note('err', '라이브러리로 쓰려면 그 파일을 먼저 저장하세요'); return null; }
    try {
      const r = await this.host.api.useOpenFile(f.fileId, otherFileId);
      this.host.librariesChanged(f.fileId);
      return r.lib ?? null;
    } catch (e) {
      this.host.note('err', this.refusal('Load Library', e as CallError) ?? commandError('Load Library', e as CallError));
      return null;
    }
  }

  // Edit Original File (a library circuit's instance): that file's tab, the circuit on show.
  async editOriginal(fileId: string, circuitId: string): Promise<void> {
    try {
      const r = await this.host.api.editOriginal(fileId, circuitId);
      if (!r) return;
      const o = await this.host.opened(r);
      if (!o) return;
      const opened = this.host.file(o.fileId);
      const c = opened?.circuits.find((x) => x.name === r.circuit);
      if (opened && c) this.host.show(o.fileId, c.circuitId, false);
    } catch (e) {
      this.host.note('err', commandError('Edit Original File', e as CallError));
    }
  }

  // ---- saving (P-03): what other open files would lose; hcs-mips.jar beside the file ----

  async saveCuts(f: OpenFile): Promise<boolean> {
    if (!this.host.ready() || f.path === null) return true;
    let cuts: SaveCut[] = [];
    try { cuts = (await this.host.api.call<{ cuts: SaveCut[] }>('file.saveImpact', { fileId: f.fileId })).cuts; } catch { return true; }
    if (!cuts.length) return true;
    return confirmSaveCuts(f.name, cuts);
  }

  async copyMipsJar(fileId: string): Promise<void> {
    const r = await this.call<{ name: string }>('file.copyMipsJar', { fileId }, 'Copy hcs-mips.jar Here');
    if (r) this.host.note('ok', `복사했습니다 · ${r.name}`);
  }
}
