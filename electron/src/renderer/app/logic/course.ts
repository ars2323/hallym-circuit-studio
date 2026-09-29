/* The two courses and what each shows (A-08, D-168: the user's decision of
   2026-09-29, which replaced O-08's "one screen for both").

     논리설계 및 실험 (logic)      gates and wires, subcircuits, clocks and
                                 registers: no instruction or data memory,
                                 no register file, no program
     컴퓨터구조 (architecture)    everything

   The course is asked on the first screen every launch and never kept (the
   lab-PC rule: nothing persists, D-152); the title bar's chip switches it.
   Only what is on screen changes: the engine, the circuit, its simulation
   and what a file saves are the same in both.  COURSE_TABLE is the one
   place that decides, and it is screen-side only.

   Parts: of the bundled Hallym MIPS library only the MIPS-only parts hide
   in 논리설계 (MIPS_ONLY); Radix Probe is a general part (a value in hex,
   decimal and binary at once; the logic tutorial uses it) and shows in
   both.  A file that uses a MIPS-only part still draws and runs them in
   논리설계; a strip says so, with its one action (MIPS_NOTICE). */

import type { LibraryGroup } from '../../../main/protocol.ts';

export type Course = 'logic' | 'architecture';

export const COURSES: readonly Course[] = ['logic', 'architecture'];
export const COURSE_NAMES: Readonly<Record<Course, string>> = { logic: '논리설계 및 실험', architecture: '컴퓨터구조' };

export const MIPS_LIB = 'kr.ac.hallym.hcs.mips.MipsLibrary';
// The Hallym MIPS parts that belong to 컴퓨터구조 only (Stack: listed nowhere since D-140, named for old files).
export const MIPS_ONLY: readonly string[] = ['Instruction Memory', 'Data Memory', 'Console', 'Stack'];

// Everything the screen shows only in some courses: one row each, the courses that show it.
export type Feature =
  | 'loadProgram'        // the toolbar's Load Program…, the palette's command, a memory's Load Program… / Reload
  | 'cycleSide'          // the Cycle View's Registers | Memory | Instruction (the cycle table and waves stay)
  | 'markPc'             // Mark as PC (a register's menu, the Registers tab's rows)
  | 'markRegisterFile'   // Mark as Register File (a subcircuit's menu)
  | 'registerMapping'    // Register Mapping…
  | 'fieldColors'        // the instruction's field colours over the Canvas
  | 'statusPc'           // the status bar's PC
  | 'statusChanged'      // the status bar's Changed $t0, $sp (the registers the cycle changed, Hallym MIPS's 방금 바뀜)
  | 'statusProgram'      // the status bar's program facts
  | 'programNotices';    // the execution image's notices (a reload that failed, a program loaded again)

export const COURSE_TABLE: Readonly<Record<Feature, readonly Course[]>> = {
  loadProgram: ['architecture'],
  cycleSide: ['architecture'],
  markPc: ['architecture'],
  markRegisterFile: ['architecture'],
  registerMapping: ['architecture'],
  fieldColors: ['architecture'],
  statusPc: ['architecture'],
  statusChanged: ['architecture'],
  statusProgram: ['architecture'],
  programNotices: ['architecture'],
};

export const shows = (course: Course, feature: Feature): boolean => COURSE_TABLE[feature].includes(course);

// A part of a library: whether this course lists it (the Components list, Ctrl+K).
export const partShown = (course: Course, lib: string | null, name: string): boolean =>
  course === 'architecture' || lib !== MIPS_LIB || !MIPS_ONLY.includes(name);

// The engine's library tree (model.library) as this course lists it: the MIPS-only parts left out (a copy: the
// engine's answer is kept as it came).
export function visibleLibraries(libs: LibraryGroup[], course: Course): LibraryGroup[] {
  if (course === 'architecture') return libs;
  return libs
    .map((g) => (g.lib === MIPS_LIB ? { ...g, tools: g.tools.filter((t) => partShown(course, g.lib, t.name)) } : g))
    .filter((g) => g.lib !== MIPS_LIB || g.tools.length > 0);
}

// Whether parts (a circuit's, model.circuit) include a MIPS-only one.
export const usesMipsOnly = (parts: Iterable<{ lib: string | null; name: string }>): boolean => {
  for (const p of parts) if (p.lib === MIPS_LIB && MIPS_ONLY.includes(p.name)) return true;
  return false;
};

// A file opened before a course was chosen (named on the command line, Ctrl+O on the first screen): 컴퓨터구조 when it
// uses a MIPS-only part, else 논리설계 및 실험.  A new circuit before a course was chosen: 논리설계 및 실험.
export const inferredCourse = (mipsOnly: boolean): Course => (mipsOnly ? 'architecture' : 'logic');

// The strip over the work while the file on show uses a MIPS-only part in 논리설계 (D-135 point 14: the fact, then
// the action).
export const MIPS_NOTICE = { text: '이 파일은 컴퓨터구조 부품(Hallym MIPS)을 씁니다', action: '컴퓨터구조로 바꾸기' } as const;
export const mipsNoticeShown = (course: Course, mipsOnly: boolean): boolean => course === 'logic' && mipsOnly;

// Help › Examples: the course's own (the other course's after switching).
export interface ExampleEntry { id: string; name: string; course: Course }
export const examplesFor = <T extends { course: Course }>(list: readonly T[], course: Course): T[] => list.filter((x) => x.course === course);
