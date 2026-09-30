/* tools/check-translation.ts (D-174): a translation changes comments and
   words only.  Code keeps its tokens (string literals only when listed);
   a doc keeps its code, links, IDs, numbers and structure. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { compareCode, compareDoc, docFacts } from '../../tools/check-translation.ts';
import { lexJava, lexKotlin, lexTs } from '../../tools/lexers.ts';

const JAVA = `package a;
// 레지스터 파일을 읽는다
class A {
  /* 두 번째 줄
     세 번째 줄 */
  String s = "Ready";   // 상태 표시줄
  char c = '"';
  String t = """
      // not a comment
      "quoted" """;
}
`;

test('code: a comment-only change passes (Java, TypeScript, Kotlin)', () => {
  const english = JAVA.replace('레지스터 파일을 읽는다', 'reads the register file').replace('두 번째 줄\n     세 번째 줄', 'the second line')
    .replace('// 상태 표시줄', '// the status bar\n');
  assert.deepEqual(compareCode('A.java', JAVA, english).problems, []);
  const ts = 'const a = 1; // 하나\n/** 설명 */\nexport function f() { return a / 2; }\n';
  assert.deepEqual(compareCode('a.ts', ts, ts.replace('하나', 'one').replace('설명', 'what it does')).problems, []);
  const kts = 'plugins { java } // 자바\n/* 바깥 /* 안쪽 */ 끝 */\nval s = "x"\n';
  assert.deepEqual(compareCode('b.kts', kts, kts.replace('자바', 'Java').replace('바깥 /* 안쪽 */ 끝', 'nested')).problems, []);
});

test('code: moving code across lines and changing whitespace passes; changing a token fails', () => {
  const ts = 'if (a) {\n  b();\n}\n';
  assert.deepEqual(compareCode('a.ts', ts, 'if (a) { b(); }\n').problems, []);
  const r = compareCode('a.ts', ts, 'if (a) {\n  c();\n}\n');
  assert.equal(r.problems.length, 1);
  assert.match(r.problems[0], /the code differs at token 6/);
  assert.match(compareCode('a.ts', ts, `${ts}x();\n`).problems[0], /tokens before .* added/);
});

test('code: a changed string literal fails and is listed; passes when listed in --literals', () => {
  const before = 'throw new IllegalStateException("엔진이 없다");\n';
  const after = 'throw new IllegalStateException("no engine");\n';
  const r = compareCode('E.java', before, after);
  assert.equal(r.problems.length, 1);
  assert.match(r.problems[0], /string literal changed/);
  assert.deepEqual(r.literals, [{ old: '"엔진이 없다"', new: '"no engine"' }]);
  assert.deepEqual(compareCode('E.java', before, after, new Set(['no engine'])).problems, []);
  assert.deepEqual(compareCode('E.java', before, after, new Set(['"no engine"'])).problems, []);
  assert.equal(compareCode('E.java', before, after, new Set(['no engines'])).problems.length, 1);
  const ts = 'log(`엔진 ${n}개`);\n';
  assert.match(compareCode('a.ts', ts, 'log(`${n} engines`);\n').problems.join('\n'), /string literal changed|the code differs/);
});

test('code: a Java text block and a TS template literal are strings, their // and /* are not comments', () => {
  const java = lexJava('String t = """\n  // x\n  /* y */ "a" """; // real\n');
  assert.deepEqual(java.filter((t) => t.kind === 'comment').map((t) => t.text), ['// real']);
  assert.equal(java.filter((t) => t.kind === 'string').length, 1);
  assert.match(java.find((t) => t.kind === 'string')!.text, /^"""[\s\S]*"""$/);
  const ts = lexTs('const s = `a // b ${x /* c */ + `/* d ${y} */`} e`; // f\nconst r = /\\/\\/ g/; const q = a / b / c; // h\n');
  assert.deepEqual(ts.filter((t) => t.kind === 'comment').map((t) => t.text), ['/* c */', '// f', '// h']);
  assert.ok(ts.some((t) => t.kind === 'string' && t.text === '/\\/\\/ g/'), 'the regular expression is one literal');
  // a template's text changed is a string change, its expression's comment is not
  const before = 'const s = `안녕 ${x /* 이름 */}`;\n';
  assert.deepEqual(compareCode('a.ts', before, before.replace('이름', 'the name')).problems, []);
  assert.match(compareCode('a.ts', before, before.replace('안녕', 'hi')).problems[0], /string literal changed/);
});

