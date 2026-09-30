/* The tutorial's engine (derived from Hallym MIPS v2.5.0
   electron/src/renderer/app/tutorial.ts): a list of steps over an example
   the caller opens, and the two layers that point at what a step is about.
   The steps are the caller's (src/renderer/app/tutorial/): this file knows
   nothing of circuits.  Nothing of it is kept: a new start of the program
   always begins at the course choice; within one run, coming back to the
   same course offers to go on where it stopped.

   Two kinds of step:
     explain   points at something; [다음] (or →) goes on;
     practice  the student does the thing (places a part, draws a wire, one
               cycle...), the tutorial sees it happen (the caller tells it
               something changed: changed(), and the step's done() reads the
               engine's facts); [건너뛰기] turns up after a few seconds and
               does it for them, so that the steps after have what they need.
               A practice step whose result is something to see then points
               at that result on the same card and waits for [다음].  A step
               whose result the next step points at anyway goes straight on.
   A card is one text, a title and a body: a practice step's body says what
   to do and, in its last sentence, what happens once it is done.  No [다음]
   on the card is the other sign that it waits for the student.

   Two layers.  The panel a target is in is lit whole (not dimmed), the
   title bar for a toolbar button, the status bar for its words; the rest of
   the window is dimmed -- the window's caption buttons too (the host's
   shade(): a patch the page cannot paint).  Inside, a box on each target
   says where to look.  Lit is not clickable: only the targets take a click
   (and what the step lets through without a box: pass(), its own pop-ups).
   The card never covers a box, and keeps off the lit panels where the
   window has room (placement.ts), with Haram at its far end.  Before
   drawing, a step makes its targets really visible (reveal(): the tab, the
   Canvas's view, a fold opened), and says what it did (`did`).

   Keys: → next, ← back, Esc stop (asks first); while the clock runs or a
   Signal Flow is on show, Esc stops that instead (the host's escape()).
   Keys a step does not ask for do nothing, so the circuit
   stays where the next steps expect it.

   Changed from the upstream: the steps and the host come from the caller
   (the upstream's twenty steps over an assembly program are not taken: they
   lean on the simulator's own words, ORIGIN.md); done() reads facts instead of matching one
   signal; keys a step lets through are named ("Ctrl+K", "F10"); pass();
   the caption buttons darken (shade); [건너뛰기]'s delay can be set (the
   tests); the questions' buttons are names in English (D-135 14). */

import { ask } from './ask.ts';
import { character, codeText, h } from './dom.ts';
import { merge, place, type Rect } from './placement.ts';

// An element, or a box inside one (a place on the Canvas): the element is
// what the box is cut to and what a click there must reach.
export type Target = Element | { rect: DOMRect | null; within: Element | null } | null | undefined;

// A key a step lets through: "F5", "F10", "Ctrl+K", "Ctrl+=", "Ctrl+-", "Ctrl+0", "Delete".
export type Key = string;

export interface Result<T> {
  title(t: T): string;
  body(t: T): string;
  targets(t: T): Target[];
  reveal?(t: T): void;
}

export interface Step<T> {
  id: string;                               // "L3", "C6": for the tests and the log
  kind: 'explain' | 'practice' | 'end';
  pose?: string;                            // the character's picture (assets/hallym/character/<pose>.png)
  keys?: Key[];
  typing?: boolean;                         // plain keys reach the window (a search typed on the Canvas)
  title(t: T): string;
  body(t: T): string;                       // `code` in backticks
  targets(t: T): Target[];
  pass?(t: T): Target[];                    // clickable without a box: the step's own pop-ups
  avoid?(t: T): Target[];                   // not pointed at, but the card keeps off it
  lights?(t: T): Element[];                 // areas lit whole besides the targets' own
  prepare?(t: T): Promise<void>;            // the circuit where the step needs it
  reveal?(t: T): void;                      // the targets into view
  done?(t: T): boolean;                     // the practice done (read from the engine's facts)
  result?: Result<T>;                       // shown after done, before the next step
  skip?(t: T): Promise<void>;
  leave?(t: T): Promise<void>;
}

// What the window does for the tutorial.
export interface TutorialHost {
  begin(): Promise<void>;                   // the example up
  finish(): Promise<void>;                  // the example down, back to what was there
  running(): boolean;                       // the clock ticks
  stop(): Promise<void>;
  escape(): Promise<boolean>;               // Esc's own job first (the clock stopped, a Signal Flow put away): whether it did one
  shade(on: boolean): void;                 // the caption buttons darken with the page
}

