/* Small lexers for the repository's code files: enough to tell comments from
   string literals from the rest (D-174).  check-korean.ts counts Korean lines
   in comments with them; check-translation.ts compares a file's tokens without
   comments before and after a translation.

   Every lexer returns the file as a list of tokens:
     comment  a comment, its text as written (// ..., /* ... *\/, # ..., <!-- ... -->)
     string   a string, character, text block, template or regular expression literal, as written
     code     anything else, one word (letters, digits, _ and $) or one other character per token
   Whitespace is not a token, so line breaks and indentation never matter.
   `line` is the 1-based line a token starts on, `end` the line it ends on.

   The languages:
     java     // and /* *\/ comments; "...", '...', and """ text blocks
     kotlin   the same, with nested /* *\/ comments, raw """ strings and ${...} templates holding strings
     ts       TypeScript and JavaScript through the typescript package's scanner (templates and
              regular expressions re-scanned the way the parser would)
     css      /* *\/ comments; "..." and '...'
     html     <!-- --> comments
     hash     # comments (shell, Python, YAML, PowerShell); '...' and "..." on one line; Python's
              triple-quoted strings; PowerShell's <# #> block comments
     nsis     ; and # comments; '...', "..." and `...` on one line */

import { SyntaxKind } from 'typescript/unstable/ast';
import { createScanner } from 'typescript/unstable/ast/scanner';

export type TokenKind = 'comment' | 'string' | 'code';
export interface Token { kind: TokenKind; text: string; line: number; end: number }
export type Language = 'java' | 'kotlin' | 'ts' | 'css' | 'html' | 'hash' | 'python' | 'powershell' | 'nsis';

// The language of a file by its name, or undefined when it is not code these lexers read.
export function languageOf(file: string): Language | undefined {
  const ext = /\.([^./]+)$/.exec(file)?.[1]?.toLowerCase();
  switch (ext) {
    case 'java': return 'java';
    case 'kts': case 'kt': return 'kotlin';
    case 'ts': case 'js': case 'cjs': case 'mjs': case 'mts': case 'cts': return 'ts';
    case 'css': return 'css';
    case 'html': case 'htm': return 'html';
    case 'sh': case 'yml': case 'yaml': return 'hash';
    case 'py': return 'python';
    case 'ps1': case 'psm1': return 'powershell';
    case 'nsh': case 'nsi': return 'nsis';
    default: return undefined;
  }
}

const WORD = /[\p{L}\p{N}_$]/u;

class Out {
  tokens: Token[] = [];
  private lineStarts: number[] = [0];
  private text: string;
  constructor(text: string) {
    this.text = text;
    for (let i = 0; i < text.length; i += 1) if (text[i] === '\n') this.lineStarts.push(i + 1);
  }
  lineAt(pos: number): number {
    let lo = 0;
    let hi = this.lineStarts.length - 1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1;
      if (this.lineStarts[mid] <= pos) lo = mid; else hi = mid - 1;
    }
    return lo + 1;
  }
  push(kind: TokenKind, start: number, end: number): void {
    if (end <= start) return;
    const text = this.text.slice(start, end);
    this.tokens.push({ kind, text, line: this.lineAt(start), end: this.lineAt(end - 1) });
  }
  // Code between literals and comments: words and single characters, whitespace dropped.
  code(start: number, end: number): void {
    let i = start;
    while (i < end) {
      const c = this.text[i];
      if (/\s/.test(c)) { i += 1; continue; }
      if (WORD.test(c)) {
        let j = i + 1;
        while (j < end && WORD.test(this.text[j])) j += 1;
        this.push('code', i, j);
        i = j;
      } else {
        const cp = this.text.codePointAt(i)!;
        const n = cp > 0xffff ? 2 : 1;
        this.push('code', i, i + n);
        i += n;
      }
    }
  }
}

// From a quote at `i`, the index after the closing quote (backslash escapes; stops at a line break when oneLine).
function quoted(text: string, i: number, quote: string, oneLine: boolean, escapes = true): number {
  let j = i + quote.length;
  while (j < text.length) {
    if (escapes && text[j] === '\\') { j += 2; continue; }
    if (text.startsWith(quote, j)) return j + quote.length;
    if (oneLine && text[j] === '\n') return j;
    j += 1;
  }
  return text.length;
}

