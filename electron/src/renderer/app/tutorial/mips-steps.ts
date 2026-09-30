/* The 컴퓨터구조 course's tutorial (N-18, D-161; v2 brief 6-3 and addenda:
   the one Data Memory for data and stack, D-140; executable images only,
   D-141): fourteen steps over tests/tutorial/tutorial-mips.circ (a copy of
   it, with tutorial.hmx and tutorial.s beside it).  The example: a small
   single-cycle datapath -- PC (PC reg, and started choosing the entry
   before the first clock), Instruction Memory, the instruction's fields,
   the subcircuits control, regfile and alu, Data Memory, Console -- with
   one tunnel misspelt (RegWirte for RegWrite).  The program is Hallym MIPS
   2.4.0's export of tutorial.s (it sums four words and prints "sum = 14"). */

import type { Step, Target } from '../../shared/tutorial.ts';
import actions from './actions.json' with { type: 'json' };
import { boxOf, labelled, longest, messagesOf, netOf, onNet, parts, tunnel, union, wireBox, type Box } from './facts.ts';
import type { CourseTutorial } from './host.ts';

type T = CourseTutorial;
const M = actions.mips;

const main = (t: T) => t.host.snapshot();
const one = (t: T, name: string) => parts(main(t), name)[0];
const pcReg = (t: T) => labelled(main(t), 'Register', 'PC reg');
const started = (t: T) => labelled(main(t), 'Register', 'started');
const entryConst = (t: T) => parts(main(t), 'Constant').find((c) => /^0x0*400000$/i.test(c.attrs.value ?? ''));
const pcMux = (t: T) => { const r = pcReg(t); return r ? parts(main(t), 'Multiplexer').find((m) => netOf(main(t), r.id, 0)?.ports.some(([id]) => id === m.id)) : undefined; };
// The wire from the PC multiplexer to the Instruction Memory's address (the PC's own wire).
const pcWire = (t: T): string | undefined => { const m = pcMux(t); const s = main(t); if (!m || !s) return undefined; return longest(s, netOf(s, m.id, m.ports.length - 1)?.wires ?? []); };
const pcPlace = (t: T): Box | null => boxOf([pcReg(t), started(t), entryConst(t), pcMux(t)], 16);
const fetchPlace = (t: T): Box | null => union(pcPlace(t), boxOf([one(t, 'Instruction Memory')], 16));
const whole = (t: T): Box | null => boxOf(main(t)?.components ?? [], 20);
const tool = (t: T, name: string): Target => t.host.el(`.toolbar [data-unit="${name}"]`);
const typoMessage = (t: T) => messagesOf(t.host.messages(), 'TUNNEL_UNPAIRED').find((m) => m.near === M.fixed);
const messageRow = (t: T, id: string | undefined): Target => (id ? t.host.el(`.msglist button.msg[data-id="${id}"]`) : null);
const box = (t: T, ...names: string[]): Target => t.host.box(boxOf(names.map((n) => one(t, n)), 8));

function onMain(t: T, b: (t: T) => Box | null): void {
  if (t.host.showMain()) t.did.push('main circuit');
  t.host.fit(b(t));
}
let cycleAtStart = 0;

