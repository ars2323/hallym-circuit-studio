/* Korean lines in the repository's tracked files (D-174, "Repository in English").

     node tools/check-korean.ts                    the report; exits 0
     node tools/check-korean.ts --strict           exits 1 when a file has more Korean lines than it is allowed
     node tools/check-korean.ts --markdown <file>  also writes the report as Markdown to <file>
     node tools/check-korean.ts --all              the table lists every file read, not only those with Korean
     node tools/check-korean.ts --show <file>...   the lines counted in these files, with their numbers

   What is counted:
     doc   .md and .txt: every line holding Korean (Hangul);
     code  .java .ts .js .cjs .mjs .kts .css .html .yml .yaml .sh .py .ps1 .nsh: the lines holding Korean inside a
           comment (tools/lexers.ts).  Korean in a string literal is the program's behavior or data (screen text,
           search terms, patterns that recognize Korean messages) and is not counted.
   Anything else (.properties, .circ, .s, .hmx, .json ...) is data and is not read.  vendor/ holds upstream
   originals and is left out.

   What is allowed: ../korean-allowed.txt, one rule a line -- `<glob>  <max lines or *>  <reason>` -- for what the
   language rules keep in Korean (the student's screen resources, test data, the Korean user guides, GLOSSARY's
   Korean column).  The first rule whose glob matches a file sets its allowance; a file no rule matches is allowed 0.
   Globs: ** any folders, * any characters but /, ? one character, {a,b} either.

   On CI the Markdown report is appended to $GITHUB_STEP_SUMMARY. */

