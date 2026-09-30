/* The 논리설계 및 실험 course's tutorial (N-18, D-161; v2 brief 6-2): sixteen
   steps over tests/tutorial/tutorial-logic.circ (a copy of it, opened for
   the tutorial and closed at the end).  The example: input pins A, B and an
   output pin Y around an empty place (the area memo "AND Gate"); pins P, Q
   whose wires end at the empty place of a half_adder; a 4-bit counter (the
   Register count, an adder and the constant 1) whose clock input is empty,
   a Clock beside it, a Radix Probe (Hallym MIPS's, shown in both courses: v2 addendum 3, A-08) and a Splitter with named arms.
   No MIPS-only part (Instruction Memory, Data Memory, Console).  What [건너뛰기] does is in actions.json (one source with the
   engine's test, TutorialExamplesTest). */

import type { Step, Target } from '../../shared/tutorial.ts';
import actions from './actions.json' with { type: 'json' };
import {
  andGate, andLabelled, andWired, boxOf, clockWired, halfAdderPlaced, labelled, longest, memoBox, messagesOf, netOf, onNet, parts, union, wireBox, wiresAt, type Box,
} from './facts.ts';
import type { CourseTutorial } from './host.ts';

type T = CourseTutorial;
const L = actions.logic;

// ---- places on the example ---------------------------------------------------------------

const main = (t: T) => t.host.snapshot();
const pin = (t: T, label: string) => labelled(main(t), 'Pin', label);
const register = (t: T) => labelled(main(t), 'Register', 'count');
// The AND place: A, B, Y and the memo between them.
const andPlace = (t: T): Box | null => union(memoBox(main(t), 'AND Gate'), boxOf([pin(t, 'A'), pin(t, 'B'), pin(t, 'Y')], 10));
const halfPlace = (t: T): Box | null => union(memoBox(main(t), 'half_adder'), boxOf([pin(t, 'P'), pin(t, 'Q'), ...parts(main(t), 'half_adder')], 10));
const counter = (t: T): Box | null => boxOf([register(t), ...parts(main(t), 'Adder'), ...parts(main(t), 'Constant'), ...parts(main(t), 'Clock'),
  ...parts(main(t), 'Radix Probe'), ...parts(main(t), 'Splitter'), ...parts(main(t), 'LED')], 20);
const busPlace = (t: T): Box | null => boxOf([register(t), ...parts(main(t), 'Radix Probe'), ...parts(main(t), 'Splitter'), ...parts(main(t), 'LED')], 16);
const clockPlace = (t: T): Box | null => boxOf([register(t), ...parts(main(t), 'Clock')], 16);
// The count register's output wires: the longest is the one to click.
const qWire = (t: T): string | undefined => { const r = register(t); return r ? longest(main(t), wiresAt(main(t), r.id, 0)) : undefined; };
const tool = (t: T, name: string): Target => t.host.el(`.toolbar [data-unit="${name}"]`);
const clockMessage = (t: T) => messagesOf(t.host.messages(), 'CLOCK_UNCONNECTED')[0];
const messageRow = (t: T, id: string | undefined): Target => (id ? t.host.el(`.msglist button.msg[data-id="${id}"]`) : null);

// The Canvas on the main circuit, its view on `box` (the steps' prepare).
function onMain(t: T, box: (t: T) => Box | null): void {
  if (t.host.showMain()) t.did.push('main circuit');
  t.host.fit(box(t));
}

