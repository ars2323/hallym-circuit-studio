/* Pictures and papers (N-21, D-162): File › Export Image… (SVG, PDF,
   high-resolution PNG; v1 ImageExport, E-07), File › Print… (the
   original's Print, I-134) and File › Create Submission… (v1 Submission,
   E-06).  The page draws the circuit as SVG from the renderer registry's
   shapes (canvas/svg.ts: the same definitions as the Canvas, D-137) and
   hands it here; the page never names a path (D-135), so the save dialog
   and the writing are here.

     picture:export     the SVG made self-contained -- its fonts
                        (Pretendard, D2Coding, SIL OFL: embedding allowed)
                        written into it as data -- so it looks the same on a
                        computer without them; then written as .svg, or
                        drawn by Chromium in a hidden window into a PDF
                        (vector, the page the picture's size) or a PNG (the
                        SVG drawn onto a canvas at 1-4 x)
     picture:print      the chosen circuits, one page each, in a hidden
                        window, then the system's print dialog (the
                        original's header %n %p %P %%, Rotate to Fit)
     submission:plan    the checks and the files (file.submission without a
                        path: nothing written)
     submission:write   the save dialog (<name>-submission.zip beside the
                        .circ), then the engine writes the zip there

   Nothing else is written anywhere (the lab-PC rule): the hidden window is
   in memory, and closed at once.  HCS_PRINT_TO_PDF (the e2e tests only)
   prints into that file instead of the printer. */

import type { BrowserWindow, Dialog, IpcMainInvokeEvent, WebContents } from 'electron';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

export type PictureFormat = 'svg' | 'png' | 'pdf';

// The faces the Canvas draws with (paint.ts FONTS; shared.css @font-face): file, family, weight.
export const FACES: readonly { file: string; family: 'Pretendard' | 'D2Coding'; weight: number }[] = [
  { file: 'Pretendard-Regular.subset.woff2', family: 'Pretendard', weight: 400 },
  { file: 'Pretendard-Medium.subset.woff2', family: 'Pretendard', weight: 500 },
  { file: 'Pretendard-SemiBold.subset.woff2', family: 'Pretendard', weight: 600 },
  { file: 'Pretendard-Bold.subset.woff2', family: 'Pretendard', weight: 700 },
  { file: 'D2Coding.woff2', family: 'D2Coding', weight: 400 },
];

// The faces a picture uses: a Pretendard weight when a text of that weight is in it (400 when none is
// named), D2Coding when a text asks for it.  Only those are embedded (D2Coding alone is 1.5 MB).
export function usedFaces(svg: string): typeof FACES[number][] {
  const out: typeof FACES[number][] = [];
  const texts = svg.match(/<text\b[^>]*>/g) ?? [];
  const weights = new Set<number>();
  let code = false;
  for (const t of texts) {
    const fam = /font-family="([^"]*)"/.exec(t)?.[1] ?? '';
    if (/D2Coding/.test(fam)) { code = true; continue; }
    weights.add(Number(/font-weight="(\d+)"/.exec(t)?.[1] ?? 400));
  }
  for (const f of FACES) {
    if (f.family === 'D2Coding' ? code : weights.has(f.weight)) out.push(f);
  }
  return out;
}

export function fontFaceCss(faces: readonly { family: string; weight: number; data: Buffer }[]): string {
  return faces.map((f) => `@font-face{font-family:${f.family};font-weight:${f.weight};src:url(data:font/woff2;base64,${f.data.toString('base64')}) format('woff2')}`).join('\n');
}

// The SVG with its fonts inside (a <style> first in the <svg>): the same picture wherever it is opened.
export function embedFonts(svg: string, css: string): string {
  if (!css) return svg;
  const open = /<svg\b[^>]*>/.exec(svg);
  if (!open) throw new Error('not an SVG picture');
  const at = open.index + open[0].length;
  return `${svg.slice(0, at)}\n<defs><style>${css}</style></defs>${svg.slice(at)}`;
}

// The picture's size in px (its width and height attributes, circuit units = CSS px at 100 %).
export function svgSize(svg: string): { width: number; height: number } {
  const open = /<svg\b[^>]*>/.exec(svg)?.[0] ?? '';
  const width = Number(/\bwidth="([\d.]+)"/.exec(open)?.[1]);
  const height = Number(/\bheight="([\d.]+)"/.exec(open)?.[1]);
  if (!(width > 0) || !(height > 0)) throw new Error('the picture has no size');
  return { width, height };
}

/* The PNG's scale: what was asked (1-4 x, v1), smaller when the picture would not fit in a canvas
   (Chromium: each side at most 16384 px; here at most 100 million pixels, about 400 MB while drawn). */