export const MIPS_STEPS: Step<T>[] = [
  { id: 'C1', kind: 'explain', pose: 'haram-hari-greeting',
    title: () => '컴퓨터구조 튜토리얼입니다',
    body: () => '작은 단일 사이클 MIPS 데이터패스 예제 tutorial-mips.circ 파일의 복사본을 열었습니다. 프로그램을 불러와 한 사이클씩 실행하며 값을 봅니다. 부품 놓기와 선 긋기 같은 편집 기초는 논리설계 및 실험 튜토리얼에 있습니다.',
    targets: (t) => [t.host.el('.filebar [aria-selected="true"]')],
    prepare: async (t) => { onMain(t, whole); } },

  { id: 'C2', kind: 'explain',
    title: () => '데이터패스 둘러보기',
    body: () => 'PC 값이 Instruction Memory 부품의 주소가 되어 명령 워드가 나오고, 워드는 필드로 나뉘어 control, regfile, alu 서브회로로 갑니다. Data Memory 부품 하나가 데이터 구간 `0x10000000`~`0x100FFFFF` 주소와 스택 구간 `0x7FFC0000`~`0x7FFFFFFF` 주소를 함께 맡고, Console 부품은 syscall 출력을 보입니다.',
    targets: (t) => [t.host.box(pcPlace(t)), box(t, 'Instruction Memory'), box(t, 'control'), box(t, 'regfile'), box(t, 'alu'), box(t, 'Data Memory'), box(t, 'Console')],
    prepare: async (t) => { onMain(t, whole); },
    reveal: (t) => t.host.fit(whole(t)) },

  { id: 'C3', kind: 'explain',
    title: () => 'Hallym MIPS 부품',
    body: () => 'Components 패널의 Hallym MIPS 묶음에 이 데이터패스가 쓰는 Instruction Memory, Data Memory, Console 부품과 여러 진법으로 보이는 Radix Probe 부품이 있습니다. 새 회로에서도 여기서 골라 놓습니다.',
    targets: (t) => [t.host.els('.upper .comptree details > summary').find((s) => /Hallym MIPS/.test(s.textContent ?? '')) ?? null],
    prepare: async (t) => { if (t.host.showUpper('Components')) t.did.push('Components tab'); if (t.host.clearPartSearch()) t.did.push('search emptied'); },
    reveal: (t) => { t.host.showUpper('Components'); (t.host.els('.upper .comptree details > summary').find((s) => /Hallym MIPS/.test(s.textContent ?? '')) as HTMLElement | undefined)?.scrollIntoView({ block: 'nearest' }); } },

  { id: 'C4', kind: 'practice',
    title: () => 'Messages: 짝 없는 터널',
    body: () => '아래 Messages 패널에 짝 없는 터널 한 줄이 있습니다. 그 줄을 누르면 Canvas 위에 그 터널을 표시합니다. 누르면 다음 단계로 넘어갑니다.',
    targets: (t) => [messageRow(t, typoMessage(t)?.id)],
    prepare: async (t) => { onMain(t, whole); if (t.host.showBottom('Messages')) t.did.push('Messages tab'); },
    reveal: (t) => { t.host.showBottom('Messages'); },
    done: (t) => { const m = typoMessage(t); return !!m && t.host.chosenMessage() === m.id; },
    skip: async (t) => { const m = typoMessage(t); if (m) t.host.chooseMessage(m.id); } },

  { id: 'C5', kind: 'practice', typing: true,
    title: () => '터널 이름 고치기',
    body: () => '메시지 끝의 "혹시 RegWrite?" 말처럼, regfile 부품의 RegWrite 입력에 붙은 터널 이름이 RegWirte 입니다(글자 순서가 바뀜). Edit 도구로 그 터널을 눌러 고르고, 오른쪽 Attributes 패널의 Label 칸이나 Quick Attributes 막대의 Label 단추에서 RegWrite 라고 고친 뒤 Enter 키를 누르세요. 고치면 결과를 짚어 드립니다.',
    targets: (t) => {
      const k = tunnel(main(t), M.typo) ?? tunnel(main(t), M.fixed);
      return [t.host.box(boxOf([k], 10)), t.host.el('.atable tr[data-attr="label"] input')];
    },
    pass: (t) => [t.host.el('.quickbar:not([hidden])'), t.host.el('.canvaspanel .inline-field'), t.host.el('.atable')],
    prepare: async (t) => {
      const k = tunnel(main(t), M.typo);
      if (t.host.showMain()) t.did.push('main circuit');
      t.host.fit(boxOf([k, one(t, 'regfile')], 40));
      t.host.setTool('Edit');
    },
    done: (t) => !tunnel(main(t), M.typo) && !!tunnel(main(t), M.fixed) && messagesOf(t.host.messages(), 'TUNNEL_UNPAIRED').length === 0,
    result: {
      title: () => '짝이 맞았습니다',
      body: () => 'Messages 패널이 비었습니다. 이제 control 서브회로의 RegWrite 신호가 regfile 부품까지 갑니다. 가까운 이름은 짐작이 하나뿐일 때만 짚어 줍니다.',
      targets: (t) => [t.host.el('.panel.bottom .notice-host, .panel.bottom .msgs')],
      reveal: (t) => { t.host.showBottom('Messages'); } },
    skip: async (t) => {
      const k = tunnel(main(t), M.typo);
      t.host.setTool('Edit');
      if (k) await t.host.call('edit.setAttr', { fileId: t.host.fileId(), circuitId: main(t)?.circuitId, ids: [k.id], attr: 'label', value: M.fixed });
    } },

  { id: 'C6', kind: 'practice',
    title: () => '프로그램 불러오기',
    body: () => '도구 모음의 Load Program… 단추를 누르고 예제 옆의 tutorial.hmx 파일을 고르세요. 확장자가 .hmx인 실행 이미지는 Hallym MIPS 프로그램에서 어셈블한 뒤(Ctrl+S) 제목 줄 오른쪽 아이콘 묶음의 Export executable image (.hmx) 단추로 내보낸 파일입니다. 불러오면 무엇이 올라갔는지 짚어 드립니다.',
    targets: (t) => [tool(t, 'Load Program…')],
    prepare: async (t) => { onMain(t, fetchPlace); },
    done: (t) => !!t.host.program()?.entry,
    result: {
      title: () => '프로그램을 불러왔습니다',
      body: () => 'Instruction Memory 부품 몸체에 불러온 워드 수 `18 words` 표시와 지금 PC 주소의 워드가 보이고, 상태 표시줄에 불러온 프로그램 이름이 나옵니다. PC 값은 entry `0x00400000` 주소에서 출발합니다. 예전 파일의 Program 속성이 .s 파일을 가리키면 상태 표시줄이 그 사실과 할 일을 알려 줍니다.',
      targets: (t) => [box(t, 'Instruction Memory'), t.host.el('.status .progfact')],
      reveal: (t) => t.host.fit(fetchPlace(t)) },
    skip: async (t) => { await t.host.loadProgram(); } },

  { id: 'C7', kind: 'explain',
    title: () => '진입점과 PC',
    body: () => 'entry `0x00400000` 주소는 `main` 라벨의 자리입니다. 이 실행 이미지는 예외 처리기 없이 어셈블해 만들어 시작 코드가 없으므로, 첫 워드가 곧 main 라벨의 자리입니다. PC 시작 값을 정하는 것은 회로의 몫입니다: 이 예제는 첫 클럭 전에는 started 레지스터 값이 꺼져 있어 entry 상수를 PC 값으로 고릅니다.',
    targets: (t) => [t.host.box(pcPlace(t))],
    prepare: async (t) => { onMain(t, fetchPlace); },
    reveal: (t) => t.host.fit(fetchPlace(t)) },

  { id: 'C8', kind: 'practice', keys: ['F10'],
    title: () => '1 Cycle: 명령 하나',
    body: () => '도구 모음의 1 Cycle 단추를 누르거나 F10 키를 누르세요. 한 사이클에 명령 하나가 실행됩니다. 실행되면 무엇이 바뀌었는지 짚어 드립니다.',
    targets: (t) => [tool(t, '1 Cycle')],
    prepare: async (t) => { onMain(t, fetchPlace); cycleAtStart = t.host.sim()?.cycle ?? 0; },
    done: (t) => (t.host.sim()?.cycle ?? 0) > cycleAtStart,
    result: {
      title: () => 'PC 값이 다음 명령으로',
      body: () => 'PC 값이 4 늘어 다음 명령의 주소가 되었고, Instruction Memory 부품이 그 주소의 워드를 내보냅니다. 상태 표시줄의 PC 값도 함께 바뀝니다.',
      targets: (t) => [t.host.box(fetchPlace(t))],
      reveal: (t) => t.host.fit(fetchPlace(t)) },
    skip: async (t) => { await t.host.cycles(1); } },

  { id: 'C9', kind: 'practice',
    title: () => 'Cycle View: 지난 사이클',
    body: () => '아래 Cycle View 탭의 표에는 사이클마다 PC 값과 명령이 쌓입니다. Previous Cycle 단추를 눌러 한 사이클 앞을 보세요. 누르면 결과를 짚어 드립니다.',
    targets: (t) => [t.host.els('.cycleview .cbar button').find((b) => b.textContent === 'Previous Cycle') ?? null],
    prepare: async (t) => {
      if (t.host.showBottom('Cycle View')) t.did.push('Cycle View tab');
      if ((t.host.sim()?.cycle ?? 0) < 1) await t.host.cycles(1);
    },
    reveal: (t) => { t.host.showBottom('Cycle View'); },
    done: (t) => t.host.record()?.past === true,
    result: {
      title: () => '지난 사이클을 보고 있습니다',
      body: () => '고른 사이클의 값이 회로 전체와 오른쪽 탭들에 보입니다. Latest Cycle 단추를 누르면 마지막 사이클로 돌아옵니다.',
      targets: (t) => [t.host.el('.cycleview .ctablebox')] },
    skip: async (t) => { await t.host.previousCycle(); } },

  { id: 'C10', kind: 'explain',
    title: () => 'Registers 탭',
    body: () => 'Registers 탭은 표시한 레지스터 파일 regfile 서브회로의 `$0`~`$31` 레지스터 값을 Hex, Dec, Bin 세 진법으로 함께 보입니다. 방금 바뀐 레지스터 줄은 노란 띠로 표시합니다.',
    targets: (t) => [t.host.el('.cycleview .cside')],
    prepare: async (t) => {
      if (t.host.showBottom('Cycle View')) t.did.push('Cycle View tab');
      if (t.host.showCycleSide('Registers')) t.did.push('Registers tab');
    },
    reveal: (t) => { t.host.showCycleSide('Registers'); } },

  { id: 'C11', kind: 'explain',
    title: () => 'Instruction 탭',
    body: () => 'Instruction 탭은 보고 있는 사이클의 명령 워드를 필드별 색으로 나누어 보입니다(op, rs, rt, rd, shamt, funct, imm). Hallym MIPS 프로그램의 Inspector 패널과 같은 명령이면 같은 필드 색과 같은 값이 나옵니다.',
    targets: (t) => [t.host.el('.cycleview .cside')],
    prepare: async (t) => {
      if (t.host.showBottom('Cycle View')) t.did.push('Cycle View tab');
      if (t.host.showCycleSide('Instruction')) t.did.push('Instruction tab');
    },
    reveal: (t) => { t.host.showCycleSide('Instruction'); } },

  { id: 'C12', kind: 'practice',
    title: () => 'Signal Flow: PC 값이 가는 길',
    body: () => '도구 모음의 Signal Flow 단추가 켜져 있는 동안 Edit 도구로 PC 값이 Instruction Memory 부품으로 가는 선을 누르세요. 흐름이 보이면 끝점을 짚어 드립니다.',
    targets: (t) => [tool(t, 'Signal Flow'), t.host.box(wireBox(main(t), pcWire(t) ?? ''))],
    prepare: async (t) => { onMain(t, fetchPlace); t.host.setTool('Edit'); if (t.host.flowOnClick(true)) t.did.push('Signal Flow on Click'); },
    reveal: (t) => t.host.fit(fetchPlace(t)),
    done: (t) => { const f = t.host.flow(); const m = pcMux(t); return f.running && !!m && onNet(netOf(main(t), m.id, m.ports.length - 1), f.from); },
    result: {
      title: () => 'PC 값의 끝점',
      body: () => 'PC 값은 Instruction Memory 부품의 주소, +4 가산기, 분기 목적지 가산기로 갑니다. 끝점마다 이름이 붙어 흐름이 멈춥니다.',
      targets: (t) => [t.host.box(whole(t))],
      reveal: (t) => t.host.fit(whole(t)) },
    skip: async (t) => { const w = pcWire(t); if (w) await t.host.startFlow(w); } },

  { id: 'C13', kind: 'practice', keys: ['F5'],
    title: () => 'Run: 끝까지',
    body: () => 'Run 단추를 누르거나 F5 키를 누르면 클럭이 계속 뜁니다. 시뮬레이션은 처음 상태로 돌려 두었고 클럭 속도 칸은 16 Hz 값으로 맞춰 두었습니다. 프로그램이 exit syscall 명령에 닿으면 Console 부품에 exit 표시가 나오고 PC 값이 멈춥니다. 끝나면 출력을 짚어 드립니다.',
    targets: (t) => [tool(t, 'Run'), box(t, 'Console')],
    prepare: async (t) => {
      onMain(t, whole);
      if (t.host.showBottom('Console')) t.did.push('Console tab');
      if (t.host.running()) await t.host.run(false);
      await t.host.reset();   // the program from its start
      if (await t.host.setHz(16)) t.did.push('16 Hz');
      // Reset empties the Console (the engine's mips.console after the answer): wait for it, not for a stale exit
      for (let i = 0; i < 100 && t.host.console()?.exited; i += 1) await new Promise((done) => setTimeout(done, 20));
    },
    reveal: (t) => t.host.fit(whole(t)),
    done: (t) => t.host.console()?.exited === true,
    result: {
      title: () => '프로그램이 끝났습니다',
      body: () => 'Console 탭과 Console 부품에 `sum = 14` 출력이 나왔습니다. 클럭은 아직 뛰고 있습니다: Esc 키를 누르면 멈춥니다.',
      targets: (t) => [t.host.el('.panel.bottom .consolescroll'), box(t, 'Console')],
      reveal: (t) => { t.host.showBottom('Console'); } },
    skip: async (t) => { await t.host.cycles(40); } },

  { id: 'C14', kind: 'end', pose: 'haram-hari-congrats',
    title: () => '튜토리얼 끝!',
    body: () => 'Messages, 실행 이미지 불러오기, 진입점과 PC 값, 1 Cycle 단추와 Run 단추, Cycle View 탭의 Registers 탭과 Instruction 탭, Signal Flow까지 해 보았습니다. [끝내기] 단추를 누르면 예제가 닫히고 튜토리얼 전의 화면으로 돌아갑니다. 파일을 모두 닫으면 나오는 처음 화면의 튜토리얼 보기 단추로 언제든 다시 시작합니다.',
    targets: () => [] },
];
