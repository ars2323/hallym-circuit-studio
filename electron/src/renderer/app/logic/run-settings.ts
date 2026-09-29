/* The window's settings are for this run only (the lab-PC rule: N-19,
   D-152, v2 지시 7절; Hallym MIPS does the same).  Lab PCs are shared: every
   student starts from the same screen, so nothing the student sets is read
   from or written to disk -- the next start has these defaults again.
   Where the window has a place to set something, it says so (RUN_ONLY).

   What the window lets the student set now, all of it in the window's
   memory (and Chromium's profile is this run's folder, removed after quit:
   src/main/run-folder.ts):

     zoom and pan       each circuit view's (app.ts views; canvas/view.ts)
     panel sizes        the four splitters, the bottom panel collapsed (app.ts
                        dragged, bottomCollapsed; logic/layout.ts)
     panel tabs         the tab on show in each panel
     Show Bus Widths    the Wire Colors legend (canvas/legend.ts), with the
                        overlays' Colors (Values / Groups), Bus Values and
                        Active Path under it (N-15, canvas/overlays/)
     Signal Flow        its right-click menu's Signal Flow on Click, Flow
                        Speed, Through Registers, Active Path Only, Reduce
                        Motion, Smooth (overlays controller.ts settings)
     clock speed        the toolbar's select (sim.run hz; its tooltip says RUN_ONLY)
     N Cycles count     the count given last (the N Cycles dialog's field; app.ts lastCycles)
     circuit tabs       each file's open circuits (logic/files.ts)
     Load Program…      the .hmx picked last for a file (main.ts programs)
     keys               the fourteen keys the student can change (logic/keys.ts,
                        Preferences › Keyboard, D-158)
     recent files       File › Open Recent: this run's files (main.ts recent, I-130)
     folds              a panel the window folded for the room, opened by hand
                        until the window's size changes (logic/layout.ts)

   Preferences (preferences.ts, D-158) shows them in one place and says
   RUN_ONLY at its top.  Radix choices are not settings (the Radix Probe's
   radix is an attribute of the student's circuit, saved in the .circ).  A
   new setting keeps to this: memory only, a default here, and RUN_ONLY where
   it is set (tests/unit/run-settings.test.ts looks for web storage in src/;
   tests/e2e/labpc.e2e.ts sets every one, quits and starts again). */

export const RUN_ONLY = '이번 실행에만 적용됩니다';

export const RUN_DEFAULTS = {
  hz: 1,              // the clock speed: v1's default (1 Hz), the engine's
  busWidths: true,    // Show Bus Widths: on, as v1
} as const;