export const PNG_MAX_SIDE = 16384;
export const PNG_MAX_PIXELS = 100_000_000;
export function pngScale(width: number, height: number, asked: number): number {
  let s = Math.min(4, Math.max(1, Math.round(asked)));
  const fit = Math.min(PNG_MAX_SIDE / width, PNG_MAX_SIDE / height, Math.sqrt(PNG_MAX_PIXELS / (width * height)));
  if (s > fit) s = Math.max(0.1, Math.floor(fit * 100) / 100);
  return s;
}

const escHtml = (s: string): string => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// One picture as a page of its own size (the PDF: vector, nothing around it).
export function pdfHtml(svg: string): string {
  const { width, height } = svgSize(svg);
  return `<!doctype html><html><head><meta charset="utf-8"><style>@page{size:${width}px ${height}px;margin:0}html,body{margin:0;padding:0;background:#fff}svg{display:block}</style></head><body>${svg}</body></html>`;
}

/* The original's print header (Print.format): %n the circuit's name, %p the page, %P the pages,
   %% a percent sign; any other %x stays as it is. */
export function printHeader(template: string, name: string, page: number, pages: number): string {
  return template.replace(/%([npP%])/g, (_m, c: string) => (c === 'n' ? name : c === 'p' ? String(page) : c === 'P' ? String(pages) : '%'));
}

export interface PrintPage { svg: string; name: string }
export interface PrintOptions { header: string; rotate: boolean }

/* The pages: one circuit each, its header on top, the picture scaled down to the paper (never up,
   as the original's), turned a quarter when it is wider than tall and Rotate to Fit is on (the
   original turns it when that makes it larger). */
export function printHtml(pages: PrintPage[], o: PrintOptions, fontsCss: string): string {
  const body = pages.map((p, i) => {
    const { width, height } = svgSize(p.svg);
    const turn = o.rotate && width > height;
    const head = o.header.trim() ? `<div class="head">${escHtml(printHeader(o.header, p.name, i + 1, pages.length))}</div>` : '';
    const pic = turn
      ? `<div class="pic turn" style="--w:${width}px;--h:${height}px">${p.svg}</div>`
      : `<div class="pic" style="--w:${width}px;--h:${height}px">${p.svg}</div>`;
    return `<section class="page">${head}${pic}</section>`;
  }).join('\n');
  return `<!doctype html><html><head><meta charset="utf-8"><style>
${fontsCss}
@page{margin:12mm}
html,body{margin:0;padding:0;background:#fff}
.page{height:100vh;display:flex;flex-direction:column;break-after:page;overflow:hidden}
.page:last-child{break-after:auto}
.head{flex:none;text-align:center;font:500 10pt Pretendard,sans-serif;color:#1f2933;padding:0 0 4mm}
.pic{flex:1;min-height:0;position:relative;container-type:size}
.pic svg{position:absolute;left:50%;top:50%;width:min(var(--w),100cqw);height:min(var(--h),100cqh);transform:translate(-50%,-50%)}
.pic.turn svg{width:min(var(--w),100cqh);height:min(var(--h),100cqw);transform:translate(-50%,-50%) rotate(90deg)}
</style></head><body>${body}</body></html>`;
}

// ---- the hidden window ------------------------------------------------------------------------

export interface PicturesHost {
  dialog: Pick<Dialog, 'showSaveDialog'>;
  windowCall<T>(method: string, params: unknown): Promise<T>;
  openFiles: Map<string, string | null>;
  parent(e: IpcMainInvokeEvent): BrowserWindow;
  handle(channel: string, f: (e: IpcMainInvokeEvent, ...args: unknown[]) => unknown): void;
  fontsDir: string;
  // a window no one sees, for Chromium to draw the picture or the pages in (closed after)
  hiddenWindow(): BrowserWindow;
}

async function inHiddenPage<T>(h: PicturesHost, html: string, run: (wc: WebContents) => Promise<T>): Promise<T> {
  const w = h.hiddenWindow();
  try {
    await w.loadURL('about:blank');
    await w.webContents.executeJavaScript(`document.open();document.write(${JSON.stringify(html)});document.close();
      Promise.all([...document.fonts].map((f) => f.load().catch(() => null))).then(() => document.fonts.ready).then(() => true)`);
    return await run(w.webContents);
  } finally {
    if (!w.isDestroyed()) w.destroy();
  }
}

// The picture drawn onto a canvas at `scale` in the hidden page, as PNG bytes.
async function pngOf(h: PicturesHost, svg: string, scale: number): Promise<Buffer> {
  const { width, height } = svgSize(svg);
  const W = Math.max(1, Math.round(width * scale)), H = Math.max(1, Math.round(height * scale));
  const url = await inHiddenPage(h, '<!doctype html><html><body></body></html>', (wc) => wc.executeJavaScript(`(async () => {
    const img = new Image();
    img.src = URL.createObjectURL(new Blob([${JSON.stringify(svg)}], { type: 'image/svg+xml' }));
    await img.decode();
    const c = document.createElement('canvas');
    c.width = ${W}; c.height = ${H};
    const g = c.getContext('2d');
    g.fillStyle = '#ffffff'; g.fillRect(0, 0, ${W}, ${H});
    g.drawImage(img, 0, 0, ${W}, ${H});
    return c.toDataURL('image/png');
  })()`) as Promise<string>);
  return Buffer.from(url.slice(url.indexOf(',') + 1), 'base64');
}

