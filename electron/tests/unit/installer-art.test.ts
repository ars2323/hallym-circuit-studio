/* The installer's and uninstaller's side band (packaging/installerSidebar.bmp,
   uninstallerSidebar.bmp; tools/installer-art.py, D-155), read back from
   the committed files: NSIS's size and format, the app's navy, the
   university's symbol unaltered on a white plate -- its own colours, its
   own proportions (against the repository's original rendering of the same
   SVG) --, the program's name in white under it, no Korean. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { decodePng } from '../e2e/png.ts';

const root = path.join(import.meta.dirname, '..', '..');
const repo = path.join(root, '..');
const INSTALLER = path.join(root, 'packaging/installerSidebar.bmp');
const UNINSTALLER = path.join(root, 'packaging/uninstallerSidebar.bmp');
const SCRIPT = readFileSync(path.join(root, 'tools/installer-art.py'), 'utf8');

type RGB = [number, number, number];
interface Bmp { width: number; height: number; bits: number; at(x: number, y: number): RGB }
// A Windows BMP (BITMAPINFOHEADER, 24 bits, no compression), rows bottom-up and padded to 4 bytes.
function readBmp(file: string): Bmp {
  const b = readFileSync(file);
  assert.equal(b.toString('latin1', 0, 2), 'BM');
  const offset = b.readUInt32LE(10), header = b.readUInt32LE(14);
  const width = b.readInt32LE(18), height = b.readInt32LE(22), bits = b.readUInt16LE(28), compression = b.readUInt32LE(30);
  assert.equal(header, 40, 'BITMAPINFOHEADER');
  assert.equal(compression, 0, 'BI_RGB');
  const stride = Math.ceil((width * bits) / 32) * 4;
  return {
    width, height, bits,
    at: (x, y) => { const i = offset + (height - 1 - y) * stride + x * 3; return [b[i + 2], b[i + 1], b[i]]; },
  };
}
const near = (a: RGB, b: RGB, d: number) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]) <= d;
const isWhite = (c: RGB) => c[0] > 245 && c[1] > 245 && c[2] > 245;

const art = readBmp(INSTALLER);
// The plate, as the script places it (at 1x).
const [PX0, PY0, PX1, PY1] = [18, 40, 164 - 18, 112];

test('NSIS\'s side band: 164x314, 24-bit BMP; the uninstaller\'s is the same picture', () => {
  assert.deepEqual([art.width, art.height, art.bits], [164, 314, 24]);
  assert.deepEqual(readFileSync(UNINSTALLER), readFileSync(INSTALLER));
});

test('the app\'s navy (#00205B, a little lighter towards the bottom), not electron-builder\'s light blue', () => {
  for (const [x, y] of [[2, 2], [160, 2], [2, 311], [160, 311], [80, 20], [80, 250]]) {
    const c = art.at(x, y);
    assert.ok(c[0] < 20 && c[1] < 60 && c[2] > 80 && c[2] < 130, `(${x}, ${y}) rgb(${c}) is navy`);
  }
  assert.ok(near(art.at(80, 1), [0, 32, 91], 6), `top: rgb(${art.at(80, 1)})`);
  assert.ok(art.at(80, 312)[2] > art.at(80, 1)[2], 'lighter towards the bottom');
});

test('the university\'s symbol on a white plate: its own colours, its own proportions, clear space around it', () => {
  // The plate's inside (away from its rounded corners) is white but for the symbol.
  let blue = 0, teal = 0, other = 0;
  let x0 = Infinity, y0 = Infinity, x1 = -1, y1 = -1;
  for (let y = PY0 + 6; y < PY1 - 6; y++) {
    for (let x = PX0 + 6; x < PX1 - 6; x++) {
      const c = art.at(x, y);
      if (isWhite(c)) continue;
      x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
      // The symbol's two colours in symbol-basic.svg: rgb(28,99,183) and rgb(51,186,171); edges blend with white.
      if (c[2] > c[0] + 60 && c[2] > c[1] + 30) blue++;
      else if (c[1] > c[0] + 60 && Math.abs(c[1] - c[2]) < 40) teal++;
      else if (!(c[0] > 150 && c[1] > 150 && c[2] > 150)) other++;
    }
  }
  assert.ok(blue > 300 && teal > 300, `blue ${blue}, teal ${teal}`);
  assert.equal(other, 0, 'nothing but the symbol\'s colours (and their edges) on the plate');
  // Clear space: the symbol stays well inside the plate.
  assert.ok(x0 >= PX0 + 12 && x1 <= PX1 - 12 && y0 >= PY0 + 8 && y1 <= PY1 - 8, `symbol ${x0},${y0}..${x1},${y1}`);
  // Proportions: the same as the repository's own rendering of the same SVG (symbol-basic-64@2x.png).
  const png = decodePng(readFileSync(path.join(repo, 'assets/hallym/logo/symbol-basic-64@2x.png')));
  let a0 = Infinity, b0 = Infinity, a1 = -1, b1 = -1;
  for (let y = 0; y < png.height; y++) {
    for (let x = 0; x < png.width; x++) {
      if (png.rgba[(y * png.width + x) * 4 + 3] < 128) continue;
      a0 = Math.min(a0, x); b0 = Math.min(b0, y); a1 = Math.max(a1, x); b1 = Math.max(b1, y);
    }
  }
  const ours = (x1 - x0 + 1) / (y1 - y0 + 1), original = (a1 - a0 + 1) / (b1 - b0 + 1);
  assert.ok(Math.abs(ours / original - 1) < 0.06, `aspect ${ours.toFixed(3)} against the original's ${original.toFixed(3)}`);
  // The original file itself, byte for byte Hallym MIPS's (assets/MANIFEST.sha256 keeps it): the script reads that one.
  assert.match(SCRIPT, /SYMBOL = os\.path\.join\(REPO, 'assets\/hallym\/logo\/symbol-basic\.svg'\)/);
  assert.match(SCRIPT, /svg2png\(url=SYMBOL, output_width=sym_w\)/); // the width alone: its own height follows
});

test('the program\'s name in white under the plate (Pretendard, two lines), in English only', () => {
  let white = 0;
  const rows = new Set<number>();
  for (let y = PY1 + 10; y < PY1 + 70; y++) {
    for (let x = 10; x < 154; x++) if (isWhite(art.at(x, y))) { white++; rows.add(y); }
  }
  assert.ok(white > 150, `${white} white pixels`);
  // Two lines of text: two runs of rows with white in them.
  const sorted = [...rows].sort((a, b) => a - b);
  const runs = sorted.filter((y, i) => i === 0 || y - sorted[i - 1] > 1).length;
  assert.equal(runs, 2, `rows with text: ${sorted.join(',')}`);
  assert.match(SCRIPT, /^NAME = 'Hallym Circuit Studio'$/m);
  assert.match(SCRIPT, /^FONT = os\.path\.join\(REPO, 'assets\/fonts\/pretendard\/Pretendard-Bold\.otf'\)$/m);
  assert.doesNotMatch(SCRIPT.replace(/^"""[\s\S]*?"""/, ''), /[가-힣]/, 'no Korean drawn');
});
