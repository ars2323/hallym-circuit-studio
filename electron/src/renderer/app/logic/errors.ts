/* What the window says when a file cannot be opened, saved or made (logic
   only; app.ts shows it in the window's own dialog, with no character),
   and when a command (Undo, 1 Cycle, Run …) cannot be done (one line in
   the status bar).

   The words are the screen's, not the engine's: the engine answers in
   English for developers (`cannot read: /home/…/lab3.circ`, docs/engine-
   api.md 2, code 2 with data.path and data.reason); the student reads a
   Korean sentence -- what happened, then what to do -- and the file's name
   on a line of its own ("File: lab3.circ": the name only, never the whole
   path, and never a particle right after it).  Only where the engine's own
   words are the fact to act on (Logisim's loader message, the libraries a
   .circ names) do they go in the dialog's detail box. */

export interface FileErrorDialog {
  title: string;
  file?: string;          // the file's name, for the "File:" line
  body: string;           // Korean: what happened, what to do
  detail?: string;        // facts in the mono font: the loader's words, the missing libraries
}

export interface FailedCall {
  name?: string;
  message: string;
  code?: number;
  data?: unknown;
}

export const ERR_FILE = 2;

const baseName = (p: string): string => p.split(/[\\/]/).filter(Boolean).pop() ?? p;
// The folder a file was looked for in: its own name only (never the whole path, D-135 14), for a sentence to name it.
const folderName = (p: string): string | null => { const parts = p.split(/[\\/]/).filter(Boolean); return parts.length > 1 ? parts[parts.length - 2] : null; };

export function fileError(action: 'open' | 'save' | 'new', e: FailedCall, name?: string): FileErrorDialog {
  const data = (typeof e.data === 'object' && e.data !== null ? e.data : {}) as { path?: string; reason?: string; missing?: string[] };
  const file = data.path ? baseName(data.path) : name;
  const title = action === 'open' ? '파일을 열지 못했습니다' : action === 'save' ? '파일을 저장하지 못했습니다' : '새 회로를 만들지 못했습니다';
  if (e.name === 'EngineGone') {
    return { title, file, body: '엔진이 멈춰서 끝내지 못했습니다. 엔진이 다시 시작하면 한 번 더 해 보세요.' };
  }
  if (e.code !== ERR_FILE) {
    return { title, file, body: '엔진이 이 일을 끝내지 못했습니다. 한 번 더 해 보고, 그래도 안 되면 조교에게 알려 주세요.', detail: e.message };
  }
  switch (data.reason) {
    case 'notFound': {
      // The folder by its name (UI review of #413: "그 자리" named nothing the dialog showed), the Open button by its key.
      const folder = data.path ? folderName(data.path) : null;
      const where = folder && !folder.includes('`') ? `\`${folder}\` 폴더에` : '그 자리에';
      return { title, file, body: `${where} 그 이름의 파일이 없습니다. 파일을 옮기거나 이름을 바꿨다면 제목 줄의 Open 단추(Ctrl+O)로 다시 골라 여세요.` };
    }
    case 'unreadable':
      return { title, file, body: '파일을 읽을 수 없습니다. 읽을 권한이 있는지, 다른 프로그램이 파일을 잠그고 있지 않은지 확인하세요.' };
    case 'libraryMissing':
      return {
        title, file,
        body: '이 회로가 쓰는 라이브러리 파일이 .circ에 적힌 자리에 없습니다. 아래 파일을 그 자리에 둔 뒤 다시 여세요.',
        detail: (data.missing ?? []).join('\n') || undefined,
      };
    case 'loadFailed':
      return { title, file, body: 'Logisim이 이 파일을 회로로 읽지 못했습니다. Logisim 회로 파일(.circ)인지, 원조 Logisim 2.7.1에서는 열리는지 확인하세요.', detail: e.message };
    case 'writeFailed':
      return { title, file, body: '그 자리에 쓸 수 없습니다. 폴더에 쓸 권한이 있는지 확인하고, Save As(Ctrl+Shift+S)로 다른 자리에 저장해 보세요.' };
    case 'templateFailed':
      return { title, body: '엔진이 빈 회로를 준비하지 못했습니다. 한 번 더 해 보고, 그래도 안 되면 조교에게 알려 주세요.' };
    default:
      return { title, file, body: '엔진이 이 파일을 다루지 못했습니다. 한 번 더 해 보세요.', detail: e.message };
  }
}

// A command the engine did not do: the command's name (English), then a Korean sentence.
export const ERR_READ_ONLY = 3;
export const ERR_SIM = 4;
export function commandError(command: string, e: FailedCall): string {
  const reason = (typeof e.data === 'object' && e.data !== null ? (e.data as { reason?: string }).reason : undefined);
  if (e.name === 'EngineGone') return `${command}: 엔진이 멈춰서 하지 못했습니다`;
  if (e.code === ERR_SIM) {
    if (reason === 'oscillating') return `${command}: 회로가 발진해서 시뮬레이션이 꺼져 있습니다. 회로를 고친 뒤 Reset을 누르세요`;
    if (reason === 'off') return `${command}: 시뮬레이션이 꺼져 있습니다. Reset을 누르세요`;
    if (reason === 'frozenPin') return `${command}: 서브회로 안의 입력 핀은 바깥 회로가 정합니다`;
    if (reason === 'running') return `${command}: 시뮬레이션이 켜져 있을 때는 할 수 없습니다. Ctrl+E 키로 끈 뒤 하세요`;
    return `${command}: 지금 시뮬레이션 상태에서는 할 수 없습니다`;
  }
  if (e.code === ERR_READ_ONLY) {
    if (reason === 'readOnly') return `${command}: 읽기 전용 파일입니다. Save As로 새 이름으로 저장한 뒤 고치세요`;
    // placing a part (the Components list, the search palette, a drop; N-12): the original's refusals
    if (reason === 'circular') return `${command}: 회로 안에 그 회로 자신을 놓을 수 없습니다`;
    if (reason === 'exclusive') return `${command}: 그 자리에는 이미 값을 내는 다른 부품이 있습니다`;
    if (reason === 'negativeCoord') return `${command}: 부품은 음수 좌표에 놓을 수 없습니다. 조금 오른쪽 아래에 놓으세요`;
    if (reason === 'cannotModify') return `${command}: 불러온 라이브러리의 회로는 고칠 수 없습니다`;
    return `${command}: 이 회로에서는 할 수 없습니다`;
  }
  return `${command}: 엔진이 하지 못했습니다`;
}