import { execFileSync } from 'node:child_process';
import { appendFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { HANGUL, koreanCommentLines, languageOf, lex } from './lexers.ts';

export const repo = path.join(import.meta.dirname, '..', '..');
export const ALLOW_FILE = 'korean-allowed.txt';
const LEFT_OUT = /^vendor\//;
const DOC = /\.(md|txt)$/i;
const CODE = /\.(java|ts|js|cjs|mjs|kts|css|html|yml|yaml|sh|py|ps1|nsh)$/i;

export type Kind = 'doc' | 'code';
export interface Rule { glob: string; max: number; reason: string; re: RegExp }
export interface Row { path: string; kind: Kind; korean: number; allowed: number; over: number; rule?: Rule }

// A glob as a regular expression over a path from the repository's root.
export function globToRegExp(glob: string): RegExp {
  let re = '';
  for (let i = 0; i < glob.length; i += 1) {
    const c = glob[i];
    if (c === '*' && glob[i + 1] === '*') {
      i += 1;
      if (glob[i + 1] === '/') { i += 1; re += '(?:.*/)?'; } else re += '.*';
    } else if (c === '*') re += '[^/]*';
    else if (c === '?') re += '[^/]';
    else if (c === '{') {
      const close = glob.indexOf('}', i);
      re += `(?:${glob.slice(i + 1, close).split(',').map((p) => p.replace(/[.+^$()|[\]\\]/g, '\\$&').replace(/\*/g, '[^/]*')).join('|')})`;
      i = close;
    } else re += c.replace(/[.+^$()|[\]\\]/g, '\\$&');
  }
  return new RegExp(`^${re}$`);
}

export function parseRules(text: string): Rule[] {
  const rules: Rule[] = [];
  text.split('\n').forEach((raw, n) => {
    const line = raw.trim();
    if (!line || line.startsWith('#')) return;
    const m = /^(\S+)\s+(\*|\d+)\s+(.+)$/.exec(line);
    if (!m) throw new Error(`${ALLOW_FILE}:${n + 1}: not "<glob>  <max lines or *>  <reason>": ${line}`);
    rules.push({ glob: m[1], max: m[2] === '*' ? Infinity : Number(m[2]), reason: m[3], re: globToRegExp(m[1]) });
  });
  return rules;
}

export const kindOf = (file: string): Kind | undefined => (DOC.test(file) ? 'doc' : CODE.test(file) ? 'code' : undefined);

// The Korean lines of one file (1-based numbers): every line for a doc, comment lines for code.
export function koreanLineNumbers(file: string, text: string): number[] {
  const kind = kindOf(file);
  if (kind === 'doc') return text.split('\n').flatMap((l, i) => (HANGUL.test(l) ? [i + 1] : []));
  if (kind === 'code') {
    if (!HANGUL.test(text)) return [];
    const lang = languageOf(file);
    return lang ? [...koreanCommentLines(lex(lang, text))].sort((a, b) => a - b) : [];
  }
  return [];
}
export const koreanLines = (file: string, text: string): number => koreanLineNumbers(file, text).length;

export function rowFor(file: string, text: string, rules: readonly Rule[]): Row | undefined {
  const kind = kindOf(file);
  if (!kind) return undefined;
  const korean = koreanLines(file, text);
  const rule = rules.find((r) => r.re.test(file));
  const allowed = rule ? rule.max : 0;
  return { path: file, kind, korean, allowed, over: Math.max(0, korean - allowed), rule };
}

export const topOf = (file: string): string => (file.includes('/') ? `${file.split('/')[0]}/` : '(root)');

export interface Totals { files: number; korean: number; allowed: number; over: number; overFiles: number }
export function totalsByTop(rows: readonly Row[]): Map<string, Totals> {
  const out = new Map<string, Totals>();
  for (const r of rows) {
    const t = out.get(topOf(r.path)) ?? { files: 0, korean: 0, allowed: 0, over: 0, overFiles: 0 };
    t.files += r.korean > 0 ? 1 : 0;
    t.korean += r.korean;
    t.allowed += Math.min(r.korean, r.allowed);
    t.over += r.over;
    t.overFiles += r.over > 0 ? 1 : 0;
    out.set(topOf(r.path), t);
  }
  return new Map([...out].sort(([a], [b]) => a.localeCompare(b)));
}

const fmt = (n: number) => (n === Infinity ? '*' : String(n));

export function markdown(rows: readonly Row[], all = false): string {
  const shown = rows.filter((r) => all || r.korean > 0);
  const totals = totalsByTop(rows);
  const sum = [...totals.values()].reduce((a, t) => ({ files: a.files + t.files, korean: a.korean + t.korean, allowed: a.allowed + t.allowed,
    over: a.over + t.over, overFiles: a.overFiles + t.overFiles }), { files: 0, korean: 0, allowed: 0, over: 0, overFiles: 0 });
  const lines = [
    '## Korean lines (tools/check-korean.ts)', '',
    `${sum.korean} Korean lines in ${sum.files} files; ${sum.allowed} allowed by ${ALLOW_FILE}; ${sum.over} over the allowance in ${sum.overFiles} files.`, '',
    '| top directory | files with Korean | Korean lines | allowed | over |', '| --- | ---: | ---: | ---: | ---: |',
    ...[...totals].map(([top, t]) => `| ${top} | ${t.files} | ${t.korean} | ${t.allowed} | ${t.over} |`),
    `| **total** | **${sum.files}** | **${sum.korean}** | **${sum.allowed}** | **${sum.over}** |`, '',
    '<details><summary>Per file</summary>', '',
    '| path | kind | Korean lines | allowed | over |', '| --- | --- | ---: | ---: | ---: |',
    ...shown.map((r) => `| \`${r.path}\` | ${r.kind} | ${r.korean} | ${fmt(r.allowed)} | ${r.over} |`),
    '', '</details>', '',
  ];
  return lines.join('\n');
}

export function report(files: readonly string[], read: (f: string) => string, rules: readonly Rule[]): Row[] {
  return files.filter((f) => !LEFT_OUT.test(f)).map((f) => (kindOf(f) ? rowFor(f, read(f), rules) : undefined))
    .filter((r): r is Row => r !== undefined).sort((a, b) => a.path.localeCompare(b.path));
}

if (import.meta.main) {
  const argv = process.argv.slice(2);
  const strict = argv.includes('--strict');
  const all = argv.includes('--all');
  const mdAt = argv.indexOf('--markdown');
  const showAt = argv.indexOf('--show');
  if (showAt >= 0) {
    for (const f of argv.slice(showAt + 1)) {
      const text = readFileSync(path.resolve(f), 'utf8');
      const lines = text.split('\n');
      const rel = path.relative(repo, path.resolve(f)).split(path.sep).join('/');
      for (const n of koreanLineNumbers(rel, text)) console.log(`${rel}:${n}: ${lines[n - 1]}`);
    }
    process.exit(0);
  }
  const tracked = execFileSync('git', ['-C', repo, 'ls-files', '-z'], { encoding: 'utf8' }).split('\0').filter(Boolean)
    .filter((f) => existsSync(path.join(repo, f)));
  const allowPath = path.join(repo, ALLOW_FILE);
  const rules = existsSync(allowPath) ? parseRules(readFileSync(allowPath, 'utf8')) : [];
  const rows = report(tracked, (f) => readFileSync(path.join(repo, f), 'utf8'), rules);
  const shown = rows.filter((r) => all || r.korean > 0);
  const w = Math.max(4, ...shown.map((r) => r.path.length));
  console.log(`${'path'.padEnd(w)}  kind  korean  allowed  over`);
  for (const r of shown) console.log(`${r.path.padEnd(w)}  ${r.kind.padEnd(4)}  ${String(r.korean).padStart(6)}  ${fmt(r.allowed).padStart(7)}  ${String(r.over).padStart(4)}`);
  console.log('');
  console.log('top directory      files  korean  allowed   over');
  let korean = 0; let over = 0; let overFiles = 0;
  for (const [top, t] of totalsByTop(rows)) {
    console.log(`${top.padEnd(17)}  ${String(t.files).padStart(5)}  ${String(t.korean).padStart(6)}  ${String(t.allowed).padStart(7)}  ${String(t.over).padStart(5)}`);
    korean += t.korean; over += t.over; overFiles += t.overFiles;
  }
  console.log(`\n${korean} Korean lines; ${over} over the allowance in ${overFiles} files${strict ? '' : ' (report only; --strict fails on this)'}`);
  const md = markdown(rows, all);
  if (mdAt >= 0) writeFileSync(argv[mdAt + 1], md);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, md);
  process.exit(strict && over > 0 ? 1 : 0);
}