export const LOGIC_STEPS: Step<T>[] = [
  { id: 'L1', kind: 'explain', pose: 'haram-hari-greeting',
    title: () => '논리설계 튜토리얼입니다',
    body: () => '예제 회로 tutorial-logic.circ 파일의 복사본을 열었습니다. 게이트를 놓고 선을 잇고, 서브회로와 버스를 보고, 클럭을 한 번씩 뛰어 봅니다. 복사본이라 마음대로 바꿔도 원래 예제는 그대로입니다. [다음] 단추나 → 키로 넘어갑니다.',
    targets: (t) => [t.host.el('.filebar [aria-selected="true"]')],
    prepare: async (t) => { onMain(t, andPlace); } },

  { id: 'L2', kind: 'practice', keys: ['Ctrl+K'], typing: true,
    title: () => '부품 찾기: AND Gate',
    body: () => '부품은 이름으로 찾습니다. Ctrl+K 검색 창에서 고르면 부품이 곧바로 Canvas 위에 놓이고, 왼쪽 Components 패널의 검색 칸에서 고르면 손에 들립니다. 이번에는 Components 패널 검색 칸에 and 라고 치고 목록의 AND Gate 부품을 누르세요. 누르면 부품이 손에 들리고 다음 단계로 넘어갑니다.',
    targets: (t) => [t.host.el('.upper .compsearch-input')],
    pass: (t) => [t.host.el('.upper .comps')],
    prepare: async (t) => { if (t.host.showUpper('Components')) t.did.push('Components tab'); },
    reveal: (t) => { t.host.showUpper('Components'); },
    done: (t) => t.host.held()?.name === 'AND Gate' || !!andGate(main(t)),
    skip: async (t) => { t.host.hold(L.and.lib, L.and.name); } },

  { id: 'L3', kind: 'practice',
    title: () => '빈 자리에 놓기',
    body: () => '손에 든 AND Gate 부품을 Canvas 위 AND Gate 칸 안에 놓으세요. 칸 안을 한 번 누르면 그 자리에 놓입니다. 놓으면 다음 단계로 넘어갑니다.',
    targets: (t) => [t.host.box(memoBox(main(t), 'AND Gate'))],
    prepare: async (t) => {
      onMain(t, andPlace);
      if (!andGate(main(t)) && t.host.held()?.name !== 'AND Gate') t.host.hold(L.and.lib, L.and.name);
    },
    reveal: (t) => t.host.fit(andPlace(t)),
    done: (t) => !!andGate(main(t)),
    skip: async (t) => {
      await t.host.call('edit.addComponent', { fileId: t.host.fileId(), circuitId: main(t)?.circuitId, lib: L.and.lib, name: L.and.name, loc: L.and.loc });
      t.host.setTool('Edit');
    } },

  { id: 'L4', kind: 'practice',
    title: () => '선 잇기',
    body: () => 'Wire 도구를 고르고 A 입력 핀 끝에서 AND Gate 부품의 입력까지 끌어 선을 그으세요. B 입력 핀도 다른 입력에, AND Gate 출력은 Y 출력 핀에 이으세요. 세 선이 모두 이어지면 결과를 짚어 드립니다.',
    targets: (t) => [t.host.box(andPlace(t)), tool(t, 'Wire')],
    prepare: async (t) => { onMain(t, andPlace); },
    reveal: (t) => t.host.fit(andPlace(t)),
    done: (t) => andWired(main(t)),
    result: {
      title: () => '선이 이어졌습니다',
      body: () => '포트 끝에서 끌면 ㄱ자 선이 그어지고, 선 끝이 포트에 닿으면 이어집니다. 선이 갈라지는 자리에는 점이 찍힙니다.',
      targets: (t) => [t.host.box(andPlace(t))],
      reveal: (t) => t.host.fit(andPlace(t)) },
    skip: async (t) => {
      const fileId = t.host.fileId(), circuitId = main(t)?.circuitId;
      const g = andGate(main(t));
      if (g && (g.loc[0] !== L.and.loc[0] || g.loc[1] !== L.and.loc[1])) {
        await t.host.call('edit.move', { fileId, circuitId, ids: [g.id], dx: L.and.loc[0] - g.loc[0], dy: L.and.loc[1] - g.loc[1] });
      } else if (!g) {
        await t.host.call('edit.addComponent', { fileId, circuitId, lib: L.and.lib, name: L.and.name, loc: L.and.loc });
      }
      for (const points of L.andWires) await t.host.call('edit.addWire', { fileId, circuitId, points });
      t.host.setTool('Edit');
    } },

  { id: 'L5', kind: 'practice',
    title: () => 'Poke: 입력 바꾸기',
    body: () => '도구 모음의 Poke 도구를 고르고 A 입력 핀과 B 입력 핀을 한 번씩 눌러 둘 다 켜세요(값 1). 두 입력이 켜지면 Y 출력 핀의 값을 짚어 드립니다.',
    targets: (t) => [t.host.box(andPlace(t)), tool(t, 'Poke')],
    prepare: async (t) => { onMain(t, andPlace); },
    reveal: (t) => t.host.fit(andPlace(t)),
    done: (t) => { const y = pin(t, 'Y'); return !!y && t.host.value(y.id, 0) === '1'; },
    result: {
      title: () => 'Y 출력 핀이 켜졌습니다',
      body: () => 'AND Gate 출력은 두 입력이 모두 1일 때만 1입니다. 선 색은 그 선의 값입니다. 색마다 뜻은 상태 표시줄 오른쪽의 Wire Colors 단추에서 볼 수 있습니다.',
      targets: (t) => [t.host.box(andPlace(t)), t.host.el('.status .legend-button')],
      reveal: (t) => t.host.fit(andPlace(t)) },
    skip: async (t) => {
      t.host.setTool('Poke');
      for (const label of ['A', 'B']) {
        const p = pin(t, label);
        if (p && t.host.value(p.id, 0) !== '1') await t.host.call('sim.poke', { fileId: t.host.fileId(), circuitId: main(t)?.circuitId, componentId: p.id });
      }
    } },

  { id: 'L6', kind: 'practice', typing: true,
    title: () => 'Quick Attributes: 라벨 붙이기',
    body: () => 'Edit 도구로 AND Gate 부품을 눌러 고르면 옆에 Quick Attributes 막대가 나옵니다. 막대의 Label 단추를 누르고 g1 이라고 친 뒤 Enter 키를 누르세요. 라벨이 붙으면 결과를 짚어 드립니다.',
    targets: (t) => {
      const bar = t.host.el('.quickbar:not([hidden]) [data-attr="label"]');
      return [t.host.box(boxOf([andGate(main(t))], 8)), bar];
    },
    pass: (t) => [t.host.el('.quickbar:not([hidden])'), t.host.el('.quickbar ~ .inline-field, .canvaspanel .inline-field')],
    prepare: async (t) => { onMain(t, andPlace); t.host.setTool('Edit'); },
    reveal: (t) => t.host.fit(andPlace(t)),
    done: (t) => andLabelled(main(t), L.label),
    result: {
      title: () => '라벨이 붙었습니다',
      body: () => '부품 옆 라벨 칩에 g1 이름이 보입니다. 라벨은 Messages 패널과 Cycle View 탭에서도 그 부품의 이름으로 쓰입니다.',
      targets: (t) => [t.host.box(boxOf([andGate(main(t))], 24))],
      reveal: (t) => t.host.fit(andPlace(t)) },
    skip: async (t) => {
      const g = andGate(main(t));
      t.host.setTool('Edit');
      if (g) await t.host.call('edit.setAttr', { fileId: t.host.fileId(), circuitId: main(t)?.circuitId, ids: [g.id], attr: 'label', value: L.label });
    } },

  { id: 'L7', kind: 'practice', keys: ['Ctrl+=', 'Ctrl+-', 'Ctrl+0'],
    title: () => '확대하기',
    body: () => 'Ctrl 키를 누른 채 Canvas 위에서 휠을 굴리거나 Ctrl+= 키를 눌러 150% 이상으로 확대하세요. 지금 배율은 상태 표시줄 오른쪽 끝에 나옵니다. 150% 이상이 되면 결과를 짚어 드립니다.',
    targets: (t) => [t.host.box(boxOf([andGate(main(t))], 30)), t.host.el('.status .zoom-button')],
    // from 100 %: the student's own zoom is what the step waits for (the steps before fitted the view)
    prepare: async (t) => { if (t.host.showMain()) t.did.push('main circuit'); t.host.setZoom(1); },
    done: (t) => t.host.zoom() >= L.zoom - 0.005,
    result: {
      title: () => '확대했습니다',
      body: () => 'Ctrl+0 키를 누르면 회로 전체가 한 화면에 들어오게 맞춥니다. 다음 단계에서는 튜토리얼이 알맞게 맞춰 보여 드립니다.',
      targets: (t) => [t.host.el('.status .zoom-button')] },
    skip: async (t) => { t.host.setZoom(L.zoom); } },

  { id: 'L8', kind: 'practice',
    title: () => '서브회로 놓기: half_adder',
    body: () => 'Components 패널 맨 위 묶음에는 이 파일의 회로가 있습니다. half_adder 회로를 골라 Canvas 위 half_adder 칸에 놓으세요. 미리 보기의 왼쪽 포트 두 개를 P·Q 핀에서 온 선 끝에 맞춰 놓으면 선이 이어지고 다음 단계로 넘어갑니다.',
    targets: (t) => [t.host.el('.upper .comptree details:first-of-type'), t.host.box(halfPlace(t))],   // the file's group: its half_adder row inside
    pass: (t) => [t.host.el('.upper .comps')],
    prepare: async (t) => {
      onMain(t, halfPlace);
      if (t.host.showUpper('Components')) t.did.push('Components tab');
      if (t.host.clearPartSearch()) t.did.push('search emptied');
    },
    reveal: (t) => { t.host.showUpper('Components'); t.host.clearPartSearch(); t.host.fit(halfPlace(t)); },
    done: (t) => halfAdderPlaced(main(t), L.halfAdder.name),
    skip: async (t) => {
      const fileId = t.host.fileId(), circuitId = main(t)?.circuitId;
      const k = parts(main(t), L.halfAdder.name)[0];
      if (k) await t.host.call('edit.move', { fileId, circuitId, ids: [k.id], dx: L.halfAdder.loc[0] - k.loc[0], dy: L.halfAdder.loc[1] - k.loc[1] });
      else await t.host.call('edit.addComponent', { fileId, circuitId, lib: null, name: L.halfAdder.name, loc: L.halfAdder.loc });
      t.host.setTool('Edit');
    } },

  { id: 'L9', kind: 'practice',
    title: () => '서브회로 안 보기',
    body: () => '도구 모음의 Poke 도구를 고르고 half_adder 부품을 두 번 누르면 그 회로 안으로 들어갑니다(Edit 도구로 두 번 누르면 라벨을 고칩니다). 들어가면 안에 무엇이 있는지 짚어 드립니다.',
    targets: (t) => [t.host.box(boxOf(parts(main(t), L.halfAdder.name), 6)), tool(t, 'Poke')],
    prepare: async (t) => { onMain(t, halfPlace); },
    reveal: (t) => t.host.fit(halfPlace(t)),
    done: (t) => (t.host.shownCircuit()?.path.length ?? 0) > 0 && t.host.shownCircuit()?.circuit === L.halfAdder.name,
    result: {
      title: () => 'half_adder 회로 안입니다',
      body: () => 'XOR Gate 부품이 합 s 출력을, AND Gate 부품이 올림 c 출력을 만듭니다. 이 회로를 고치면 half_adder 부품을 놓은 모든 자리에 함께 반영됩니다. 다음 단계에서 main 회로로 돌아갑니다.',
      targets: (t) => [t.host.box(t.host.shownExtent())],
      reveal: (t) => t.host.fit(t.host.shownExtent()) },
    skip: async (t) => { const k = parts(main(t), L.halfAdder.name)[0]; if (k) t.host.enter(k.id); },
    leave: async (t) => { t.host.showMain(); t.host.setTool('Edit'); } },

  { id: 'L10', kind: 'explain',
    title: () => '버스와 Splitter',
    body: () => '굵은 선은 여러 비트를 한꺼번에 나르는 버스입니다. 선 옆 숫자 4 표시는 4비트라는 뜻입니다. Splitter 부품은 버스를 비트별 팔로 나누고, 팔마다 붙인 이름 q0~q3 표시가 옆에 보입니다. 팔 이름은 Splitter 편집기에서 붙입니다.',
    targets: (t) => [t.host.box(busPlace(t))],
    prepare: async (t) => { onMain(t, counter); },
    reveal: (t) => t.host.fit(counter(t)) },

  { id: 'L11', kind: 'practice',
    title: () => 'Messages: 동작할 수 없는 곳',
    body: () => '아래 Messages 패널에는 회로가 동작할 수 없는 까닭이 한 줄씩 나옵니다. count 레지스터의 클럭 입력이 비어 있다는 줄을 누르세요. 누르면 Canvas 위에 그 자리를 표시하고 다음 단계로 넘어갑니다.',
    targets: (t) => [messageRow(t, clockMessage(t)?.id)],
    prepare: async (t) => { onMain(t, counter); if (t.host.showBottom('Messages')) t.did.push('Messages tab'); },
    reveal: (t) => { t.host.showBottom('Messages'); },
    done: (t) => { const m = clockMessage(t); return !!m && t.host.chosenMessage() === m.id; },
    skip: async (t) => { const m = clockMessage(t); if (m) t.host.chooseMessage(m.id); } },

  { id: 'L12', kind: 'practice',
    title: () => '클럭 잇기',
    body: () => 'Wire 도구로 Clock 부품의 출력에서 count 레지스터 아래쪽 클럭 입력(삼각형 표시)까지 선을 이으세요. 이어지면 Messages 패널이 어떻게 바뀌는지 짚어 드립니다.',
    targets: (t) => [t.host.box(clockPlace(t)), tool(t, 'Wire')],
    prepare: async (t) => { onMain(t, counter); },
    reveal: (t) => t.host.fit(counter(t)),
    done: (t) => clockWired(main(t)) && (t.host.messages() ?? []).length === 0,
    result: {
      title: () => '메시지가 없습니다',
      body: () => 'Messages 패널이 비었습니다. 동작할 수 없는 곳이 없다는 뜻이지, 회로가 바라는 대로 동작한다는 뜻은 아닙니다. 값이 맞는지는 직접 돌려 보며 확인합니다.',
      targets: (t) => [t.host.el('.panel.bottom .notice-host, .panel.bottom .msgs')],
      reveal: (t) => { t.host.showBottom('Messages'); } },
    skip: async (t) => {
      for (const points of L.clockWires) await t.host.call('edit.addWire', { fileId: t.host.fileId(), circuitId: main(t)?.circuitId, points });
      t.host.setTool('Edit');
    } },

  { id: 'L13', kind: 'practice',
    title: () => 'Signal Flow: 신호가 가는 길',
    body: () => '도구 모음의 Signal Flow 단추가 켜져 있는 동안에는 Edit 도구로 선을 누르면 신호가 그 선에서 어디로 가는지 흐름으로 보여 줍니다. count 레지스터의 출력 선을 누르세요. 흐름이 보이면 결과를 짚어 드립니다.',
    targets: (t) => [tool(t, 'Signal Flow'), t.host.box(wireBox(main(t), qWire(t) ?? ''))],
    prepare: async (t) => { onMain(t, counter); t.host.setTool('Edit'); if (t.host.flowOnClick(true)) t.did.push('Signal Flow on Click'); },
    reveal: (t) => t.host.fit(counter(t)),
    done: (t) => { const f = t.host.flow(); const r = register(t); return f.running && !!r && onNet(netOf(main(t), r.id, 0), f.from); },
    result: {
      title: () => '카운터 고리',
      body: () => 'count 레지스터의 출력은 가산기를 거쳐 다시 그 레지스터의 입력으로 돌아옵니다(카운터 고리). 흐름이 멈춘 끝점에는 Radix Probe 부품과 LED 부품이 있습니다.',
      targets: (t) => [t.host.box(counter(t))],
      reveal: (t) => t.host.fit(counter(t)) },
    skip: async (t) => { const w = qWire(t); if (w) await t.host.startFlow(w); } },

  { id: 'L14', kind: 'practice', keys: ['F10'],
    title: () => '1 Cycle: 클럭 한 번',
    body: () => '도구 모음의 1 Cycle 단추를 누르거나 F10 키를 누르세요. 클럭이 한 번 뛰면 무엇이 바뀌었는지 짚어 드립니다.',
    targets: (t) => [tool(t, '1 Cycle')],
    prepare: async (t) => { onMain(t, counter); cycleAtStart = t.host.sim()?.cycle ?? 0; },
    done: (t) => (t.host.sim()?.cycle ?? 0) > cycleAtStart,
    result: {
      title: () => '한 사이클이 지났습니다',
      body: () => 'count 레지스터 값이 하나 늘었습니다. Radix Probe 부품이 같은 값을 16진수, 10진수, 2진수로 함께 보이고, LED q0 칸도 켜졌습니다.',
      targets: (t) => [t.host.box(busPlace(t))],
      reveal: (t) => t.host.fit(counter(t)) },
    skip: async (t) => { await t.host.cycles(1); } },

  { id: 'L15', kind: 'practice',
    title: () => 'Cycle View: 지난 사이클',
    body: () => '아래 Cycle View 탭에는 사이클마다 값이 쌓입니다. Previous Cycle 단추를 누르면 한 사이클 앞의 값을 회로 전체에 보여 줍니다. 누르면 결과를 짚어 드립니다.',
    targets: (t) => [t.host.el('.cycleview .cbar [data-cmd="prev"]') ?? t.host.els('.cycleview .cbar button').find((b) => b.textContent === 'Previous Cycle') ?? null],
    prepare: async (t) => {
      if (t.host.showBottom('Cycle View')) t.did.push('Cycle View tab');
      if ((t.host.sim()?.cycle ?? 0) < 1) await t.host.cycles(1);
    },
    reveal: (t) => { t.host.showBottom('Cycle View'); },
    done: (t) => t.host.record()?.past === true,
    result: {
      title: () => '지난 사이클을 보고 있습니다',
      body: () => '표의 열 하나가 한 사이클이고, 고른 사이클의 값이 Canvas 위에도 보입니다. Latest Cycle 단추를 누르면 마지막 사이클로 돌아옵니다.',
      targets: (t) => [t.host.el('.cycleview .ctablebox')] },
    skip: async (t) => { await t.host.previousCycle(); } },

  { id: 'L16', kind: 'end', pose: 'haram-hari-congrats',
    title: () => '튜토리얼 끝!',
    body: () => '게이트와 선, Quick Attributes, 서브회로, 버스, Messages, 클럭과 Cycle View 탭까지 해 보았습니다. [끝내기] 단추를 누르면 예제가 닫히고 튜토리얼 전의 화면으로 돌아갑니다. 파일을 모두 닫으면 나오는 처음 화면의 튜토리얼 보기 단추로 언제든 다시 시작합니다.',
    targets: () => [] },
];

let cycleAtStart = 0;
