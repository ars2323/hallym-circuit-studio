/* Circuits, their appearance and other files: the words and the small
   rules (N-11, D-153; logic only, no page).  Names are English and stand
   apart from the Korean around them: a name is never followed by a Korean
   particle (D-135 14) -- "half 회로의 …", "File: lab.circ".

     circuit names    Add Circuit… and Rename…: the original's checks (a
                      name, not one already there, v1 D-? / Logisim
                      circuitNameMissingError, circuitNameDuplicateError)
     impact           what an appearance change would break (Port Order,
                      Auto Appearance: v1 lists up to eight places)
     port impact      a subcircuit's pin edit broke instance connections
     instance band    a subcircuit opened on its own, not the instance in
                      main (v1 P-02 InstanceBanner)
     libraries        refusals of Load/Unload Library, a saved library that
                      came in, the connections a save would break (P-03)
     same names       file tabs of the same name get the shortest folder
                      that tells them apart (v1 V-05, D-100)
     port order       moving one port in its side's list */

import type { CircuitRef, Impact, ImportPlan, PortImpact, SaveCut } from '../../../main/protocol.ts';

// ---- circuit names ----

export function nameProblem(name: string, circuits: readonly CircuitRef[], self?: string): string | null {
  const n = name.trim();
  if (n === '') return '회로 이름을 적으세요.';
  if (circuits.some((c) => c.name === n && c.circuitId !== self)) return '이 파일에 같은 이름의 회로가 이미 있습니다.';
  return null;
}

// The engine's refusal of a new circuit (edit.createCircuit: data.reason nameMissing, nameTaken).
export function createRefusal(reason: string | undefined): string | null {
  if (reason === 'nameMissing') return '회로 이름을 적으세요.';
  if (reason === 'nameTaken') return '이 파일에 같은 이름의 회로나 부품이 이미 있습니다.';
  return null;
}

// Remove Circuit refused (edit.deleteCircuit: lastCircuit, inUse).
export function removeRefusal(reason: string | undefined, name: string): string | null {
  if (reason === 'lastCircuit') return `Remove Circuit: 파일에는 회로가 하나는 있어야 해서 ${name} 회로를 지우지 않았습니다`;
  if (reason === 'inUse') return `Remove Circuit: 다른 회로가 ${name} 회로를 서브회로로 쓰고 있어 지우지 않았습니다`;
  return null;
}

// ---- appearance changes ----

export const IMPACT_PLACES = 8;

export function impactSentence(i: Impact): string {
  return `포트 자리가 바뀌어 인스턴스 ${i.instances}개에서 연결 ${i.connections}곳이 끊어집니다. 끊어진 곳은 그 회로에서 다시 이어야 합니다.`;
}

// The places, eight at most, and how many more.
export function impactPlaces(i: Impact): string {
  const shown = i.where.slice(0, IMPACT_PLACES);
  const more = i.where.length - shown.length;
  return [...shown, ...(more > 0 ? [`… (+${more})`] : [])].join('\n');
}

export function portImpactText(p: PortImpact): string {
  const kept = p.kept > 0 ? ` · ${p.kept}개는 선을 이어 되살렸습니다(따로 되돌릴 수 있습니다)` : '';
  return `${p.name} 회로의 핀을 바꿔 인스턴스 연결 ${p.broken}개가 끊겼습니다${kept}`;
}

// ---- a subcircuit on its own (v1 P-02) ----

export function standaloneText(sub: string, main: string): string {
  return `${sub} 회로를 따로 열었습니다. ${main} 회로 안에서 실행 중인 인스턴스가 아니어서 여기 보이는 값은 ${main} 회로의 값이 아닙니다.`;
}

// A pin chosen in a subcircuit that has instances.
export function pinPreviewText(connections: number, instances: number): string | null {
  if (connections <= 0) return null;
  return `이 핀을 지우거나 옮기면 인스턴스 ${instances}개에서 연결 ${connections}개가 끊길 수 있습니다.`;
}

// The Pin tool held in a subcircuit that has instances (v1 pinAddPreview: facts only).
export function pinAddText(sub: string, instances: number, connected: number, defaultAppearance: boolean): string | null {
  if (instances <= 0) return null;
  return defaultAppearance
    ? `핀을 더하면 ${sub} 회로의 인스턴스 ${instances}개 모양이 바뀌어 이어진 포트 ${connected}개가 움직일 수 있습니다.`
    : `핀을 더하면 ${sub} 회로의 인스턴스 ${instances}개에 포트가 하나 생깁니다. 사용자 모양이라 기존 포트는 그대로입니다.`;
}