export interface Shown {
  id: string; step: number; phase: number; result: boolean;
  targets: Rect[]; passes: Rect[]; lit: Rect[]; card: Rect | null; hits: boolean[]; did: string[];
}

export class Tutorial<H extends TutorialHost> {
  readonly host: H;
  readonly steps: Step<Tutorial<H>>[];
  active = false;
  index = 0;
  phase = 0;
  result = false;                           // a practice step done: its result on the card, [다음] awaited
  skipAfter = 6000;                         // ms until [건너뛰기] (the tests make it short)
  advanceAfter = 500;                       // ms between a practice done and the next step
  ended: (() => void) | null = null;        // told when the tutorial has ended (the window takes its keys back)
  private lastStep = 0;                     // this run of the program only
  private busy = false;
  private root: HTMLElement | null = null;
  private dim: SVGPathElement | null = null;   // dark, but over the lit panels
  private block: SVGPathElement | null = null; // clicks, but on the targets
  private rings: HTMLElement | null = null;
  private card: HTMLElement | null = null;
  private skipTimer = 0;
  private skipShown = false;
  private frame = 0;
  private lastLayout = '';
  private lastReveal = 0;
  private advanceTimer = 0;
  // The last layout, for the tests: what is pointed at and where the card is.
  shown: Shown = { id: '', step: 0, phase: 0, result: false, targets: [], passes: [], lit: [], card: null, hits: [], did: [] };
  // What this step had to do to show its targets (for the report and tests).
  did: string[] = [];

  constructor(steps: Step<Tutorial<H>>[], host: H) {
    this.steps = steps;
    this.host = host;
  }

  get step(): Step<Tutorial<H>> { return this.steps[this.index]; }

  // ---- start and end ----------------------------------------------------------------

  async start(): Promise<void> {
    if (this.active) return;
    let from = 0;
    if (this.lastStep > 0) {
      const again = await ask({
        title: '이어서 할까요?',
        body: `지난번에 ${this.lastStep + 1}단계에서 그만두었습니다. 프로그램을 끄면 이 기록은 없어지고 다시 1단계부터입니다.`,
        ok: `Resume (${this.lastStep + 1})`, cancel: 'From the Start',
      });
      from = again ? this.lastStep : 0;
    }
    this.active = true;
    document.body.classList.add('tutorial-on');
    this.host.shade(true);
    try {
      await this.host.begin();
    } catch (e) {
      this.active = false;
      document.body.classList.remove('tutorial-on');
      this.host.shade(false);
      throw e;
    }
    this.mount();
    await this.go(from);
  }

  async quit(): Promise<void> {
    if (this.busy) return;
    const sure = this.index === this.steps.length - 1 || await ask({
      title: '튜토리얼을 그만둘까요?',
      body: '예제는 내려가고 튜토리얼을 시작하기 전의 화면으로 돌아갑니다.',
      ok: 'Quit Tutorial', cancel: 'Continue', outsideCancels: true,   // a click outside: the question away, the tutorial goes on
    });
    if (sure) await this.end();
  }

  async end(): Promise<void> {
    if (!this.active) return;
    this.busy = true;
    try {
      await this.step.leave?.(this);
      if (this.host.running()) await this.host.stop();
      this.lastStep = this.index === this.steps.length - 1 ? 0 : this.index;
      this.unmount();
      this.active = false;
      document.body.classList.remove('tutorial-on');
      this.host.shade(false);
      await this.host.finish();
      this.ended?.();
    } finally {
      this.busy = false;
    }
  }

  // ---- steps ----------------------------------------------------------------------

  async go(i: number): Promise<void> {
    if (this.busy || i < 0 || i >= this.steps.length) return;
    this.busy = true;
    clearTimeout(this.skipTimer);
    clearTimeout(this.advanceTimer);
    try {
      if (this.active && i !== this.index) await this.step.leave?.(this);
      this.index = i;
      this.phase = 0;
      this.result = false;
      this.did = [];
      await this.step.prepare?.(this);
      this.renderCard();
      this.lastLayout = '';
      this.lastReveal = 0;
      if (this.step.kind === 'practice') this.armSkip();
    } finally {
      this.busy = false;
    }
    // Done already (the student did it on the step before): on at once.
    if (this.step.kind === 'practice') this.changed();
  }

  private armSkip(): void {
    this.skipShown = false;
    clearTimeout(this.skipTimer);
    this.skipTimer = window.setTimeout(() => { this.skipShown = true; this.renderCard(); }, this.skipAfter);
  }

