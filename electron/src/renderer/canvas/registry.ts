/* The part renderer registry (N-05, D-137; the user's decision, v2 addendum
   item 3): ONE table from a part's kind -- its library and name, exactly
   as the engine sends them -- to a vector drawing of it (shapes.ts) made
   from the engine's attributes, bounds and ports.  The Canvas draws these
   drawings (paint.ts), picture export writes the same ones (svg.ts), and
   the geometry check (N-06) holds them on the engine's ports and bounds.
   The future Verilog mapping table (PLAN.md 7.0) attaches to this table's
   rows: one row per kind.

   A row also says how the part's surroundings are drawn (labels.ts):
     portNames  hover   names outside the part on hover or from 200 % (v1 S-06, S-08)
                drawn   the body writes them itself (Hallym MIPS parts, S-23)
                none    no names (gates, wiring)
     label      chip    the Label attribute in a chip beside the part (v1 S-01, S-05, S-12)
                own     the part shows its label itself (a tunnel's name is its body)
                none    the part has no label
     valueChip  a narrow part's value, in a chip beside it (v1 S-07)
   `course` marks the parts the courses use (the v2 brief's list); a row
   without it imitates the original look (docs/canvas-renderers.md). */

import type { Shape } from './shapes.ts';
import { drawArithmetic } from './parts/arithmetic.ts';
import { drawText } from './parts/base.ts';
import type { Part, PartState } from './parts/common.ts';
import { drawFallback } from './parts/fallback.ts';
import { drawBuffer, drawControlledBuffer, drawControlledInverter, drawGate, drawNot, GATE_NAMES } from './parts/gates.ts';
import { drawButton, drawHexDigit, drawLed, drawSevenSegment } from './parts/io.ts';
import { drawFlipFlop, drawMemory, drawRegisterLike, drawShiftRegister, registerChip } from './parts/memory.ts';
import { drawConsole, drawMipsMemory, drawRadixProbe } from './parts/mips.ts';
import { drawBitSelector, drawDecoder, drawDemux, drawMux, drawPriorityEncoder } from './parts/plexers.ts';
import { drawSubcircuit } from './parts/subcircuit.ts';
import { drawBitExtender, drawClock, drawConstant, drawGround, drawPin, drawPower, drawProbe, drawPull, drawSplitter, drawTunnel } from './parts/wiring.ts';

export const MIPS_LIB = 'kr.ac.hallym.hcs.mips.MipsLibrary';
export const CIRCUIT = 'circuit';   // a subcircuit instance (of this file's circuits or a .circ library's)

export interface Renderer {
  lib: string;                      // the engine's library name (CIRCUIT for subcircuits)
  name: string;                     // the engine's component name ('*' for a subcircuit)
  draw(p: Part, st: PartState): Shape[];
  portNames: 'hover' | 'drawn' | 'none';
  label: 'chip' | 'own' | 'none';
  valueChip?: (p: Part, st: PartState) => { text: string; value?: string } | null;
  course: boolean;
}

type Row = Omit<Renderer, 'portNames' | 'label' | 'course'> & Partial<Pick<Renderer, 'portNames' | 'label' | 'course'>>;
const row = (r: Row): Renderer => ({ portNames: 'hover', label: 'chip', course: true, ...r });