function lexJavaLike(text: string, kotlin: boolean): Token[] {
  const out = new Out(text);
  let codeStart = 0;
  let i = 0;
  const flush = (to: number) => { out.code(codeStart, to); };
  // A Kotlin string from i (at its opening quote or quotes): ${...} may hold code with strings of its own.
  const kotlinString = (from: number): number => {
    const raw = text.startsWith('"""', from);
    const q = raw ? '"""' : '"';
    let j = from + q.length;
    while (j < text.length) {
      if (!raw && text[j] === '\\') { j += 2; continue; }
      if (text.startsWith(q, j)) {
        if (raw) { while (text[j + 3] === '"') j += 1; }   // """a"""" ends with the last three quotes
        return j + q.length;
      }
      if (text.startsWith('${', j)) {
        let depth = 1;
        j += 2;
        while (j < text.length && depth > 0) {
          if (text[j] === '"') { j = kotlinString(j); continue; }
          if (text[j] === '{') depth += 1;
          else if (text[j] === '}') depth -= 1;
          j += 1;
        }
        continue;
      }
      if (!raw && text[j] === '\n') return j;
      j += 1;
    }
    return text.length;
  };
  while (i < text.length) {
    const c = text[i];
    if (c === '/' && text[i + 1] === '/') {
      flush(i);
      let j = text.indexOf('\n', i);
      if (j < 0) j = text.length;
      out.push('comment', i, j);
      i = codeStart = j;
    } else if (c === '/' && text[i + 1] === '*') {
      flush(i);
      let j = i + 2;
      let depth = 1;
      while (j < text.length && depth > 0) {
        if (text.startsWith('*/', j)) { depth -= 1; j += 2; continue; }
        if (kotlin && text.startsWith('/*', j)) { depth += 1; j += 2; continue; }
        j += 1;
      }
      out.push('comment', i, Math.min(j, text.length));
      i = codeStart = Math.min(j, text.length);
    } else if (c === '"') {
      flush(i);
      let j: number;
      if (kotlin) j = kotlinString(i);
      else if (text.startsWith('"""', i)) j = quoted(text, i, '"""', false);
      else j = quoted(text, i, '"', true);
      out.push('string', i, j);
      i = codeStart = j;
    } else if (c === '\'') {
      flush(i);
      const j = quoted(text, i, '\'', true);
      out.push('string', i, j);
      i = codeStart = j;
    } else {
      i += 1;
    }
  }
  flush(text.length);
  return out.tokens;
}

export const lexJava = (text: string): Token[] => lexJavaLike(text, false);
export const lexKotlin = (text: string): Token[] => lexJavaLike(text, true);

const STRINGISH = new Set<SyntaxKind>([SyntaxKind.StringLiteral, SyntaxKind.NoSubstitutionTemplateLiteral, SyntaxKind.TemplateHead,
  SyntaxKind.TemplateMiddle, SyntaxKind.TemplateTail, SyntaxKind.RegularExpressionLiteral]);
// After these a / divides; after anything else it starts a regular expression.
const ENDS_EXPRESSION = new Set<SyntaxKind>([SyntaxKind.Identifier, SyntaxKind.PrivateIdentifier, SyntaxKind.NumericLiteral,
  SyntaxKind.BigIntLiteral, SyntaxKind.StringLiteral, SyntaxKind.NoSubstitutionTemplateLiteral, SyntaxKind.TemplateTail,
  SyntaxKind.RegularExpressionLiteral, SyntaxKind.CloseParenToken, SyntaxKind.CloseBracketToken, SyntaxKind.CloseBraceToken,
  SyntaxKind.PlusPlusToken, SyntaxKind.MinusMinusToken, SyntaxKind.ThisKeyword, SyntaxKind.SuperKeyword, SyntaxKind.TrueKeyword,
  SyntaxKind.FalseKeyword, SyntaxKind.NullKeyword, SyntaxKind.ExclamationToken]);

export function lexTs(text: string): Token[] {
  const out = new Out(text);
  const s = createScanner(false, 0, text);
  const braces: ('brace' | 'template')[] = [];
  let prev: SyntaxKind | undefined;
  for (let k = s.scan(); k !== SyntaxKind.EndOfFile; k = s.scan()) {
    if (k === SyntaxKind.CloseBraceToken && braces.at(-1) === 'template') {
      braces.pop();
      k = s.reScanTemplateToken(false);
    } else if ((k === SyntaxKind.SlashToken || k === SyntaxKind.SlashEqualsToken) && (prev === undefined || !ENDS_EXPRESSION.has(prev))) {
      k = s.reScanSlashToken();
    }
    const start = s.getTokenStart();
    const end = s.getTokenEnd();
    switch (k) {
      case SyntaxKind.WhitespaceTrivia: case SyntaxKind.NewLineTrivia: continue;
      case SyntaxKind.SingleLineCommentTrivia: case SyntaxKind.MultiLineCommentTrivia:
        out.push('comment', start, end);
        continue;
      case SyntaxKind.OpenBraceToken: braces.push('brace'); break;
      case SyntaxKind.CloseBraceToken: braces.pop(); break;
      case SyntaxKind.TemplateHead: case SyntaxKind.TemplateMiddle: braces.push('template'); break;
      default: break;
    }
    if (STRINGISH.has(k)) out.push('string', start, end);
    else out.push('code', start, end);
    prev = k;
  }
  return out.tokens;
}

