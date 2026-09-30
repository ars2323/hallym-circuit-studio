# Hallym Circuit Studio — Working instructions

The Micro-architecture lab tool for Hallym University. It runs the Logisim 2.7.1 engine unchanged, and the screen is rebuilt from scratch in Electron (Hallym Circuit Studio 2, D-132). **`PLAN.md` is the standard** for what to build and why. Read it before working, and where it conflicts with this file, follow PLAN.md's product decisions.

## 0. Scope this round

- The scope this round is to finish all of **v2 roadmap N-00 through N-28** (PLAN.md 9.2, the N table in `docs/PROGRESS.md`) and publish v2.0.0. New instructions from the user go into the A items of the same table.
- The screen is built with the same materials and the same flow as Hallym MIPS Simulator (Electron edition, reference tag v2.6.0). How you use it is almost the same as original Logisim, and the features are all of v1 (`docs/v1-feature-parity.md`).
- **The Swing screen is retired.** v1.0.3 was the last Swing release and it stays as tag `swing-final`. The Swing and FlatLaf code was deleted in N-27 (D-163). It is not brought back.
- **Verilog (PLAN.md chapter 7, roadmap stages 5–9) is not implemented.** Only the principles of PLAN.md 7.0 are kept. No Verilog parser, Yosys, or ANTLR dependency is added.

## 1. How we work: don't make the user do the work