async function pdfOf(h: PicturesHost, svg: string): Promise<Buffer> {
  return inHiddenPage(h, pdfHtml(svg), (wc) => wc.printToPDF({ preferCSSPageSize: true, printBackground: true, margins: { top: 0, bottom: 0, left: 0, right: 0 } }));
}

export function registerPictures(h: PicturesHost): void {
  const known = (fileId: unknown): string => {
    if (typeof fileId !== 'string' || !h.openFiles.has(fileId)) throw new Error(`no open file ${String(fileId)}`);
    return fileId;
  };
  const fontCache = new Map<string, Buffer>();
  const fontsFor = (svg: string): string => fontFaceCss(usedFaces(svg).map((f) => {
    let data = fontCache.get(f.file);
    if (!data) { data = readFileSync(path.join(h.fontsDir, f.file)); fontCache.set(f.file, data); }
    return { family: f.family, weight: f.weight, data };
  }));
  const folder = (fileId: string): string | undefined => { const p = h.openFiles.get(fileId); return p ? path.dirname(p) : undefined; };
  const FILTERS: Record<PictureFormat, { name: string; extensions: string[] }> = {
    svg: { name: 'SVG picture', extensions: ['svg'] },
    png: { name: 'PNG picture', extensions: ['png'] },
    pdf: { name: 'PDF document', extensions: ['pdf'] },
  };

  h.handle('picture:export', async (e, fileId: unknown, o: unknown) => {
    const id = known(fileId);
    const { format, svg, name, scale } = o as { format: PictureFormat; svg: string; name: string; scale?: number };
    if (!FILTERS[format] || typeof svg !== 'string' || typeof name !== 'string') throw new Error('bad picture');
    svgSize(svg);
    const dir = folder(id);
    const r = await h.dialog.showSaveDialog(h.parent(e), { title: 'Export Image', defaultPath: dir ? path.join(dir, name) : name, filters: [FILTERS[format]] });
    if (r.canceled || !r.filePath) return null;
    let target = r.filePath;
    if (!target.toLowerCase().endsWith(`.${format}`)) target += `.${format}`;
    const whole = embedFonts(svg, fontsFor(svg));
    let used = 1;
    let bytes: Buffer;
    if (format === 'svg') bytes = Buffer.from(whole, 'utf8');
    else if (format === 'pdf') bytes = await pdfOf(h, whole);
    else {
      const { width, height } = svgSize(svg);
      used = pngScale(width, height, scale ?? 2);
      bytes = await pngOf(h, whole, used);
    }
    writeFileSync(target, bytes);
    return { name: path.basename(target), bytes: bytes.length, scale: used };
  });

  h.handle('picture:print', async (_e, pages: unknown, o: unknown) => {
    if (!Array.isArray(pages) || pages.length === 0) throw new Error('nothing to print');
    const list = (pages as PrintPage[]).map((p) => ({ svg: String(p.svg), name: String(p.name) }));
    const opts = o as PrintOptions;
    const html = printHtml(list, { header: String(opts.header ?? ''), rotate: opts.rotate !== false }, fontsFor(`${list.map((p) => p.svg).join('\n')}<text font-weight="500">`));   // and the header's face
    return inHiddenPage(h, html, async (wc) => {
      const test = process.env.HCS_PRINT_TO_PDF;
      if (test) {
        writeFileSync(test, await wc.printToPDF({ printBackground: true }));
        return { printed: true, pages: list.length };
      }
      const printed = await new Promise<boolean>((done) => wc.print({ silent: false, printBackground: true }, (ok) => done(ok)));
      return { printed, pages: list.length };
    });
  });

  h.handle('submission:plan', (_e, fileId: unknown) => h.windowCall('file.submission', { fileId: known(fileId) }));
  h.handle('submission:write', async (e, fileId: unknown) => {
    const id = known(fileId);
    const plan = await h.windowCall<{ suggested: string; saved: boolean }>('file.submission', { fileId: id });
    if (!plan.saved) throw new Error('the file was never saved');
    const dir = folder(id);
    const r = await h.dialog.showSaveDialog(h.parent(e), { title: 'Create Submission', defaultPath: dir ? path.join(dir, plan.suggested) : plan.suggested, filters: [{ name: 'ZIP archive', extensions: ['zip'] }] });
    if (r.canceled || !r.filePath) return null;
    return h.windowCall('file.submission', { fileId: id, path: path.resolve(r.filePath) });
  });
}
