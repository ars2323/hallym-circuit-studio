/* What the window does for the two courses' tutorials (N-18, D-161): the
   facts the steps read, where things are on screen, what a step brings
   into view and what [건너뛰기] does.  app.ts makes it from its own state
   (tutorialHost()); the steps (logic-steps.ts, mips-steps.ts) see only
   this.  Every action is the window's own way of doing it -- the engine's
   intents, the same buttons' handlers -- so a skipped step leaves the
   circuit as the student's own action would. */

import type { Component, DiagMessage, ProgramInfo, RecordState, SimState, Snapshot, WindowMethod } from '../../../main/protocol.ts';
import type { Target, Tutorial, TutorialHost } from '../../shared/tutorial.ts';
import type { Box } from './facts.ts';

export type Track = 'logic' | 'architecture';

export interface CourseHost extends TutorialHost {
  readonly track: Track;
  // ---- facts (what the engine sent)
  fileId(): string | null;                          // the example's file (a copy of it: the example stays as it is)
  snapshot(circuit?: string): Snapshot | null;      // a circuit of the example as drawn (main when not named)
  shownCircuit(): { circuit: string; path: string[] } | null;   // the circuit on the Canvas and the instances gone into
  messages(): DiagMessage[] | null;
  chosenMessage(): string | null;                   // the message chosen last in Messages (its id)
  sim(): SimState | null;
  record(): RecordState | null;
  zoom(): number;
  held(): { lib: string | null; name: string } | null;   // the part in hand (the placing tool), or null
  tool(): string;                                   // Edit, Poke, Wire, Text, Place
  selected(): string[];                             // the parts chosen on the Canvas
  flow(): { running: boolean; from: string | null; ends: string[] };   // a Signal Flow on show: from which wire or part
  console(): { text: string; exited: boolean } | null;
  program(): ProgramInfo | null;
  value(componentId: string, port: number): string | undefined;   // the value on a part's port (the Canvas's)
  // ---- where things are on screen
  box(b: Box | null): Target;                       // a box of the circuit on show, on the Canvas
  shownExtent(): Box | null;                        // the whole circuit on show (inside an instance too)
  el(selector: string): Element | null;
  els(selector: string): Element[];
  // ---- bringing things into view
  showBottom(tab: 'Messages' | 'Cycle View' | 'Console'): boolean;   // true: it had to (and it did)
  showUpper(tab: 'Components' | 'Circuits'): boolean;
  clearPartSearch(): boolean;                       // the Components list's search emptied: the whole list (true: it had words)
  showCycleSide(tab: 'Registers' | 'Memory' | 'Instruction'): boolean;
  showMain(): boolean;                              // the example's main circuit, out of any instance
  fit(b: Box | null): void;                         // the Canvas's view on that box
  // ---- doing (the skips)
  call<T>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
  hold(lib: string | null, name: string): void;     // a part in hand, as the Components list gives it
  setTool(name: 'Edit' | 'Poke'): void;
  loadProgram(): Promise<boolean>;                  // tutorial.hmx beside the example (Load Program…'s own way, no dialog)
  cycles(n: number): Promise<void>;
  reset(): Promise<void>;                          // the toolbar's Reset
  run(on: boolean): Promise<void>;
  previousCycle(): Promise<void>;
  startFlow(wireId: string): Promise<void>;
  enter(componentId: string): void;
  chooseMessage(id: string): void;
  setZoom(z: number): void;
  setHz(hz: number): Promise<boolean>;              // the toolbar's clock speed, the engine's too (true: it changed)
  flowOnClick(on: boolean): boolean;                // Signal Flow on Click (the toolbar's switch; true: it changed)
}

export type CourseTutorial = Tutorial<CourseHost>;

// A part of the circuit on show by name (and label), for the steps' boxes.
export type Finder = (s: Snapshot | null) => Component | undefined;