  next(): void { void this.go(this.index + 1); }
  back(): void { void this.go(this.index - 1); }

  async skip(): Promise<void> {
    if (this.busy) return;
    const step = this.step;
    this.busy = true;
    try {
      await step.skip?.(this);
    } finally {
      this.busy = false;
    }
    if (step !== this.step) return;
    if (step.result) { this.showResult(); return; }
    this.next();
  }

  // The result beat: the card points at what the step just did and waits.
  private showResult(): void {
    this.result = true;
    clearTimeout(this.skipTimer);
    this.renderCard();
    this.lastLayout = '';
    this.lastReveal = 0;
  }

  // Something happened in the window (the model, the simulation, the messages, a tool, the view...):
  // the practice step looks at the facts again.
  changed(): void {
    if (!this.active || this.busy || this.result) return;
    const step = this.step;
    if (step.kind !== 'practice' || !step.done?.(this)) return;
    if (step.result) { this.showResult(); return; }
    const at = this.index;
    clearTimeout(this.advanceTimer);
    this.advanceTimer = window.setTimeout(() => { if (this.index === at && this.active && !this.result) this.next(); }, this.advanceAfter);
  }

  // The window's keys go through here first; true: taken (or refused).
  handleKey(e: KeyboardEvent): boolean {
    if (!this.active || document.querySelector('dialog[open]')) return false;
    const step = this.step;
    // Only a step that asks for typing (a search, a label) lets words into a field or onto the Canvas.
    const typing = !!step.typing && !!(e.target as HTMLElement | null)?.closest?.('input, textarea, select, [contenteditable="true"]');
    const plain = !e.ctrlKey && !e.altKey && !e.metaKey;
    const take = () => { e.preventDefault(); e.stopImmediatePropagation(); return true; };
    if (e.key === 'Escape') {
      if (typing || document.querySelector('.palette:not([hidden]), .ovmenu, .popmenu')) return false; // closes that first
      take();
      void this.host.escape().then((did) => { if (!did) void this.quit(); });
      return true;
    }
    if ((e.key === 'ArrowRight' || e.key === 'ArrowLeft') && !typing && plain) {
      take();
      if (e.key === 'ArrowLeft') { this.back(); return true; }
      if (step.kind === 'explain' || this.result) this.next();
      else if (step.kind === 'practice' && this.skipShown) void this.skip();
      return true;
    }
    if (e.key === 'Shift' || e.key === 'Control' || e.key === 'Alt' || e.key === 'Meta') return false;   // a modifier alone does nothing
    const name = keyName(e);
    if (this.result) return take();                   // done: the result is what to look at
    if (name && (step.keys ?? []).includes(name)) return false;
    if (step.typing && plain && (e.key.length === 1 || ['Enter', 'Backspace', 'Delete', 'ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key))) {
      // the words the step asks for (in its field, or typed on the Canvas: the search opens with them)
      if (e.key !== 'Delete' || typing) return false;
    }
    return take();                                    // any other key: nothing (the circuit stays as the next steps expect)
  }

  // ---- drawing -----------------------------------------------------------------

  private mount(): void {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('class', 'tut-dim');
    this.dim = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    this.dim.setAttribute('fill-rule', 'evenodd');
    this.dim.setAttribute('class', 'dim');
    this.block = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    this.block.setAttribute('fill-rule', 'evenodd');
    this.block.setAttribute('class', 'block');
    svg.append(this.dim, this.block);
    this.rings = h('div', { class: 'tut-rings' });
    this.card = h('div', { class: 'tut-card', role: 'dialog', 'aria-label': 'Tutorial' });
    this.root = h('div', { class: 'tut' }, svg as unknown as HTMLElement, this.rings, this.card);
    document.body.append(this.root);
    const tick = () => { this.layout(); this.frame = requestAnimationFrame(tick); };
    this.frame = requestAnimationFrame(tick);
  }

  private unmount(): void {
    cancelAnimationFrame(this.frame);
    clearTimeout(this.skipTimer);
    clearTimeout(this.advanceTimer);
    this.root?.remove();
    this.root = this.dim = this.block = this.rings = this.card = null;
  }

  renderCard(): void {
    const card = this.card;
    if (!card) return;
    const step = this.step;
    const n = this.index + 1;
    const button = (label: string, cls: string, onClick: () => void, disabled = false) => {
      const b = h('button', { class: `btn small ${cls}`, type: 'button', disabled }, label);
      b.addEventListener('click', onClick);
      return b;
    };
    const res = this.result ? step.result : undefined;
    const buttons: HTMLElement[] = [button('이전', 'tut-back', () => this.back(), n === 1)];
    if (step.kind === 'explain' || res) buttons.push(button('다음', 'primary tut-next', () => this.next()));
    if (step.kind === 'practice' && !res && this.skipShown) buttons.push(button('건너뛰기', 'tut-skip', () => void this.skip()));
    if (step.kind === 'end') buttons.push(button('끝내기', 'primary tut-finish', () => void this.end()));
    card.className = `tut-card kind-${step.kind}${res ? ' done' : ''}`;
    card.dataset.step = step.id;
    card.replaceChildren(
      h('div', { class: 'tut-say' },
        h('div', { class: 'tut-top' }, h('span', { class: 'tut-count' }, `${n} / ${this.steps.length}`),
          step.kind === 'end' ? null : button('그만두기', 'tut-quit linkish', () => void this.quit())),
        h('h3', {}, (res ?? step).title(this)),
        h('p', {}, codeText((res ?? step).body(this))),
        h('div', { class: 'tut-buttons' }, ...buttons)),
      character(step.pose ?? 'haram', 76));
    (card.querySelector('img.char') as HTMLElement).classList.add('tut-char');
    this.lastLayout = '';
  }

  // Every frame: where the targets are now; the dimmed layer, the rings and
  // the card follow them (the Canvas moves, lists scroll, pop-ups open...).
  private layout(): void {
    if (!this.card || !this.dim || !this.block || !this.rings || this.busy) return;
    const step = this.step;
    const now: { targets(t: Tutorial<H>): Target[]; reveal?(t: Tutorial<H>): void } = this.result ? step.result! : step;
    const rects = targetRects(now.targets(this));
    // A target not (wholly) in view: bring it in, at most five times a second.
    if (now.reveal && (rects.missing || rects.clipped) && performance.now() - this.lastReveal > 200) {
      this.lastReveal = performance.now();
      now.reveal(this);
      if (!this.did.includes('revealed')) this.did.push('revealed');
    }
    const w = window.innerWidth;
    const hh = window.innerHeight;
    const passes = this.result ? { list: [], owners: [] } : targetRects(step.pass?.(this) ?? []);
    // The areas lit whole: each target's panel (the title bar, the status bar), cut to the window.
    const extra = (step.lights?.(this) ?? []).map((e) => e.getBoundingClientRect());
    const lit = merge([...litAreas(rects.owners), ...extra].map((r) => ({ left: Math.max(0, r.left), top: Math.max(0, r.top),
      right: Math.min(w, r.right), bottom: Math.min(hh, r.bottom) })).filter((r) => r.right > r.left && r.bottom > r.top), 0);
    const key = JSON.stringify([rects.list, passes.list, lit, w, hh, this.card.offsetWidth, this.card.offsetHeight, this.index, this.phase, this.result]);
    if (key === this.lastLayout) return;
    this.lastLayout = key;
    const path = (holes: Rect[]) => `M0 0H${w}V${hh}H0Z ${holes.map((r) => `M${r.left} ${r.top}H${r.right}V${r.bottom}H${r.left}Z`).join(' ')}`;
    this.dim.setAttribute('d', path(lit));
    this.block.setAttribute('d', path(merge([...rects.list.map((r) => grow(r, 4)), ...passes.list])));
    // A ring 4 px around its target, or closer when another target is near.
    this.rings.replaceChildren(...rects.list.map((r, i) => {
      const near = Math.min(Infinity, ...rects.list.filter((_, j) => j !== i).map((q) => distance(r, q)));
      const out = Math.max(0, Math.min(4, Math.floor((near - 4) / 2)));
      return h('div', {
        class: `tut-ring${out < 2 ? ' tight' : ''}`,
        style: `left:${r.left - out}px;top:${r.top - out}px;width:${r.right - r.left + 2 * out}px;height:${r.bottom - r.top + 2 * out}px`,
      });
    }));
    const size = { width: this.card.offsetWidth, height: this.card.offsetHeight };
    // Below the title bar: the card never hides the toolbar.
    const view = { left: 0, top: (document.querySelector('.titlebar')?.getBoundingClientRect().bottom ?? 0), right: w, bottom: hh };
    const keepOff = targetRects(step.avoid?.(this) ?? []).list;
    const grown = [...rects.list, ...passes.list].map((r) => grow(r, 4));
    const at = step.kind === 'end' || rects.list.length === 0
      ? { left: (w - size.width) / 2, top: (hh - size.height) / 2, side: null }
      : place([...grown, ...keepOff, ...lit], size, view) ?? place([...grown, ...keepOff], size, view) ?? place(grown, size, view)
        ?? { left: w - size.width - 8, top: hh - size.height - 8, side: null };
    this.card.style.left = `${Math.round(at.left)}px`;
    this.card.style.top = `${Math.round(at.top)}px`;
    // Haram at the card's far end from the targets (from the first target).
    const first = rects.list[0];
    const far = first && (first.left + first.right) / 2 > at.left + size.width / 2 ? 'left' : 'right';
    this.card.classList.toggle('haram-left', far === 'left');
    // A click in the middle of each target reaches it (not the card, not something else drawn over it).
    const hits = rects.list.map((r, i) => {
      const hit = document.elementFromPoint((r.left + r.right) / 2, (r.top + r.bottom) / 2);
      return !!hit && !!rects.owners[i]?.contains(hit);
    });
    this.shown = {
      id: step.id, step: this.index + 1, phase: this.phase, result: this.result, targets: rects.list, passes: passes.list, lit, hits, did: [...this.did],
      card: { left: Math.round(at.left), top: Math.round(at.top), right: Math.round(at.left) + size.width, bottom: Math.round(at.top) + size.height },
    };
  }
}

// The key a step can name: "F5", "Ctrl+K", "Ctrl+=" …; null for a key no step names.
export function keyName(e: KeyboardEvent): Key | null {
  if (/^F\d+$/.test(e.key)) return e.key;
  if (e.key === 'Delete') return 'Delete';
  if (e.ctrlKey || e.metaKey) {
    const k = e.key === '+' ? '=' : e.key === '_' ? '-' : e.key.length === 1 ? e.key.toUpperCase() : e.key;
    return `Ctrl+${k}`;
  }
  return null;
}

// What is lit whole around the targets: the panel each is in -- or, for a
// toolbar button, the title bar; for the status bar's words, the status bar.
function litAreas(owners: Element[]): DOMRect[] {
  const areas = new Set<Element>();
  for (const o of owners) {
    const area = o.closest('.panel, .titlebar, .status');
    if (area) areas.add(area);
  }
  return [...areas].map((a) => a.getBoundingClientRect());
}

// The gap between two boxes (0 when they touch or overlap).
function distance(a: Rect, b: Rect): number {
  const dx = Math.max(0, b.left - a.right, a.left - b.right);
  const dy = Math.max(0, b.top - a.bottom, a.top - b.bottom);
  return Math.hypot(dx, dy);
}

function grow(r: Rect, by: number): Rect {
  return { left: r.left - by, top: r.top - by, right: r.right + by, bottom: r.bottom + by };
}

// The targets' boxes, cut to what their scrolling boxes show; whether one
// is missing or cut short (then the step brings it into view).
export function targetRects(targets: Target[]): { list: Rect[]; owners: Element[]; missing: boolean; clipped: boolean } {
  const list: Rect[] = [];
  const owners: Element[] = [];
  let missing = false;
  let clipped = false;
  for (const t of targets) {
    const owner = t instanceof Element ? t : t?.within ?? null;
    const box = t instanceof Element ? t.getBoundingClientRect() : t?.rect ?? null;
    if (!owner || !box || !owner.isConnected || !(owner as HTMLElement).checkVisibility?.()) { missing = true; continue; }
    let r: Rect = box;
    if (r.right - r.left <= 0 || r.bottom - r.top <= 0) { missing = true; continue; }
    const full = { ...r };
    for (let p: Element | null = t instanceof Element ? owner.parentElement : owner; p && p !== document.body; p = p.parentElement) {
      const s = getComputedStyle(p);
      if (s.overflowX === 'visible' && s.overflowY === 'visible') continue;
      const c = p.getBoundingClientRect();
      r = { left: Math.max(r.left, c.left), top: Math.max(r.top, c.top), right: Math.min(r.right, c.right), bottom: Math.min(r.bottom, c.bottom) };
    }
    r = { left: Math.max(r.left, 0), top: Math.max(r.top, 0), right: Math.min(r.right, window.innerWidth), bottom: Math.min(r.bottom, window.innerHeight) };
    if (r.right - r.left < 4 || r.bottom - r.top < 4) { missing = true; continue; }
    if (r.bottom - r.top < full.bottom - full.top - 1 || r.right - r.left < full.right - full.left - 1) clipped = true;
    list.push({ left: r.left, top: r.top, right: r.right, bottom: r.bottom });
    owners.push(owner);
  }
  return { list, owners, missing, clipped };
}
