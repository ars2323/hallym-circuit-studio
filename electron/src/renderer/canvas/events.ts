/* The Canvas's events on the window (N-15, D-151), so that code that draws
   over the circuit or acts on what is chosen -- the overlays (overlays/),
   and the editing tools of N-08 -- meet in one shape without calling into
   each other:

     hcs:selection  {fileId, circuitId, ids}  what is chosen on the Canvas
                    now (component and wire ids of the circuit on show; []
                    when nothing is), sent whenever that changes -- also
                    when another circuit is shown.

     hcs:tool       {tool}  the Canvas tool in use (Edit, Poke, Wire …, the
                    toolbar's names), sent when it changes.

   Whoever changes the selection or the tool sends it (the Canvas's click
   today, the Edit tool's gestures and the toolbar later); whoever needs it
   listens. */

export interface SelectionDetail { fileId: string; circuitId: string; ids: string[] }

export const SELECTION = 'hcs:selection';

export function emitSelection(d: SelectionDetail): void {
  window.dispatchEvent(new CustomEvent<SelectionDetail>(SELECTION, { detail: { ...d, ids: [...d.ids] } }));
}

export function onSelection(listener: (d: SelectionDetail) => void): () => void {
  const f = (e: Event) => listener((e as CustomEvent<SelectionDetail>).detail);
  window.addEventListener(SELECTION, f);
  return () => window.removeEventListener(SELECTION, f);
}

export const TOOL = 'hcs:tool';

export function emitTool(tool: string): void {
  window.dispatchEvent(new CustomEvent<{ tool: string }>(TOOL, { detail: { tool } }));
}

export function onTool(listener: (tool: string) => void): () => void {
  const f = (e: Event) => listener((e as CustomEvent<{ tool: string }>).detail.tool);
  window.addEventListener(TOOL, f);
  return () => window.removeEventListener(TOOL, f);
}
