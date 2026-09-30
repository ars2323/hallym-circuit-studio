/* A translation changed words only (D-174, "Repository in English").

     node tools/check-translation.ts <base-ref> [files...] [--literals <file>] [--pair old=new]...

   Compares the working tree with <base-ref> (a commit, branch or tag).  With no files, every file changed since
   <base-ref> that this tool reads (.java .ts .kts, .md .txt).  Paths are from the repository's root.

   Code (.java, .ts, .kts): the file's tokens without comments must be the same as at <base-ref> (tools/lexers.ts;
   line numbers and whitespace do not matter).  A changed string literal is listed (file, old, new) and fails,
   unless its new value is in --literals <file>: one literal a line, as written in the source (with its quotes) or
   its text between the quotes.  Only a commit that means to change developer messages passes --literals, and the
   PR lists them.

   Docs (.md, .txt): the original at <base-ref> and the translation in the working tree.  A moved or renamed doc is
   named with --pair old=new (old at <base-ref>, new in the working tree).  These must be the same:
     code blocks          every fenced code block, byte for byte, in order
     inline code          every `code span`, byte for byte (as a multiset: a sentence may put them in another order)
     links                every link and picture target; an #anchor made of Korean may change (check-doc-links.ts
                          checks it still points at a heading)
     IDs                  D-012, N-20, A-07, I-189 ... (\b[A-Z]{1,2}-\d{2,3}[a-z]?\b), as a multiset
     #numbers             #473 ..., as a multiset
     hashes               hex strings of 7 or more characters holding a digit, as a multiset
     headings             their number and levels, in order
     tables               the number of rows of each table, in order
     list items           the number of items of each list, in order
     numbers              every number of the original is still there (numbers may be added: 세 → 3)
   Then prints how many lines of each file still hold Korean (as check-korean.ts counts them: comments in code).

   Exits 1 on any difference, with what differs for each rule. */

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { koreanLines } from './check-korean.ts';
import { HANGUL, languageOf, lex, type Token } from './lexers.ts';

export const repo = path.join(import.meta.dirname, '..', '..');

const CODE = /\.(java|ts|kts)$/i;
const DOC = /\.(md|txt)$/i;

// ---------------------------------------------------------------- code

export interface CodeResult { problems: string[]; literals: { old: string; new: string }[] }

const unquote = (s: string): string => {
  for (const q of ['"""', '"', '\'', '`']) if (s.length >= 2 * q.length && s.startsWith(q) && s.endsWith(q)) return s.slice(q.length, -q.length);
  return s;
};

export function compareCode(file: string, oldText: string, newText: string, allowed: ReadonlySet<string> = new Set()): CodeResult {
  const lang = languageOf(file);
  if (!lang) return { problems: [`${file}: not a code file this tool reads`], literals: [] };
  const strip = (ts: Token[]) => ts.filter((t) => t.kind !== 'comment');
  const a = strip(lex(lang, oldText));
  const b = strip(lex(lang, newText));
  const problems: string[] = [];
  const literals: { old: string; new: string }[] = [];
  // The code with every string literal as a placeholder must be the same; then the literals are compared in place.
  const shape = (t: Token) => (t.kind === 'string' ? '\u0000string' : `${t.kind}:${t.text}`);
  const around = (ts: Token[], k: number) => ts.slice(Math.max(0, k - 4), k + 5).map((t) => t.text).join(' ');
  const n = Math.min(a.length, b.length);
  let i = 0;
  while (i < n && shape(a[i]) === shape(b[i])) i += 1;
  if (i < n) {
    problems.push(`${file}: the code differs at token ${i + 1} (line ${a[i].line} before, line ${b[i].line} now)\n    before: ${around(a, i)}\n    now:    ${around(b, i)}`);
    return { problems, literals };
  }
  if (a.length !== b.length) {
    const rest = (a.length > b.length ? a : b).slice(i, i + 8).map((t) => t.text).join(' ');
    problems.push(`${file}: the code has ${a.length} tokens before and ${b.length} now; ${a.length > b.length ? 'missing' : 'added'} from token ${i + 1}: ${rest}`);
    return { problems, literals };
  }
  for (let k = 0; k < n; k += 1) {
    const x = a[k];
    const y = b[k];
    if (x.kind !== 'string' || x.text === y.text) continue;
    literals.push({ old: x.text, new: y.text });
    if (!allowed.has(y.text) && !allowed.has(unquote(y.text))) {
      problems.push(`${file}:${y.line}: string literal changed (not in --literals)\n    old (line ${x.line}): ${x.text}\n    new: ${y.text}`);
    }
  }
  return { problems, literals };
}

// ---------------------------------------------------------------- docs

export interface DocFacts {
  codeBlocks: string[];
  inlineCode: string[];
  links: string[];
  ids: string[];
  issues: string[];
  hashes: string[];
  headings: number[];
  tables: number[];
  lists: number[];
  numbers: string[];
}

export const ID = /\b[A-Z]{1,2}-\d{2,3}[a-z]?\b/g;

