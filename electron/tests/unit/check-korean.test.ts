/* tools/check-korean.ts (D-174): Korean lines in docs, and in code only
   inside comments; the allowance from korean-allowed.txt. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { globToRegExp, koreanLineNumbers, parseRules, report, rowFor, totalsByTop } from '../../tools/check-korean.ts';

const repo = path.join(import.meta.dirname, '..', '..', '..');

test('code: Korean in a comment counts, Korean in a string literal does not', () => {
  const java = [
    'class A {',
    '  String s = "준비";            // 상태 표시줄',
    '  String t = "// 주석 아님";',
    '  /* 여러 줄',
    '     comment in English',
    '     둘째 */',
    '  String u = """',
    '    /* 글 덩어리 */',
    '    """;',
    '}',
  ].join('\n');
  assert.deepEqual(koreanLineNumbers('A.java', java), [2, 4, 6]);
  const ts = 'const a = `템플릿 ${b /* 식 안의 주석 */}`;\nconst r = /한글/; // 정규식 뒤\n';
  assert.deepEqual(koreanLineNumbers('a.ts', ts), [1, 2]);
  assert.deepEqual(koreanLineNumbers('a.ts', 'const a = \'한글\';\nconst b = `\n한글\n`;\n'), []);
});

test('the # and ; languages, CSS and HTML', () => {
  assert.deepEqual(koreanLineNumbers('x.sh', 'echo "# 문자열"  # 주석\necho \'한글\'\n# 둘째 주석\nn=${#arr[@]}\n'), [1, 3]);
  assert.deepEqual(koreanLineNumbers('x.yml', 'name: 이름 # 주석\n  run: |\n    # 셸 주석\n    echo "한글"\n'), [1, 3]);
  assert.deepEqual(koreanLineNumbers('x.py', 's = """\n# 문자열 안\n"""\n# 주석\n'), [4]);
  assert.deepEqual(koreanLineNumbers('x.ps1', '<# 블록\n   주석 #>\n$a = "# 한글"\n'), [1, 2]);
  assert.deepEqual(koreanLineNumbers('x.nsh', '; 주석\n!define A "한글" ; 뒤 주석\n# 샵 주석\n'), [1, 2, 3]);
  assert.deepEqual(koreanLineNumbers('x.css', '.a { content: "한글"; } /* 주석 */\n'), [1]);
  assert.deepEqual(koreanLineNumbers('x.css', '.a { content: "한글"; }\n'), []);
  assert.deepEqual(koreanLineNumbers('x.html', '<!-- 주석 -->\n<title>한글</title>\n'), [1]);
});

test('docs: every line holding Korean; other files are data and not read', () => {
  assert.deepEqual(koreanLineNumbers('a.md', '# 제목\n\nEnglish\n`한글`\n'), [1, 4]);
  assert.deepEqual(koreanLineNumbers('a.txt', '한\n글\n'), [1, 2]);
  assert.equal(rowFor('a.properties', '한글', []), undefined);
  assert.equal(rowFor('t.circ', '한글', []), undefined);
});

test('the allowance: first matching rule, * for any, 0 when no rule matches', () => {
  const rules = parseRules('# comment\n\ndocs/usage/*.ko.md  *  Korean user guide\ndocs/GLOSSARY.md  3  the Korean column\ntests/**/*.{txt,json}  *  test data\n');
  const read: Record<string, string> = {
    'docs/usage/usage.ko.md': '가\n나\n다\n', 'docs/GLOSSARY.md': '가\n나\n다\n라\n', 'docs/PLAN.md': '가\n',
    'tests/disasm/a.txt': '가\n', 'tests/README.md': '가\n', 'app/A.java': '// 가\n', 'app/B.java': 'String s = "가";\n',
  };
  const rows = report(Object.keys(read), (f) => read[f], rules);
  const by = Object.fromEntries(rows.map((r) => [r.path, [r.korean, r.allowed, r.over]]));
  assert.deepEqual(by['docs/usage/usage.ko.md'], [3, Infinity, 0]);
  assert.deepEqual(by['docs/GLOSSARY.md'], [4, 3, 1]);
  assert.deepEqual(by['docs/PLAN.md'], [1, 0, 1]);
  assert.deepEqual(by['tests/disasm/a.txt'], [1, Infinity, 0]);
  assert.deepEqual(by['tests/README.md'], [1, 0, 1]);
  assert.deepEqual(by['app/A.java'], [1, 0, 1]);
  assert.deepEqual(by['app/B.java'], [0, 0, 0]);
  assert.deepEqual(totalsByTop(rows).get('docs/'), { files: 3, korean: 8, allowed: 6, over: 2, overFiles: 2 });
  assert.throws(() => parseRules('docs/*.md three reasons\n'), /korean-allowed\.txt:1/);
});

test('globs: ** folders, * one name, {a,b}', () => {
  assert.ok(globToRegExp('**/*_ko.properties').test('app/resources/logisim/ko/a_ko.properties'));
  assert.ok(globToRegExp('**/*_ko.properties').test('a_ko.properties'));
  assert.ok(!globToRegExp('docs/*.md').test('docs/usage/a.md'));
  assert.ok(globToRegExp('tests/**/*.{s,circ}').test('tests/mips/x/y.circ'));
  assert.ok(!globToRegExp('tests/**/*.{s,circ}').test('tests/mips/README.md'));
});

test('the repository\'s korean-allowed.txt parses, and every rule has a reason', () => {
  const rules = parseRules(readFileSync(path.join(repo, 'korean-allowed.txt'), 'utf8'));
  assert.ok(rules.length > 0);
  for (const r of rules) assert.ok(r.reason.length > 5, r.glob);
});
