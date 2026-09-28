/* The Canvas's colours and sizes (N-05, D-137): Hallym MIPS's tokens
   (shared.css --navy, --blue, --teal, --text …) for the drawing, and the
   Canvas's own -- the value colours -- defined once here and in canvas.css
   under the same names (--v-one …).  The legend (legend.ts) shows the CSS
   ones and the Canvas draws these; a test holds the two equal, and another
   compares the legend's swatches with pixels of drawn wires.

   The value colours keep Logisim's meaning, so a Logisim user reads them as
   before: 1 bright green, 0 dark green, x (floating, not decided) blue,
   E (error: two outputs disagree) red, several bits (a bus) dark, and
   a width mismatch orange. */

export const THEME = {
  // Hallym MIPS (shared.css)
  navy: '#00205b',
  blue: '#0055a5',
  teal: '#00a9a5',
  ink: '#1f2933',          // --text
  ink2: '#5a6472',         // --text-2
  muted: '#65707e',        // --muted
  dim: '#a9b1bb',          // --dim
  border: '#e1e5ea',       // --border
  window: '#f5f7fa',       // --window
  white: '#ffffff',
  blueTint: '#e8f0f9',     // --blue-tint
  blueTint2: '#d3e2f3',    // --blue-tint2
  tealTint: '#e6f6f5',     // --teal-tint
  tealText: '#00736f',     // --teal-text
  error: '#c0392b',        // --error
  errorTint: '#fbeae8',    // --error-tint
  amberText: '#8a5a00',    // --amber-text
  // the Canvas's own
  paper: '#ffffff',        // the Canvas's background
  grid: '#d3d9e1',         // grid dots
  gridMajor: '#b9c2cd',    // every fifth dot
  body: '#ffffff',         // inside a part
  bodyStroke: '#2b3743',   // a part's outline
  bodySoft: '#f4f6f9',     // inside a part's inner panels (memory tables, the console)
  chip: '#ffffff',         // label chips
  chipStroke: '#c9d1db',
  select: '#0055a5',       // selection (--blue)
  selectTint: '#d3e2f3',   // --blue-tint2
  hover: '#00a9a5',        // hover (--teal)
  focus: '#00a9a5',
  // value colours (canvas.css --v-*)
  vOne: '#22b14c',
  vZero: '#1d6b3a',
  vFloat: '#2f66d0',
  vError: '#c0392b',
  vBus: '#1f3b63',
  vWidth: '#e08a00',
  vNone: '#6b7785',        // no value yet (the engine has not sent one)
} as const;

export type Token = keyof typeof THEME;
export type Theme = Record<Token, string>;

// The CSS custom property of each value colour (canvas.css, the legend).
export const VALUE_VARS: Record<'vOne' | 'vZero' | 'vFloat' | 'vError' | 'vBus' | 'vWidth' | 'vNone', string> = {
  vOne: '--v-one', vZero: '--v-zero', vFloat: '--v-float', vError: '--v-error', vBus: '--v-bus', vWidth: '--v-width', vNone: '--v-none',
};

// A value's kind (docs/engine-api.md 4: high bit first, '0' '1' 'x' 'E').
export type ValueKind = 'one' | 'zero' | 'float' | 'error' | 'bus' | 'none';
export function valueKind(v: string | undefined | null): ValueKind {
  if (!v) return 'none';
  if (v.includes('E')) return 'error';
  if (v.length === 1) return v === '1' ? 'one' : v === '0' ? 'zero' : 'float';
  if (!/[01]/.test(v)) return 'float';     // every bit floating
  return 'bus';                            // several bits (some may be x: the bus colour, as Logisim)
}
export const KIND_TOKEN: Record<ValueKind, Token> = { one: 'vOne', zero: 'vZero', float: 'vFloat', error: 'vError', bus: 'vBus', none: 'vNone' };
export const valueColor = (theme: Theme, v: string | undefined | null): string => theme[KIND_TOKEN[valueKind(v)]];

// The legend's rows: the kinds a wire can show, in reading order.  The name is the value's own mark;
// what it means is an explanation, in Korean (v1 messages_ko legend.*, GLOSSARY: sentences are Korean).
export const LEGEND: { token: Token; name: string; say: string }[] = [
  { token: 'vOne', name: '1', say: '값 1' },
  { token: 'vZero', name: '0', say: '값 0' },
  { token: 'vFloat', name: 'x', say: '떠 있음(값을 내는 곳이 없음)' },
  { token: 'vError', name: 'E', say: '오류(서로 다른 값이 부딪힘)' },
  { token: 'vBus', name: 'bus', say: '여러 비트(버스)' },
  { token: 'vWidth', name: 'width', say: '비트 폭이 맞지 않음' },
];

// The tunnel palette (v1 TunnelColors, #79): Okabe–Ito and Paul Tol
// "muted", told apart with colour-vision deficiencies too; the name is
// always written, so colour alone never carries the meaning.
export const TUNNEL_PALETTE = [
  '#e69f00', '#56b4e9', '#009e73', '#0072b2', '#d55e00', '#cc79a7',
  '#332288', '#117733', '#999933', '#882255', '#44aa99', '#aa4499',
] as const;

// The theme the page's CSS says (the same names as THEME, as --canvas-<name>
// and the value colours as --v-*), THEME where the CSS says nothing.
export function themeFrom(read: (cssVar: string) => string): Theme {
  const t = { ...THEME } as Theme;
  for (const [k, v] of Object.entries(VALUE_VARS)) {
    const got = read(v).trim();
    if (got) t[k as Token] = got;
  }
  return t;
}