export function lexCss(text: string): Token[] {
  const out = new Out(text);
  let codeStart = 0;
  let i = 0;
  while (i < text.length) {
    const c = text[i];
    if (c === '/' && text[i + 1] === '*') {
      out.code(codeStart, i);
      let j = text.indexOf('*/', i + 2);
      j = j < 0 ? text.length : j + 2;
      out.push('comment', i, j);
      i = codeStart = j;
    } else if (c === '"' || c === '\'') {
      out.code(codeStart, i);
      const j = quoted(text, i, c, true);
      out.push('string', i, j);
      i = codeStart = j;
    } else i += 1;
  }
  out.code(codeStart, text.length);
  return out.tokens;
}

export function lexHtml(text: string): Token[] {
  const out = new Out(text);
  let codeStart = 0;
  let i = 0;
  while (i < text.length) {
    if (text.startsWith('<!--', i)) {
      out.code(codeStart, i);
      let j = text.indexOf('-->', i + 4);
      j = j < 0 ? text.length : j + 3;
      out.push('comment', i, j);
      i = codeStart = j;
    } else i += 1;
  }
  out.code(codeStart, text.length);
  return out.tokens;
}

// Line comments starting with one of `marks` at the start of a line or after whitespace.
function lexLineComments(text: string, marks: string, opts: { python?: boolean; powershell?: boolean; backtick?: boolean }): Token[] {
  const out = new Out(text);
  let codeStart = 0;
  let i = 0;
  while (i < text.length) {
    const c = text[i];
    const atWordStart = i === 0 || /\s/.test(text[i - 1]);
    if (opts.powershell && text.startsWith('<#', i)) {
      out.code(codeStart, i);
      let j = text.indexOf('#>', i + 2);
      j = j < 0 ? text.length : j + 2;
      out.push('comment', i, j);
      i = codeStart = j;
    } else if (marks.includes(c) && atWordStart) {
      out.code(codeStart, i);
      let j = text.indexOf('\n', i);
      if (j < 0) j = text.length;
      out.push('comment', i, j);
      i = codeStart = j;
    } else if (opts.python && (text.startsWith('"""', i) || text.startsWith('\'\'\'', i))) {
      out.code(codeStart, i);
      const j = quoted(text, i, text.slice(i, i + 3), false);
      out.push('string', i, j);
      i = codeStart = j;
    } else if (c === '"' || c === '\'' || (opts.backtick && c === '`')) {
      // PowerShell escapes with `, shell's single quotes have none: a backslash is taken as an escape only in "..."
      out.code(codeStart, i);
      const j = quoted(text, i, c, true, c === '"' && !opts.powershell);
      out.push('string', i, j);
      i = codeStart = j;
    } else i += 1;
  }
  out.code(codeStart, text.length);
  return out.tokens;
}

export function lex(language: Language, text: string): Token[] {
  switch (language) {
    case 'java': return lexJava(text);
    case 'kotlin': return lexKotlin(text);
    case 'ts': return lexTs(text);
    case 'css': return lexCss(text);
    case 'html': return lexHtml(text);
    case 'hash': return lexLineComments(text, '#', {});
    case 'python': return lexLineComments(text, '#', { python: true });
    case 'powershell': return lexLineComments(text, '#', { powershell: true });
    case 'nsis': return lexLineComments(text, ';#', { backtick: true });
    default: throw new Error(`no lexer for ${language as string}`);
  }
}

export const HANGUL = /[ᄀ-ᇿ㄰-㆏ꥠ-꥿가-힯ힰ-퟿]/;

// The lines (1-based) holding Korean inside a comment.
export function koreanCommentLines(tokens: readonly Token[]): Set<number> {
  const lines = new Set<number>();
  for (const t of tokens) {
    if (t.kind !== 'comment' || !HANGUL.test(t.text)) continue;
    t.text.split('\n').forEach((part, k) => { if (HANGUL.test(part)) lines.add(t.line + k); });
  }
  return lines;
}