// A link target, its #anchor decoded.
const target = (t: string): string => {
  const k = t.indexOf('#');
  if (k < 0) return t;
  try { return `${t.slice(0, k)}#${decodeURIComponent(t.slice(k + 1))}`; } catch { return t; }
};
const pathOf = (t: string): string => (t.includes('#') ? t.slice(0, t.indexOf('#')) : t);
const koreanAnchor = (t: string): boolean => t.includes('#') && HANGUL.test(t.slice(t.indexOf('#')));

export function docFacts(text: string): DocFacts {
  const lines = text.replace(/\r\n/g, '\n').split('\n');
  const codeBlocks: string[] = [];
  const prose: string[] = [];          // lines outside fenced blocks
  const headings: number[] = [];
  const tables: number[] = [];
  const lists: number[] = [];
  let fence: { mark: string; start: number } | undefined;
  let table = 0;
  let list = 0;
  let blankInList = false;
  const endTable = () => { if (table) tables.push(table); table = 0; };
  const endList = () => { if (list) lists.push(list); list = 0; blankInList = false; };
  lines.forEach((line, n) => {
    if (fence) {
      if (line.trimStart().startsWith(fence.mark) && line.trim().replace(/[`~]/g, '') === '') {
        codeBlocks.push(lines.slice(fence.start, n + 1).join('\n'));
        fence = undefined;
      }
      return;
    }
    const open = /^\s*(`{3,}|~{3,})/.exec(line);
    if (open) { endTable(); fence = { mark: open[1], start: n }; return; }
    prose.push(line);
    const heading = /^(#{1,6})\s/.exec(line);
    if (heading) { endTable(); endList(); headings.push(heading[1].length); return; }
    if (/^\s*\|/.test(line)) { table += 1; return; }
    endTable();
    if (/^\s*([-*+]|\d+[.)])\s+/.test(line)) { list += 1; blankInList = false; return; }
    if (line.trim() === '') { if (list) blankInList = true; return; }
    // A line in a list: indented, or right after an item (a lazy continuation); a new paragraph ends it.
    if (list && (/^\s+/.test(line) || !blankInList)) return;
    endList();
  });
  if (fence) codeBlocks.push(lines.slice(fence.start).join('\n'));
  endTable();
  endList();

  let body = prose.join('\n');
  const inlineCode: string[] = [];
  body = body.replace(/(`+)([^`]|[^`][\s\S]*?[^`])\1(?!`)/g, (m) => { inlineCode.push(m); return ' '; });
  const links: string[] = [];
  body = body.replace(/(!?\[[^\]]*\])\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)/g, (_m, label: string, t: string) => { links.push(target(t)); return `${label} `; });
  body = body.replace(/<img[^>]+src="([^"]+)"[^>]*>/g, (_m, t: string) => { links.push(target(t)); return ' '; });
  body = body.replace(/^\s*\[[^\]]+\]:\s*(\S+).*$/gm, (_m, t: string) => { links.push(target(t)); return ' '; });
  const ids = body.match(ID) ?? [];
  const issues = body.match(/(?<![\w&])#\d+\b/g) ?? [];
  const hashes = body.match(/\b(?=[0-9a-f]*\d)(?=[0-9a-f]*[a-f])[0-9a-f]{7,64}\b/g) ?? [];
  const numbers = body.match(/\d+(?:\.\d+)*/g) ?? [];
  return { codeBlocks, inlineCode, links, ids, issues, hashes, headings, tables, lists, numbers };
}

const count = (xs: readonly string[]) => {
  const m = new Map<string, number>();
  for (const x of xs) m.set(x, (m.get(x) ?? 0) + 1);
  return m;
};

// What a multiset lost and gained.
function multisetDiff(a: readonly string[], b: readonly string[]): { missing: string[]; added: string[] } {
  const ca = count(a);
  const cb = count(b);
  const missing: string[] = [];
  const added: string[] = [];
  for (const [k, n] of ca) for (let j = cb.get(k) ?? 0; j < n; j += 1) missing.push(k);
  for (const [k, n] of cb) for (let j = ca.get(k) ?? 0; j < n; j += 1) added.push(k);
  return { missing, added };
}

const show = (s: string) => (s.length > 120 ? `${JSON.stringify(s.slice(0, 117))}…` : JSON.stringify(s));

export function compareDoc(label: string, oldText: string, newText: string): string[] {
  const a = docFacts(oldText);
  const b = docFacts(newText);
  const problems: string[] = [];
  // In order: code blocks, headings, tables, lists.
  if (a.codeBlocks.length !== b.codeBlocks.length) problems.push(`code blocks: ${a.codeBlocks.length} before, ${b.codeBlocks.length} now`);
  a.codeBlocks.forEach((blk, k) => {
    if (k < b.codeBlocks.length && blk !== b.codeBlocks[k]) problems.push(`code block ${k + 1} changed\n      before: ${show(blk)}\n      now:    ${show(b.codeBlocks[k])}`);
  });
  const seq = (name: string, x: number[], y: number[]) => {
    if (x.join(',') === y.join(',')) return;
    const k = x.findIndex((v, j) => v !== y[j]);
    const at = k < 0 ? Math.min(x.length, y.length) : k;
    problems.push(`${name}: ${x.length} before, ${y.length} now; first difference at ${name.replace(/s \(.*$|s$/, '')} ${at + 1}: ${x[at] ?? 'none'} before, ${y[at] ?? 'none'} now`);
  };
  seq('headings (levels)', a.headings, b.headings);
  seq('tables (rows)', a.tables, b.tables);
  seq('lists (items)', a.lists, b.lists);
  // As multisets: inline code, links, IDs, #numbers, hashes.
  const multi = (name: string, x: string[], y: string[]) => {
    const d = multisetDiff(x, y);
    if (d.missing.length) problems.push(`${name} missing: ${d.missing.map(show).join(', ')}`);
    if (d.added.length) problems.push(`${name} added: ${d.added.map(show).join(', ')}`);
  };
  multi('inline code', a.inlineCode, b.inlineCode);
  // Link targets: the same files; an anchor stays unless it was made of Korean words (it follows the heading).
  multi('link targets', a.links.map(pathOf), b.links.map(pathOf));
  const kept = multisetDiff(a.links.filter((t) => t.includes('#') && !koreanAnchor(t)), b.links);
  if (kept.missing.length) problems.push(`link anchors missing: ${kept.missing.map(show).join(', ')}`);
  multi('IDs', a.ids, b.ids);
  multi('#numbers', a.issues, b.issues);
  multi('hashes', a.hashes, b.hashes);
  // Numbers: every one still there.
  const now = new Set(b.numbers);
  const lost = [...new Set(a.numbers)].filter((n) => !now.has(n));
  if (lost.length) problems.push(`numbers missing: ${lost.join(', ')}`);
  return problems.map((p) => `${label}: ${p}`);
}

// ---------------------------------------------------------------- the command

function git(args: string[]): string {
  return execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
}
const atRef = (ref: string, file: string): string | undefined => {
  try { return git(['show', `${ref}:${file}`]); } catch { return undefined; }
};

export function main(argv: string[]): number {
  const pairs = new Map<string, string>();   // new -> old
  let literalsFile: string | undefined;
  const rest: string[] = [];
  for (let k = 0; k < argv.length; k += 1) {
    if (argv[k] === '--pair') {
      const [o, n] = (argv[++k] ?? '').split('=');
      if (!o || !n) { console.error('--pair old=new'); return 2; }
      pairs.set(n, o);
    } else if (argv[k] === '--literals') literalsFile = argv[++k];
    else rest.push(argv[k]);
  }
  const [base, ...given] = rest;
  if (!base) { console.error('usage: node tools/check-translation.ts <base-ref> [files...] [--literals <file>] [--pair old=new]...'); return 2; }
  const allowed = new Set(literalsFile ? readFileSync(literalsFile, 'utf8').split('\n').map((l) => l.replace(/\r$/, '')).filter(Boolean) : []);
  const files = given.length ? given : [...new Set([...git(['diff', '--name-only', '--no-renames', base]).split('\n').filter(Boolean), ...pairs.keys()])];
  const problems: string[] = [];
  const allLiterals: { file: string; old: string; new: string }[] = [];
  const korean: [string, number][] = [];
  let checked = 0;
  for (const f of files) {
    if (!CODE.test(f) && !DOC.test(f)) continue;
    const abs = path.join(repo, f);
    if (!existsSync(abs)) continue;   // deleted, or the old side of a --pair
    const oldPath = pairs.get(f) ?? f;
    const before = atRef(base, oldPath);
    if (before === undefined) { if (pairs.has(f)) problems.push(`${oldPath}: not at ${base}`); continue; }   // a new file
    const now = readFileSync(abs, 'utf8');
    checked += 1;
    if (CODE.test(f)) {
      const r = compareCode(f, before, now, allowed);
      problems.push(...r.problems);
      allLiterals.push(...r.literals.map((l) => ({ file: f, ...l })));
    } else {
      problems.push(...compareDoc(oldPath === f ? f : `${oldPath} -> ${f}`, before, now));
    }
    korean.push([f, koreanLines(f, now)]);
  }
  if (allLiterals.length) {
    console.log('changed string literals:');
    for (const l of allLiterals) console.log(`  ${l.file}\n    old: ${l.old}\n    new: ${l.new}`);
  }
  console.log(`checked ${checked} files against ${base}`);
  for (const [f, n] of korean) console.log(`  ${String(n).padStart(5)} lines with Korean  ${f}`);
  if (problems.length) {
    console.log(`\n${problems.length} differences:`);
    for (const p of problems) console.log(`  ${p}`);
    return 1;
  }
  console.log('same code, same structure');
  return 0;
}

if (import.meta.main) process.exit(main(process.argv.slice(2)));