export const REGISTRY: Renderer[] = [
  // Gates
  ...GATE_NAMES.map((name) => row({ lib: 'Gates', name, draw: drawGate, portNames: 'none', course: !name.includes('Parity') })),
  row({ lib: 'Gates', name: 'NOT Gate', draw: (p, s) => drawNot(p, s), portNames: 'none' }),
  row({ lib: 'Gates', name: 'Buffer', draw: drawBuffer, portNames: 'none' }),
  row({ lib: 'Gates', name: 'Controlled Buffer', draw: drawControlledBuffer, portNames: 'none' }),
  row({ lib: 'Gates', name: 'Controlled Inverter', draw: drawControlledInverter, portNames: 'none', course: false }),
  // Plexers
  row({ lib: 'Plexers', name: 'Multiplexer', draw: drawMux, label: 'none' }),
  row({ lib: 'Plexers', name: 'Demultiplexer', draw: (p, s) => drawDemux(p, s), label: 'none' }),
  row({ lib: 'Plexers', name: 'Decoder', draw: drawDecoder, label: 'none' }),
  row({ lib: 'Plexers', name: 'Priority Encoder', draw: drawPriorityEncoder, label: 'none' }),
  row({ lib: 'Plexers', name: 'BitSelector', draw: drawBitSelector, label: 'none' }),
  // Wiring
  row({ lib: 'Wiring', name: 'Splitter', draw: drawSplitter, portNames: 'none', label: 'none' }),
  row({ lib: 'Wiring', name: 'Pin', draw: drawPin, portNames: 'none' }),
  row({ lib: 'Wiring', name: 'Probe', draw: drawProbe, portNames: 'none' }),
  row({ lib: 'Wiring', name: 'Tunnel', draw: drawTunnel, portNames: 'none', label: 'own' }),
  row({ lib: 'Wiring', name: 'Clock', draw: drawClock, portNames: 'none' }),
  row({ lib: 'Wiring', name: 'Constant', draw: drawConstant, portNames: 'none', label: 'none' }),
  row({ lib: 'Wiring', name: 'Pull Resistor', draw: drawPull, portNames: 'none', label: 'none' }),
  row({ lib: 'Wiring', name: 'Ground', draw: drawGround, portNames: 'none', label: 'none' }),
  row({ lib: 'Wiring', name: 'Power', draw: drawPower, portNames: 'none', label: 'none' }),
  row({ lib: 'Wiring', name: 'Bit Extender', draw: drawBitExtender, label: 'none', course: false }),
  // Arithmetic
  ...['Adder', 'Subtractor', 'Multiplier', 'Divider', 'Negator', 'Comparator', 'Shifter', 'BitAdder', 'BitFinder']
    .map((name) => row({ lib: 'Arithmetic', name, draw: drawArithmetic, label: 'none' })),
  // Memory
  ...['D Flip-Flop', 'T Flip-Flop', 'J-K Flip-Flop', 'S-R Flip-Flop'].map((name) => row({ lib: 'Memory', name, draw: drawFlipFlop })),
  ...['Register', 'Counter', 'Random'].map((name) => row({ lib: 'Memory', name, draw: drawRegisterLike, valueChip: registerChip })),
  row({ lib: 'Memory', name: 'Shift Register', draw: drawShiftRegister }),
  row({ lib: 'Memory', name: 'RAM', draw: drawMemory, label: 'none' }),
  row({ lib: 'Memory', name: 'ROM', draw: drawMemory, label: 'none' }),
  // I/O
  row({ lib: 'I/O', name: 'Button', draw: drawButton, portNames: 'none' }),
  row({ lib: 'I/O', name: 'LED', draw: drawLed, portNames: 'none' }),
  row({ lib: 'I/O', name: '7-Segment Display', draw: drawSevenSegment, label: 'none' }),
  row({ lib: 'I/O', name: 'Hex Digit Display', draw: drawHexDigit, label: 'none' }),
  // Base
  row({ lib: 'Base', name: 'Text', draw: drawText, portNames: 'none', label: 'none' }),
  // Hallym MIPS (lib-mips); the old Stack only in old circuits (D-140)
  row({ lib: MIPS_LIB, name: 'Instruction Memory', draw: drawMipsMemory, portNames: 'drawn' }),
  row({ lib: MIPS_LIB, name: 'Data Memory', draw: drawMipsMemory, portNames: 'drawn' }),
  row({ lib: MIPS_LIB, name: 'Stack', draw: drawMipsMemory, portNames: 'drawn' }),
  row({ lib: MIPS_LIB, name: 'Console', draw: drawConsole, portNames: 'drawn' }),
  row({ lib: MIPS_LIB, name: 'Radix Probe', draw: drawRadixProbe, portNames: 'none' }),
  // Subcircuits: their appearance (default or the student's)
  row({ lib: CIRCUIT, name: '*', draw: drawSubcircuit }),
];

// The parts of Logisim 2.7.1 drawn by the default renderer (docs/canvas-renderers.md holds the same list).
export const FALLBACK_KINDS: string[] = [
  'Wiring/Transistor', 'Wiring/Transmission Gate', 'I/O/Joystick', 'I/O/Keyboard', 'I/O/DotMatrix', 'I/O/TTY',
];

export const FALLBACK: Renderer = { lib: '*', name: '*', draw: drawFallback, portNames: 'hover', label: 'chip', course: false };

const byKind = new Map(REGISTRY.map((r) => [`${r.lib}/${r.name}`, r]));
export const kindOf = (p: Pick<Part, 'lib' | 'name' | 'appearance' | 'subcircuit'>): string =>
  p.appearance || p.subcircuit !== undefined ? `${CIRCUIT}/*` : `${p.lib ?? CIRCUIT}/${p.name}`;
export const rendererFor = (p: Pick<Part, 'lib' | 'name' | 'appearance' | 'subcircuit'>): Renderer => byKind.get(kindOf(p)) ?? FALLBACK;