- The user receives only results. Don't ask questions or request confirmation — decide the most reasonable way and record it.
- Record decisions one at a time in `docs/DECISIONS.md` (date, decision, reason, alternatives, test). **Only the coordinator session assigns D numbers** (D-166). Whoever works an item uses the number the coordinator session gave, and if there is no number yet, leaves the slot open until one is given. If you resolve an open item in PLAN.md, update PLAN.md too.
- Don't stop until the work is done. While waiting on CI, a build, or a review, do another item. If the context grows long, leave the progress state in `docs/PROGRESS.md` and memory, and continue from there.
- The only time to stop is **an irreversible decision where either choice is plausible**. Example: deploying to students, deleting an original file the user placed. In that case, do the preparation and leave only the decision. The public repository and the public release are already approved.
- Collect everything a human must do into **one GitHub issue** (#54, `needs-human` label) as a checklist. Example: confirming installation on lab PCs, professor's confirmation, notifying students of the rollout. Write the exact command as one line. Keep doing other work in the meantime.
- If the context breaks, continue by reading only `docs/PROGRESS.md`, PLAN.md, this file, and `docs/DECISIONS.md`.

## 2. Absolute rules

1. **Do not modify the Logisim simulation engine.** Don't change value propagation, net computation, or the behavior of existing parts. The target packages are `app/src`'s `com.cburch.logisim.circuit`, `.comp`, `.data`, `.instance`, `.std.*` and the `.file` loading rules. Add new features as listeners, new parts, or engine services (`kr.ac.hallym.hcs.engine.*`). If it cannot be avoided, isolate it in a single minimal patch. Then write the reason in `docs/DECISIONS.md` and prove behavior is unchanged with a regression test. Reflection is used only for reading. `tools/check-engine-unchanged.sh` and `tools/check-upstream-markers.sh` enforce this in CI.
2. **The engine is the authority. Edits are sent as intent and Logisim code carries them out.** The true state of the circuit model lives in the Java engine. The screen holds a copy and matches it to the diffs the engine sends. The screen only turns gestures into intent (`edit.*`) and sends it; the actual change (merging/splitting wires, connection points, undo history) is done by the engine using original Logisim's edit and tool code (D-134, D-146). That is why the resulting .circ matches the original. The screen does not fix up the model itself or build the .circ. New edit intents go into the recovery journal and are tested (D-142).
3. **Do not modify the originals under `vendor/`.** For now this is just the Logisim 2.7.1 jar (`vendor/logisim-2.7.1/`). `vendor/spim-9.1.24` was deleted by the user's decision (D-141, an exception limited to this folder). Keep build artifacts outside it. `tools/verify-vendor.sh` checks this.
4. **A .circ that uses no new parts is saved to be byte-compatible with the original 2.7.1.** Put any additional information in only through the namespace scheme (`<hcs:ext>`) of PLAN.md 7.0. Only information the user explicitly specified (tunnel color, signal group, area memo, splitter arm name) goes in here. A file that is only opened and saved does not change (D-149).
5. **The school logo and characters are used in their original form.** Don't redraw, recolor, resize, or add elements. Don't put the guideline PDF or the .ai originals into git (section 15).
6. **Don't mix SPIM code with GPL code.** This repository has no SPIM code (D-141). Assembling is done by Hallym MIPS, and this tool only reads its executable image (.hmx). When importing from Hallym MIPS, don't bring in files that came from SPIM (`src/core/op-table.ts`, `native/`, `src/core/*`, `src/sim/*` and files that depend on them) (D-133 item 5, `electron/tests/unit/origin.test.ts`). Keep SPIM-produced output only as test material (`tests/spim-oracle/LICENSE`).
7. **The tool gives a part library and editing tools, and reports only "a circuit that cannot work."** It does not judge, fix, or compare against a correct answer for a working but wrong circuit (values flow as 0/1 but the result is just wrong). It does not get involved in student design: computing the branch destination and initializing PC/`$sp` are the student circuit's job, and the tool only loads the executable image's machine code into memory as-is and emits the word at an address. The tool does not write to the student's registers (PLAN.md chapter 1, D-010, D-019, D-118).

## 3. Structure: two processes

```
electron/          # The screen. Electron main + renderer (TypeScript, no UI framework)
  src/main/        #   window, engine child process, JSON-RPC client, recovery file, run folder
  src/renderer/    #   app/ (panels, dialogs), canvas/ (drawing, part renderer registry), shared/ (shared with Hallym MIPS)
  tests/           #   unit (node --test), e2e (Playwright), fake-engine, fixtures
  tools/           #   build-ui, capture-screens, mutants, package, import-hmips, perf …
  docs/screens/    #   fixed-name screenshots and README
engine/            # Java engine server (hcs-engine.jar): headless Logisim 2.7.1 + kr.ac.hallym.hcs.engine.*
app/               # Fork of the Logisim 2.7.1 source (original plus // HCS: lines) and the GUI-less kr.ac.hallym.hcs.app.* (used by the engine)
lib-mips/          # Track A: MIPS parts JAR for original 2.7.1 (hcs-mips.jar, --release 8). The engine also bundles the same jar
vendor/logisim-2.7.1/   # The original jar, do not modify
assets/            # Derived logo, character, and font files
tests/             # circ (engine regression), parity (edit-equivalence goldens), hmx, mips, spim-oracle, disasm …
docs/              # DECISIONS, PROGRESS, engine-api (contract), research and design notes
tools/             # repository checks (vendor, assets, engine invariance, original markers), track A packaging
resources/, ref/   # user originals and reference clones. .gitignore targets
```

- **Electron main launches the engine.** There is one engine per app, and it opens several files (tabs) together. JSON-RPC 2.0 over stdio, one object per line. The contract for methods, identifiers, value strings, and notifications is `docs/engine-api.md` (D-133).
- **The renderer never talks to the engine directly.** contextIsolation, no renderer nodeIntegration, the API is opened only through preload. The window can only call from a fixed list of methods, and it cannot pass a path (D-135).
- **The engine is headless** (`java.awt.headless=true`). The installer bundles a JRE 21 shrunk with jlink (D-142). If the engine dies, it's reported and relaunched, and open files are recovered from the journal (D-142).
- **The fake engine** (`electron/tests/fake-engine/`) is a Node stand-in that knows only the contract. Screen e2e and screenshots use it. Where data matters, the answer is a fixture made by the real engine.
- **Drawing:** Canvas 2D; part shapes come from a single renderer registry of vector definitions (`electron/src/renderer/canvas/registry.ts`, D-137). Size and port positions are the engine's values as-is. Image export uses the same definitions too.

## 4. Rules for sharing with Hallym MIPS

- Bring in screen code, design values, fonts, icons, tools, and test scaffolding from Hallym MIPS Simulator's (`ars2323/hallym-mips-simulator`, same author, BSD-3-Clause) `electron/` and use them. The reference tag is **v2.6.0** (added user instruction 2026-09-29, A-07; before that it was v2.5.0, D-155). The source tag for files already imported is what `electron/ORIGIN.md` says. If you use something else, write the reason in DECISIONS.
- **Gather shared screen parts into one folder:** `electron/src/renderer/shared/` (dom, ui, ask, welcome, backdrop, notice, about, titlebar, splitter, registers, memory, inspector …).
- **Record the source of every imported file:** in `electron/ORIGIN.md`, note the path here, the path in Hallym MIPS, the method (`copy` = byte-identical, `derived` = modified), and what was changed. The license text goes in `electron/LICENSE.hallym-mips.txt`, and the notice is in NOTICE and About › Licenses.
- **To re-import:** `cd electron && node tools/import-hmips.ts [--tag <태그>]`. The target list is that file's `TAKEN`. `copy` overwrites, `derived` only reports what changed. After merging by hand, use `--record` to update `tools/hmips-sums.json`. `--check` runs every time in CI and `origin.test.ts`.
- **The .hmx spec and goldens are pinned at v2.4.0** (`docs/hmx.md`, `tests/hmx/hallym-mips-v2.4.0/`, D-138). The spec's source is Hallym MIPS `docs/hmx-format.md`. v2.6.0's spec is byte-identical to v2.4.0, and the 7 goldens differ only in the header's produced-by and assembled fields, so only reference addresses are moved and the test material is kept as is (A-07). Don't copy the full text.

## 5. Git and GitHub management

git and gh are installed and logged in. All repository management is done directly.

- **Repository:** `ars2323/hallym-circuit-studio`, public (D-017). Make sure the guideline PDF, `.ai`, `resources/`, and secret values never enter the history.
- **Milestones and issues:** v2 items each get one issue under the `v2.0.0` milestone. The N and A tables in `docs/PROGRESS.md` (ID | issue | status | PR | notes) are updated **only by the coordinator session** whenever an item finishes (D-166). Item PRs do not touch PROGRESS. Open questions become `question`-labeled issues, and when resolved, get the conclusion added and are closed.
- **Branches and PRs:** don't push directly to `main`. Create `feat/…`, `fix/…`, `docs/…`, `chore/…` branches and open a PR per unit of work. Write the PR body in Korean and add `Closes #N`.
- **Merge procedure:**
  1. Confirm every CI job is green. A skipped check does not count as passing (leave the reason and issue number).
  2. Run compat-reviewer (`.claude/agents/compat-reviewer.md`) on every PR before merging. It looks only at the diff and reports violations of the absolute rules in section 2 and untested feature changes.
  3. **PRs that change the screen** (PRs whose purpose is the screen, and any PR that changes images under `electron/docs/screens/` regardless of purpose) also run ui-reviewer (`.claude/agents/ui-reviewer.md`, `docs/UI-CHECKLIST.md`). Only one person reviews the changed screenshots (v2 instruction section 1: no three-way review, no full reshoot).
  4. If there are violations, fix them and run again. Merge only once there are 0 violations (0 "blocking" for ui-reviewer). Write the reasoning for "to check" items in the PR body.
  5. Merge with `gh pr merge --squash --delete-branch`.
- **Commits:** an English imperative one-line title (`Add Data Memory component`) with a body if needed. Commit often, in small units.
- **CI (`.github/workflows/ci.yml`):** first gate (`changes`), Linux build/unit test/engine regression/edit equivalence/repository checks (`linux`), mutation testing and a fixed identity-hash rerun (`linux-checks`, in parallel), track A jar on Java 8 (`track-a-java8`), screen unit/e2e (`electron`), bundled JRE and real-engine e2e (`runtime`, Linux and Windows), Windows installer and installed-app e2e (`setup-exe`, `setup-e2e`, `setup-upgrade`), release file rules (`release`). When each runs is D-166:
  - **The PR gate is the Linux jobs and `release`** (track A and the distribution file rules). Windows jobs run on push to main, tags, and manual dispatch. In a PR, they are skipped only when **every** changed file falls in the allow list (screen renderer, unit/e2e test files, engine/app/lib-mips source, test circuits, docs); if even one file is outside it, they run. The list and exceptions live in `ci.yml`'s `changes` job.
  - The parts of Windows-only install tests that depend on the screen and examples are caught on main. That's why the previous line has this rule.
  - `concurrency` cancels a prior run of the same PR when a new push arrives. Runs on main and tags are not cancelled.
  - **A Windows failure on main is fixed first.** The Windows job on main must be green before tagging alpha or v2.0.0.
  - **Flaky checks are quarantined.** Open an issue, add it to "Quarantined checks" in `docs/OPEN-ISSUES.md`, and remove it. All are restored before v2.0.0.
- **.gitignore:** `resources/`, `ref/`, `build/`, `.gradle/`, IDE files, agent worktrees.
- **Line endings:** goldens and specs that tests compare byte-for-byte (`tests/hmx/**`, `tests/parity/**`, `tests/circ/**`, `tests/mips/*.circ`, `tests/jarlib/**`, docs that tests read) are, in `.gitattributes`, marked `-text`. When you add a new byte-comparison file, add it here too (D-154).

## 6. Verification rules

- **Automated tests are the primary safety net.** Every behavior gets a test. A feature PR with no tests is not merged.
  - Java: engine API unit tests, engine regression (`tests/circ` checked against the standard 2.7.1 jar's `-tty`), byte compatibility (`OpenSaveParityTest`: add new engine paths to `screenOpens`, D-149), loader/disassembler/diagnostics/recording tests, PIT for new core packages.
  - Equivalence: edit equivalence (byte match against N-01 goldens, D-136/D-159), geometric equivalence (engine port positions = renderer ports, D-137), an e2e for every row of the interaction-equivalence table (`docs/interaction-parity.md`).
  - TypeScript: `node --test` unit tests, mutation testing for new logic (`npm run test:mutants`).
  - e2e (Playwright, fake engine): watch the main flows at FHD 100/125/150%. At least one real-engine e2e (`HCS_E2E_REAL_ENGINE=1`) per main flow.
  - Fill in the e2e columns of `docs/v1-feature-parity.md` and `docs/interaction-parity.md`.
- **Zero Messages on a normal circuit** is required on every PR.
- **Verify lightly.** No three-way review, no repeated full reshoots, no procedural proof. If verification takes longer than implementation on an item, stop, write down the cause, and continue with less.
- **Measure before fixing.** For problems like slowness, jitter, overlap, or size, measure first and record the value, then measure again the same way after fixing. Don't write "improved" without a measured value.
- **Expected results are fixed values.** Don't use threshold tests like "M out of N times." If something is flaky, remove the cause.

## 7. Screenshot convention

- Screenshots go in `electron/docs/screens/` under **fixed names**. No subfolders. Each round overwrites the same names.
- **Capture only with the tool:** `cd electron && xvfb-run -a -s '-screen 0 2400x1400x24' npm run screens` (`tools/capture-screens.ts`, fake engine, same code gives same pixels). Don't take screenshots by hand.
- Default is a maximized window on a 1920×1080 lab PC (CSS 1920×1032, 100%). No cursor, tooltip, hover, or focus, and no personal path is visible. 1.5MB or less per image.
- Keep a table in `electron/docs/screens/README.md` (file, what to look at, how to capture) and the capture conditions. When you add a new scene, add a row to the table. Commit only images whose content changed.
- **Links in reports and PRs use only raw URLs pinned to a commit SHA:** `https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/<커밋 SHA>/electron/docs/screens/<이름>.png`. Never link by branch name, and report only after pushing.
- v1's orphan branch `review-shots` and `docs/SCREENSHOTS.md` remain only as v1.0.x records. Don't post new screenshots there.

## 8. Report format

- **At the end of a work session:** short, in Korean. What's done, merged PRs, deployment (release URL, or "no release" with the reason, section 13), what's next, `needs-human` changes. If the screen changed, add a "Screenshots" section at the end (section 7 links).
- **Interim reports:** short, each time alpha is bumped. Release URL, what's done, 5–10 screenshots (one link per line plus one line of what to look at).
- **Final report (once, after v2.0.0):**
  - first line `커밋: <main SHA>`, next line the release URL
  - one line per N item (PR, decision number)
  - judgment calls made and the reasoning, and what was decided not to fix and why
  - verification as tables and numbers: test count, edit-equivalence matches / total, geometry-check count, tutorial steps × scaling, performance measurements, install/uninstall, hashes, v1 feature-parity items complete / total
  - time spent, OPEN-ISSUES closed / remaining
  - 5–10 screenshots (including side-by-side comparisons with Hallym MIPS: start screen, dialogs, empty states, tutorial cards, panel headers and tabs, status bar)
  - needs-human changes

## 9. Open issues list (OPEN-ISSUES)

- Things passed over so as not to block the release, and decisions that were made but whose follow-up work remains, go in `docs/OPEN-ISSUES.md`.
- ui-reviewer's **passed on** grade (gaps of 3px or less, alignment and similar shape polish) is not fixed and is recorded here instead. **Blocking** is fixed in that same PR (the verdict grades in `docs/UI-CHECKLIST.md`).
- When fixed, don't delete the line — change the status to "fixed (PR number)". When closing, write the PR number or the reasoning.
- Items that came out of the v1 screen are kept as "rebuilt for v2" and revisited against the v2 baseline in N-25. The goal is 0 remaining before v2.0.0.

## 10. Screen wording rules (N-20, D-135 item 14)

- **Names of things are English, sentences spoken to the student are Korean.** Names: panels, tabs, column headers, buttons, parts, ports, attributes, menus, facts in the status bar (`Ready`, `Cycle 2`, `Running (64 Hz)`, `3 messages`). Sentences: empty states, dialog bodies, bands, notification sentences in the status bar. Names follow the original 2.7.1 terms as-is, and terminology follows `docs/GLOSSARY.md`.
- The start card's choices stay in Korean, as in Hallym MIPS. A dialog title is Korean if it's a sentence, English if it's a name. Dialog buttons are names, so they're English, and the same command has the same name everywhere (Close, Cancel, Try Again, Discard).
- Don't use the Korean word "한림" ("Hallym") on screen (use Hallym University).
- Don't use the "~하면 됩니다" ("all you need to do is...") style.
- **Don't attach a particle right after a name.** Put the name after a colon or on its own line (`File: lab3.circ`).
- `word-break: keep-all` everywhere Korean text appears. Wrap English names inside a sentence in a no-wrap span (`codeText()`).
- Loader and file errors say what's wrong → what to do. If there's a line number: line number → what's wrong → what to do. Don't carry over the engine's developer-facing wording verbatim.
- Circuit diagnostics state one cause, the name the student gave, and facts and location only (PLAN.md 4.4). Only point at a close-by name when confident.
- Addresses, machine code, and values are written in D2Coding so 0 and O are distinguishable.
- Manage wording through Korean/English resources. Wording checks (grep, particle rules) are in CI.
- **Never put a character next to an error.** Hide every character on screen while an error dialog or band is showing. Don't use them in diagnostic messages either.

## 11. Lab PC rule (N-19, D-152)

- **Turn it off and on, and everything is back to default. The app remembers nothing.** Window size, layout, split positions, scale, recent files, keyboard shortcuts, tutorial progress, and settings are never written to disk. The settings window says "This applies only to this run."
- Maximize to fit the work area every time it starts.
- Chromium's user data goes in a temporary run folder each run (`<temp>/HallymCircuitStudio/run-<pid>-<시각>`) and is deleted when it ends (D-135 item 11, D-148 item 6).
- **The engine doesn't know about disk settings either.** It's an in-memory-only preferences setup that neither reads nor writes original Logisim's disk settings (D-134 item 10).
- **Crash recovery files go only next to the student's own file.** Only for files opened from disk, and only where that folder is writable. Ask only when opening that file. Deleted on save, close, or normal exit.
- Automated check: change everything, restart, and verify it's back to default. On Windows, zero change in the registry, `%APPDATA%`, `%LOCALAPPDATA%` (excluding the install folder), and `%TEMP%` before and after a run (`setup-e2e`, D-148 items 11–12).

## 12. Tutorial principles and baseline screen size

- **Baseline screen:** lab PCs are FHD at minimum. Must pass at 1920×1080 scaling 100%, 125%, 150%. The 1366×768 class is reference only. Screenshot default is CSS 1920×1032 (a maximized window minus the 48px taskbar).
- **The tutorial** follows the Hallym MIPS tutorial engine (N-18, tracks and steps in PLAN.md chapter 12).
  - Steps are one array. Each step declares a title and body (a single piece of text), the panel(s) to highlight, the box target(s), the kind (explanation or practice), what to wait for if practice, and whether it's a result step.
  - The card has "3 / 16", a quit option, a title, a body, a small character (at the card's far end), and [Previous]/[Next].
  - Explanation steps advance with [Next]. Practice steps have no [Next]; the last sentence of the body says what to do to advance. [Skip] appears after a few seconds and performs the action instead.
  - For practice with a visible result, only the card changes within the same step to point out the result, then it waits for [Next].
  - Double-layer emphasis: the whole target panel lit up, a box on the target, everything else darkened. Anything not the target isn't clickable, and keys that aren't required do nothing.
  - The card never covers the box. It makes the target actually visible (tab, scroll, zoom, collapsed panels) and logs what it did.
  - There's one example file per track. It opens read-only and is unloaded when done (unchanged on disk). Unsaved changes to the student's file are confirmed first, and restored when done.
  - Progress is not saved. Every start begins from choosing the course. Keys are → ← Esc (confirm then quit), and Esc during simulation stops it.
  - Judging is done from engine events (model, simulation, selection).
  - e2e checks five things at every step of both tracks: the target panel is lit entirely, the outside is exactly darkened, the box fits the target, a lit but non-target area isn't clickable, and the card doesn't cover the box.

## 13. Deployment rules

Order and commands follow `docs/release.md`.

- **A round that changes the app ends with a deployment.** For a round that changes only docs or tests, write "no release" and the reason in the report.

- **The Windows distributable is a single setup exe:** `HallymCircuitStudio-<버전>-win-x64-setup.exe` (electron-builder NSIS, per-user guided install, includes the engine and bundled JRE, D-148/D-155). No app zip or MSI is uploaded. What goes up alongside it is only track A files (`hcs-mips.jar`, the track A zip) and a guide PDF whose name contains `guide`. The rule is a single one (`electron/tools/release-assets.ts`) and CI checks it before upload, after upload, and at publish.
- There is no code signing. Add SmartScreen guidance to the release notes and the manual (More info → Run anyway) (`docs/install-windows-ko.md`).
- **Pre-release:** `v2.0.0-alpha.N` is a GitHub pre-release (not Latest). v2.0.0 goes up as a public release (Latest) once all items are done.
- **Old releases are not deleted.** v1.0.x releases and assets stay published as-is.
- **The tag goes on the commit that bumped the version.** Only if that commit's checks turn up a problem may it go instead on a green descendant commit that fixes only that problem without changing the version. In that case, rerun every check on the tagged commit, and note both commits (the version-bump commit and the tagged commit) in the report (D-154).
- **Size and hash are meaningful only for the published asset.** NSIS embeds the build timestamp, so the installer's bytes differ between builds. Don't record the size/SHA-256 of a file produced by a PR or main run as the release value. The values in notes and reports are those of the file attached to the release (fetched from the public URL) (D-154).
- **Post-release verification:** fetch the setup exe from the public URL and check the hash → install quietly on a clean Windows runner → start screen → both track tutorials with [Skip] to the end → load a sample .hmx → run factorial and check the Console → open ref-mips and N Cycles → zero registry/AppData change after exit → uninstall.
- The `.gitattributes -text` for byte-comparison goldens (section 5) must pass on Windows CI too before tagging.

## 14. Build environment

- **Java:** use Gradle (wrapper) and automatic toolchain downloads. Don't install a JDK with sudo. When calling `java` directly on the command line, export `JAVA_HOME` to the JDK 21 the toolchain fetched, first.
  - `lib-mips`: `--release 8`. Kept conservative since it's unknown which JRE the original 2.7.1 will run on on student PCs. A single jar with no external dependencies. Source shared between the two tracks is `lib-mips/src/shared/java` (D-125).
  - `app`, `engine`: JDK 21. `./gradlew :engine:stage :engine:test` builds the engine jar (`engine/build/stage/`) and tests, `:engine:runtime` builds the jlink JRE.
- **Node:** 22.18 or higher. `cd electron && npm ci`, `npm run typecheck`, `npm test`, `xvfb-run -a npm run e2e`, `npm run electron` (run with the engine from the source tree).
- **Running the original 2.7.1 jar:** `java -jar vendor/logisim-2.7.1/logisim-generic-2.7.1.jar file.circ -tty table` runs a circuit with no GUI. It's the reference engine for engine regression tests.
- Development and testing are done on Linux. Windows installers are built on CI's Windows runner.

## 15. Resource handling

Keep original zips as-is in `resources/` (gitignored) and commit only derived files to `assets/`. `tools/import-assets.py` produces them and `tools/verify-assets.sh` checks them against `assets/MANIFEST.sha256`.

- **Logo (`assets/hallym/logo/`):** use the originals from Hallym MIPS's `assets/ci/`. Manual-page JPGs have explanatory text mixed in, so they aren't used in the app. Follow Hallym MIPS's precedent for the app icon and logo placement.
- **Characters (`assets/hallym/character/`, Haram & Hari):** use English slug names (`haram-hari-greeting.png` etc.).
  - Use them sparingly: the start screen, tutorial cards, the About window, empty-canvas guidance, and question/summary dialogs.
  - Don't use them in error diagnostic messages or error dialogs/bands (section 10).
  - Follow the guidelines: no added elements, no changes to lines/proportions/colors, no complex or similarly-colored backgrounds (don't place directly over video), keep minimum margins.
  - The guideline PDF is a document that forbids external exposure, so it doesn't go into git.
- **Start screen video:** use Hallym MIPS v2.5.0's `start.webm`/`start.jpg` byte-for-byte. Record the source and handling in NOTICE and `electron/hallym-assets.md` (D-155).
- **Fonts:** the screen uses the Pretendard subset brought in from Hallym MIPS and D2Coding (woff2, OFL). The OTFs in `assets/fonts/pretendard/` are used by the installer's banner art (`electron/tools/installer-art.py`).
- Write third-party licenses in NOTICE. School identity elements are noted as "owned by Hallym University, not for commercial use, not an official university product."

## 16. Code principles (including readiness for Verilog)

- New Java code goes in `kr.ac.hallym.hcs.*`. Engine services in `kr.ac.hallym.hcs.engine.*`, GUI-less v1 code used by the engine in `app/`'s `kr.ac.hallym.hcs.app.*`, and code shared between the two tracks in `kr.ac.hallym.hcs.mips.*`. Keep changes to fork-original packages (`com.cburch.*`) to a minimum, and mark changed spots with a `// HCS:` comment.
- Diagnostics, the recording engine, and path computation take the circuit model as input, not the screen. Call them through the engine API and test them without a GUI.
- Keep one shared identifier utility for handling part, subcircuit, tunnel, and port names. Path notation is `datapath › PC`.
- Gather per-part-kind handling into a single registry: an engine-side registry, a screen-side part renderer registry (the spot a future Verilog mapping table attaches to).
- Manage user-facing wording through Korean/English resources. Diagnostic wording follows the principles of PLAN.md 4.4.
- A feature PR with no tests is not merged.