test('code: Kotlin raw strings and templates holding strings', () => {
  const toks = lexKotlin('val a = "x ${f("// no")} y" // yes\nval b = """ /* no */ """\n');
  assert.deepEqual(toks.filter((t) => t.kind === 'comment').map((t) => t.text), ['// yes']);
});

const DOC = `# 제목

첫 문단은 \`node tools/x.ts\`를 부른다(D-012, #473). 커밋 \`abc1234\`와 9a3f0c1d가 있다.
[안내서](usage.ko.md#설치하기)와 [표](../PLAN.md#6-8) 참고. 값은 3개, 1.5초.

## 표

| 열 | 값 |
| --- | --- |
| 가 | 1 |
| 나 | 2 |

- 하나
- 둘
  이어짐
- 셋

\`\`\`sh
echo 한글 그대로
\`\`\`
`;

const EN = `# Title

The first paragraph calls \`node tools/x.ts\` (D-012, #473). There are commits \`abc1234\` and 9a3f0c1d.
See [the guide](usage.ko.md#installing) and [the table](../PLAN.md#6-8). The value is 3, 1.5 s.

## Table

| Column | Value |
| --- | --- |
| a | 1 |
| b | 2 |

- one
- two
  continued
- three

\`\`\`sh
echo 한글 그대로
\`\`\`
`;

test('doc: a faithful translation passes; a Korean #anchor may change', () => {
  assert.deepEqual(compareDoc('x.md', DOC, EN), []);
  const f = docFacts(DOC);
  assert.deepEqual(f.headings, [1, 2]);
  assert.deepEqual(f.tables, [4]);
  assert.deepEqual(f.lists, [3]);
  assert.deepEqual(f.ids, ['D-012']);
  assert.deepEqual(f.issues, ['#473']);
  assert.deepEqual(f.hashes, ['9a3f0c1d']);
  assert.deepEqual(f.links, ['usage.ko.md#설치하기', '../PLAN.md#6-8']);
});

test('doc: a missing table row fails', () => {
  const p = compareDoc('x.md', DOC, EN.replace('| b | 2 |\n', ''));
  assert.ok(p.some((x) => /tables \(rows\): 1 before, 1 now; first difference at table 1: 4 before, 3 now/.test(x)), p.join('\n'));
});

test('doc: a changed ID fails', () => {
  const p = compareDoc('x.md', DOC, EN.replace('D-012', 'D-013'));
  assert.ok(p.some((x) => /IDs missing: "D-012"/.test(x)), p.join('\n'));
  assert.ok(p.some((x) => /IDs added: "D-013"/.test(x)), p.join('\n'));
});

test('doc: a dropped code span fails; a changed code block fails', () => {
  const p = compareDoc('x.md', DOC, EN.replace('`node tools/x.ts`', 'the tool'));
  assert.ok(p.some((x) => /inline code missing: "`node tools\/x.ts`"/.test(x)), p.join('\n'));
  const q = compareDoc('x.md', DOC, EN.replace('echo 한글 그대로', 'echo Korean as is'));
  assert.ok(q.some((x) => /code block 1 changed/.test(x)), q.join('\n'));
});

test('doc: a lost number, #number, hash, link, heading or list item fails; an added number passes', () => {
  const cases: [string, string, RegExp][] = [
    ['1.5 s', 's', /numbers missing: 1\.5/],
    ['#473', 'the issue', /#numbers missing: "#473"/],
    ['9a3f0c1d', 'a commit', /hashes missing: "9a3f0c1d"/],
    ['(../PLAN.md#6-8)', '(../PLAN.md#7-1)', /link anchors missing: "..\/PLAN.md#6-8"/],
    ['(usage.ko.md#installing)', '(usage.en.md#installing)', /link targets missing: "usage.ko.md"/],
    ['## Table', 'Table', /headings \(levels\)/],
    ['- three\n', '', /lists \(items\)/],
  ];
  for (const [from, to, rule] of cases) {
    const p = compareDoc('x.md', DOC, EN.replace(from, to));
    assert.ok(p.some((x) => rule.test(x)), `${from}: ${p.join('\n')}`);
  }
  assert.deepEqual(compareDoc('x.md', DOC, EN.replace('The value is 3', 'The value is 3 (three, 2 + 1)')), []);
});
