**사용법 (한국어): [Hallym Circuit Studio 학생 안내](https://github.com/ars2323/hallym-circuit-studio/blob/main/docs/GUIDE-ko.md)** — 내려받기, 설치, Windows 경고 창 넘기기, 첫 실행, 자주 막히는 곳.

<!-- Notes for a new release: copy this file to docs/releases/<version>.md and fill it in (docs/release.md).
     Write in English. The only Korean is the first line, the Korean user-guide link, and Korean screen text quoted
     with its English meaning. The CI release job appends each file's SHA-256 after these notes and makes a draft.
     Delete these comment lines. -->

Hallym Circuit Studio <version>, <one or two sentences, for students: what this version is, and which version to use for coursework>.

## Download (Windows 64-bit)

| File | What |
| --- | --- |
| `HallymCircuitStudio-<version>-win-x64-setup.exe` | The Windows installer (this one file is all you need) |
| `hcs-mips.jar`, `hcs-mips-<version>-windows.zip` | The MIPS part library for original Logisim 2.7.1 (track A) |
| `hallym-circuit-studio-GUIDE-ko.pdf` etc. | The user guide |

- Download the installer and run it. After the progress screen comes the "설치가 완료되었습니다" ("Installation complete") screen; leave **지금 실행하기** ("Run now") checked and click **마침** ("Finish") to open it right away. It needs no administrator rights and no Java. After that, open it from the Start menu: **Hallym Circuit Studio**.
- **If a "Windows protected your PC" window appears** (on Korean Windows: "Windows의 PC 보호"), click *More info*, then *Run anyway* (Korean Windows: **추가 정보** → **실행**). Microsoft Defender SmartScreen blocks it because the installer is not code-signed. You can check the downloaded file against the SHA-256 below.
- On a PC where 1.0.x was installed with the MSI, the old installation is removed quietly during setup. A 1.0.3 zip folder is left as it is.
- Installing does not change which program opens `.circ` files. Settings are not remembered, so every start uses the defaults (the lab PC rule).

## What you will see that is different

- <what changed, for students>

## Known limits

- <limits>

## Going back

- [<previous version>](https://github.com/ars2323/hallym-circuit-studio/releases/tag/v<previous version>), the previous release.
- [1.0.3](https://github.com/ars2323/hallym-circuit-studio/releases/tag/v1.0.3), the previous edition (the Swing edition).
- To uninstall: in Settings › Apps, remove **Hallym Circuit Studio <version>**.

## Source and licenses

The source is this repository. The project is under the GNU General Public License, version 2 or later, as Logisim 2.7.1 is. [NOTICE](https://github.com/ars2323/hallym-circuit-studio/blob/main/NOTICE) lists every third-party component, and the Hallym University marks, characters and promotional video, which are not covered by the project's license.

This is a personal project by Hakhyeon Kim (AIAC Lab, Hallym University), not an official product of Hallym University.
