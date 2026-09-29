/* The instruction fields' colours (D-167, docs/field-colors.md): the Cycle
   View's Instruction tab (shared/inspector.ts, shared/panels.css .f-*) and
   the Canvas's field colour bands (canvas/overlays/logic.ts FIELD_COLORS)
   against Hallym MIPS v2.6.0's Inspector -- electron/src/renderer/app/app.css
   lines 421-427 (the .f-* rules) with the tokens of lines 20-25, pinned here
   field by field, so neither side can drift from the other or from Hallym
   MIPS without this test saying which field. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { FIELD_COLORS, fieldColor } from '../../src/renderer/canvas/overlays/logic.ts';

const src = path.join(import.meta.dirname, '../../src/renderer/shared');
const css = readFileSync(path.join(src, 'panels.css'), 'utf8');
const shared = readFileSync(path.join(src, 'shared.css'), 'utf8');

// Hallym MIPS v2.6.0, app.css 20-25: the tokens the field rules use.
const TOKENS: Record<string, string> = {
  navy: '#00205b', blue: '#0055a5', 'blue-tint': '#e8f0f9', 'teal-tint': '#e6f6f5', 'teal-text': '#00736f',
  'amber-tint': '#fdf3e1', 'amber-text': '#8a5a00', purple: '#6b4c9a', 'cp0-tint': '#eef0f2', 'cp0-text': '#4a5560',
};

// Hallym MIPS v2.6.0, app.css 421-427: each field's background and text, the tokens resolved.
const FIELDS: Record<string, { background: string; color: string }> = {
  opcode: { background: '#dfe5ef', color: '#00205b' }, fmt: { background: '#dfe5ef', color: '#00205b' },
  rs: { background: '#e8f0f9', color: '#0055a5' },
  rt: { background: '#e6f6f5', color: '#00736f' }, ft: { background: '#e6f6f5', color: '#00736f' },
  rd: { background: '#fdf3e1', color: '#8a5a00' }, fs: { background: '#fdf3e1', color: '#8a5a00' },
  shamt: { background: '#efe9f6', color: '#6b4c9a' }, fd: { background: '#efe9f6', color: '#6b4c9a' },
  funct: { background: '#eef0f2', color: '#4a5560' },
  immediate: { background: '#e3eef0', color: '#1d5c63' }, target: { background: '#e3eef0', color: '#1d5c63' },
  offset: { background: '#e3eef0', color: '#1d5c63' },
};

// Our tokens (shared.css, panels.css :root).
const vars = new Map([...(shared + css).matchAll(/--([a-z0-9-]+):\s*(#[0-9a-f]{6})\b/gi)].map((m) => [m[1], m[2].toLowerCase()]));
const resolve = (v: string) => (v.startsWith('var(') ? vars.get(v.slice(6, -1).trim()) : v.toLowerCase());

// Our .f-* rules: selector list -> { background, color }, per field name.
function ourRules(): Map<string, { background?: string; color?: string }> {
  const out = new Map<string, { background?: string; color?: string }>();
  for (const m of css.matchAll(/((?:\.f-[a-z0-9]+(?:,\s*)?)+)\s*\{([^}]*)\}/gi)) {
    const decl: { background?: string; color?: string } = {};
    for (const d of m[2].split(';')) {
      const [k, ...v] = d.split(':');
      if (!k || !v.length) continue;
      if (k.trim() === 'background') decl.background = resolve(v.join(':').trim());
      if (k.trim() === 'color') decl.color = resolve(v.join(':').trim());
    }
    for (const sel of m[1].split(',')) out.set(sel.trim().slice(3), decl);
  }
  return out;
}

test('the tokens the field colours use are Hallym MIPS 2.6.0\'s', () => {
  for (const [name, value] of Object.entries(TOKENS)) assert.equal(vars.get(name), value, `--${name}`);
});

test('each field\'s colours in the Instruction tab are Hallym MIPS 2.6.0 Inspector\'s', () => {
  const ours = ourRules();
  assert.deepEqual([...ours.keys()].sort(), Object.keys(FIELDS).sort(), 'the same fields have a rule');
  for (const [field, want] of Object.entries(FIELDS)) assert.deepEqual(ours.get(field), want, `.f-${field}`);
});

test('the Canvas\'s band of each field is that field\'s text colour; the fields without a rule are grey', () => {
  for (const [field, want] of Object.entries(FIELDS)) assert.equal(fieldColor(field), want.color, field);
  assert.deepEqual(Object.keys(FIELD_COLORS).sort(), Object.keys(FIELDS).sort());
  // CP0's and the FI format's other fields (CO, code, sel, 0, cc, nd, tf) have no .f-* rule in Hallym MIPS either:
  // there they take the panel's colours; the band takes funct's grey.
  for (const f of ['CO', 'code', 'sel', '0', 'cc', 'nd', 'tf']) assert.equal(fieldColor(f), '#4a5560', f);
});

test('there is one theme: no dark variant of the field colours on either side', () => {
  // Hallym MIPS v2.6.0's app.css has no prefers-color-scheme or theme switch; neither has this app.
  for (const [name, text] of [['panels.css', css], ['shared.css', shared]]) {
    assert.ok(!/prefers-color-scheme|data-theme/.test(text), name);
  }
});