// ---- libraries and other files ----

export function loadRefusal(reason: string | undefined): string | null {
  switch (reason) {
    case 'self': return 'Load Library: 이 파일 자신은 라이브러리로 넣을 수 없습니다';
    case 'circular': return 'Load Library: 그 파일이 이미 이 파일을 쓰고 있어 넣을 수 없습니다(순환 참조)';
    case 'noLibraryClass': return 'Load Library: 이 JAR 파일은 Logisim 라이브러리가 아닙니다(manifest에 Library-Class가 없습니다)';
    case 'notFound': return 'Load Library: 그 자리에 파일이 없습니다';
    case 'loadFailed': return 'Load Library: 그 파일을 라이브러리로 읽지 못했습니다';
    case 'readOnly': return 'Load Library: 읽기 전용 파일입니다. Save As로 저장한 뒤 넣으세요';
    default: return null;
  }
}

export function unloadRefusal(reason: string | undefined, circuit?: string): string | null {
  if (reason === 'inUse') return `Unload Library: ${circuit ?? 'main'} 회로가 이 라이브러리의 부품을 쓰고 있어 빼지 않았습니다`;
  if (reason === 'toolbar') return 'Unload Library: 원조 도구 모음이나 마우스 설정이 이 라이브러리의 도구를 써서 빼지 않았습니다';
  return null;
}

export function libraryUpdatedText(library: string): string {
  return `라이브러리 ${library} 파일이 저장되어 새 버전을 다시 불러왔습니다 · 시뮬레이션은 Reset 상태입니다`;
}

// Saving now would break connections in other open files (v1 LibrarySync: "저장하면 … 끊깁니다").
export function saveCutLines(cuts: readonly SaveCut[]): string {
  return cuts.map((c) => {
    const who = c.instances.slice(0, 4).join(', ') + (c.instances.length > 4 ? ' …' : '');
    return `${c.file}: ${who} — ${c.connections}`;
  }).join('\n');
}
export function saveCutSentence(cuts: readonly SaveCut[]): string {
  const n = cuts.reduce((a, c) => a + c.connections, 0);
  return `이 파일을 라이브러리로 쓰는 다른 파일에서 인스턴스 연결 ${n}곳이 끊깁니다. 끊긴 곳은 그 파일에서 다시 이어야 합니다. 그래도 저장할까요?`;
}

// Import Subcircuits: what comes in, in order ("inner", "main → main-2"), and the parts left out.
export function planLines(plan: ImportPlan): string {
  const lines = plan.order.map((o) => (o.name === o.as ? o.name : `${o.name}  →  ${o.as}`));
  if (plan.skipped.length) lines.push('', `Left out (${plan.skipped.length}):`, ...plan.skipped);
  return lines.join('\n');
}
export function importedText(n: number, file: string): string {
  return `가져왔습니다 · 회로 ${n}개 · ${file}`;
}

// ---- same names (v1 V-05): the shortest folder tail that tells tabs of one name apart ----

export interface Named { id: string; name: string; path: string | null }

export function distinguishers(files: readonly Named[]): Map<string, string> {
  const out = new Map<string, string>();
  const byName = new Map<string, Named[]>();
  for (const f of files) byName.set(f.name, [...(byName.get(f.name) ?? []), f]);
  for (const group of byName.values()) {
    const saved = group.filter((f) => f.path !== null);
    if (group.length < 2 || saved.length < 2) continue;
    const dirs = new Map(saved.map((f) => [f.id, folders(f.path!)]));
    const most = Math.max(...[...dirs.values()].map((d) => d.length));
    for (const f of saved) {
      const mine = dirs.get(f.id)!;
      let tail = '';
      for (let k = 1; k <= Math.max(1, most); k++) {
        tail = mine.slice(Math.max(0, mine.length - k)).join('/');
        const clash = saved.some((o) => o.id !== f.id && dirs.get(o.id)!.slice(Math.max(0, dirs.get(o.id)!.length - k)).join('/') === tail);
        if (!clash) break;
      }
      if (tail) out.set(f.id, tail);
    }
  }
  return out;
}

function folders(p: string): string[] {
  const parts = p.split(/[\\/]+/).filter(Boolean);
  parts.pop();   // the file itself
  return parts;
}

// ---- port order ----

export function moved<T>(list: readonly T[], from: number, to: number): T[] {
  const out = [...list];
  if (from < 0 || from >= out.length || to < 0 || to >= out.length || from === to) return out;
  const [x] = out.splice(from, 1);
  out.splice(to, 0, x);
  return out;
}
