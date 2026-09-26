# 진행 추적표(최종 완성 지시)

항목 ID마다 이슈 하나. 상태: 대기 / 진행 / 완료 / needs-human 대기(사유). 항목이 끝날 때마다 갱신한다.
컨텍스트가 끊기면 이 파일과 PLAN.md, CLAUDE.md, docs/DECISIONS.md만 읽고 이어간다.

**순서(지시 9절):** 0 준비 → 배선 마무리(W-01, W-02·S-03, S-01, S-02, S-04, W-03, W-05) → 2c 나머지(W-04, P-01, P-07, P-02, P-03, S-05~S-13) → 3단계(C) → 4단계(D) → 4b(P-04~P-06, E) → 배포(R-01~R-06) → 게이트(Q) → R-07 게시.

**0 준비:** 이슈 75개(#164~#238) 생성, 옛 이슈 27개는 ID 이슈로 옮기고 닫음. `.claude/agents/ui-reviewer.md`, `docs/UI-CHECKLIST.md`, CLAUDE.md 머지 규칙 갱신 — 완료(#239).

## S 화면 문제

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| S-01 | 칩이 선을 가림 | #164 | 완료 | #242 | LabelOverlay.redrawWires, LabelsTest.wiresUnderChipsAreDrawnAgainOnTop |
| S-02 | 팔 라벨과 선 겹침 | #165 | 완료 | #242 | 막대 반대쪽 팔 라벨, LabelsTest.armLabelsGoToTheFreeSideAwayFromTheArmWires, 15d |
| S-03 | 따라온 선의 군더더기 | #166 | 완료 | #241 | D-057, 남는 이동은 clutter 0(테스트) |
| S-04 | 끌기 직후 빠른 속성 창이 칩을 가림 | #167 | 완료 | #242 | 이동 뒤 조용한 선택, SafeMoveTest(S-04 단언), 15b |
| S-05 | 출력 핀 라벨 칩이 선 위에 겹침 | #168 | 완료 | #242 | 칩 배치가 선을 피함, LabelsTest.demoChipsStayOffWires, 03d |
| S-06 | 포트 이름 덧그림 과밀 | #169 | 완료 | #250 | D-066, PortLabelsTest(100% 숨김, 200%·마우스 오버에서 부품 바깥, 다른 글자 그대로, 원조 부품만, 캔버스 글꼴, 가운데에서 먼 쪽, 25% 읽힘), PortHoverGuiTest, 21a~21d |
| S-07 | Register 값 표시 겹침 | #170 | 완료 | #250 | 원조 그림 확인(D-066, 21a-pc-400-orig와 같은 겹침), 포크는 S-06 규칙으로 en·0을 바깥에 |
| S-08 | 기본 모양 서브회로의 포트 이름 | #171 | 완료 | #253 | D-069, DefaultAppearanceHelpTest(단추 조건, 포트 목록, 자동 모양 뒤, 인스턴스가 있을 때 거래, 도움말 자리), 22a~22c |
| S-09 | 찾기 결과의 내부 포트 이름 | #172 | 완료 | #251 | D-067, KindsTest.readablePortTitles, NameIndexTest(Splitter #1 (combined end)), EditMenusTest, ShortcutsTest, 09b·09d |
| S-10 | 화면 맞춤 여백 | #173 | 완료 | #252, 후속 #257 | D-068, ZoomMathTest.fitPlacementCentersBothAxes·originCapIsTheCenteringAmount, FitCenterGuiTest(가운데·누른 자리·원조 배율 조절·커서 배율·원점 한도), 02, 18o |
| S-11 | 왼쪽 패널 빈 공간 | #174 | 완료 | #256 | D-072, SidePanelTest(터널 목록, 미니맵 변환), SidePanelGuiTest(탭, 터널 누르기·다음, 편집 반영, 미니맵 누르기), 24a~24c |
| S-12 | 제어 핀 라벨 중복 | #175 | 완료 | #255 | D-070, LabelsTest.pinNamedByItsOwnTunnelHasNoChip·demoChipsStayOffWires(칩 4개)·tunnelColorLeavesThePortClear, 23a |
| S-13 | 400% 굵기 | #176 | 완료 | #254 | D-071, DiagMarksTest.borderWidthsAreExactOnScreen·wireHighlightWidthsAreExactOnScreen(25·100·400%, 2·4px와 3·6px), 14f |
| S-20 | 회귀 확인: 원조 도구 모음·탐색기 아이콘 줄 숨김, 위쪽 네 줄 | #177 | 완료 | #258 | TopRowsGuiTest(원조 Toolbar 둘 다 안 보임, 메뉴·도구 모음·파일 탭·회로 탭 네 줄 바로 아래 캔버스), ToolKeysTest.ctrlDigitsAreBoundOnTheRoot, 01·07a·10 |
| S-21 | 회귀 확인: 배율 표시 하나와 실제 배율 동기화 | #178 | 완료 | 기존 | ZoomStatusTest.followsTheModelWhoeverChangesIt·menuOffersStepsFitAndGrid, 07b·07c |
| S-22 | 회귀 확인: 스플리터 원조 "0-7" 표시와 팔 라벨 이중 표시 없음 | #179 | 완료 | 기존 | LabelFilterTest.splitterOriginalBitLabelsGoOnlyWhereArmLabelsAreDrawn, LabelsTest.filterRemovesOnlyTheOriginalLabels·splitterArmLabelsShowRangesAndNames, 03b·08a·08d |
| S-23 | 회귀 확인: MIPS 부품 포트 이름 안쪽 14px, 콘솔 출력 영역 | #180 | 완료 | #258 | MipsPortInsetTest(Instruction Memory·Data Memory·Stack·Console 포트 이름 14px 안쪽, 터널 글자와 안 겹침, Console 출력 칸과 떨어짐, 원조·캔버스 문맥 모두), 03e·11b~11e |
| S-24 | 회귀 확인: 터널 색 12색, 가까운 다른 이름은 다른 색 | #181 | 완료 | 기존 | LabelsTest.paletteIsLargeAndDistinct(12색)·nearbyNamesGetDifferentColors·tunnelColorsAreDeterministic, TunnelColorStoreTest, 03a~03e |
| S-25 | 회귀 확인: 우클릭 메뉴 순서와 요약 줄 단수·복수 | #182 | 완료 | #258 | MenuLayoutTest.orderIsSummarySpecificCommonDelete·summariesNameTheTarget·originalItemsLandInTheirGroups, UiLanguageTest.bitWidthsUseSingularForOne(개수 말 전체에 choice 확인, input·component·place 단수 추가), 04a~04e |
| S-26 | 회귀 확인: UI 언어(D-049) | #183 | 완료 | 기존 | UiLanguageTest 11개(appNameResourcesHaveNoHangul 등), MessagesPanelTest.namesAreEnglishWithSingularForOne, 06a·06b·11a·14c |
| S-27 | 회귀 확인: Stack은 used N B (peak)만 | #184 | 완료 | 기존 | StackRegionTest.usedBytesStartAtSpimInitialStackPointerAndKeepThePeak·stackDepthFollowsPushesAndPops, 11d·11f |
| S-28 | 회귀 확인: 찾기 결과 묶음과 위치 표시 | #185 | 완료 | 기존 | NameIndexTest.sameNamesAreGroupedWithCountsAndExpand·placesNameTheAttachedPort, 09a~09d |
| S-29 | 회귀 확인: 메시지 클릭 뒤 속성 패널·빠른 속성 창·캔버스 표시 | #186 | 완료 | #258 | AttrPanelGuiTest.attributePanelFollowsASelectionMadeFromMessages, QuickAttrsTest.quietSelectionHidesTheBarUntilTheUserClicks, LiveMarksGuiTest(실제 캔버스: 누르기 전·누른 뒤 굵게·선택 해제 뒤·25%·400%), DiagMarksTest, 14a~14f |
| S-30 | 회귀 확인: gateUndefined=error일 때만 빈 게이트 입력 알림 | #187 | 완료 | 기존 | StaticCheckTest.emptyGateInputsAreQuietWhenIgnored·emptyGateInputsAreReportedWhenError·savedErrorOptionIsRead, 14e |

## W 배선

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| W-01 | 결정적 길 찾기 | #188 | 완료 | #240 | D-056, SafeMoveTest 고정 표(3회 실행 동일) |
| W-02 | 따라온 선 정리 단계 | #189 | 완료 | #241 | D-057, SafeMoveTest(이동마다 clutter 0) |
| W-03 | 묶음 재배선 | #190 | 완료 | #241 | D-057 고무줄 후보와 선 없이 옮기는 9가지 표 |
| W-04 | 연결점과 넷(#82) | #191 | 완료 | #245 | D-061, WireMarksTest(교차·T·포트 위, 25~400% 화소, 값 색, 넷 강조·지우기), 16a~16c·15d |
| W-05 | 새 선 A.4 검사기 통일 | #192 | 완료 | #244 | WireGuardTest(기능별 통과·막힘, 모든 호출이 검사기 경유), D-059 |

## P 편집 흐름·서브회로·파일

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| P-01 | 영향 경로(#83) | #193 | 완료 | #246 | D-062, InfluenceTest(레지스터 멈춤·통과·사이클, 뒤, 깊이, 두 부품 사이, 터널, 클럭 제외, 스플리터 비트, 서브회로 출력 선택, 고리, 100번 결정성), InfluenceOverlayTest, 17a~17f |
| P-07 | Signal Flow 애니메이션(추가 지시, P-01 다음) | #243 | 완료 | (이 PR) | D-063, SignalFlowPathTest(tests/circ/flow 고정 기대값, 데모 PC·ALU·MUX, 100번 결정성), FlowPainterTest, FlowGuiTest(멈춤 조건·잔상·클릭 지연·일시정지·Reduce Motion), FlowPerformanceTest(docs/PERFORMANCE.md), 18a~18l·GIF |
| P-02 | 서브회로 인스턴스 안내(#84) | #194 | 완료 | #248 | D-064, InstancePathsTest(경로·상태·미리 보기·더하기·끼우기·순서 바꾸기·삭제·문구), InstanceBannerGuiTest, 19a~19e |
| P-03 | 탭 간 라이브러리(#85) | #195 | 완료 | #249 | D-065, OpenFileLibrariesTest(후보·자동 Load Library·되돌리기·순환 차단·끊길 연결·다시 열 때), LibraryFixturesTest(원조 jar가 상대 경로 라이브러리로 18 계산), LibrarySyncGuiTest(탭 끌어 놓기·저장 반영·Updated·Edit Original·포트 변경), 20a~20f |
| P-04 | 서브회로 포트 순서 끌어 바꾸기 | #196 | 완료 | (이 PR) | D-092, PortOrderTest(준 순서로 모양·격자·되돌리기, 순서 바꾸기 경계), 42a~42b |
| P-05 | 다른 .circ에서 서브회로 가져오기 | #197 | 완료 | (이 PR) | D-093, CircuitImportTest(딸린 것 먼저·이름 번호·인스턴스가 사본을 가리킴·사용자 모양·저장 후 원조 로더로 다시 열기·되돌리기), 43a~43b |
| P-06 | 나란히 보기·창 분리·탭 복원 | #198 | 완료 | (이 PR) | D-090, TabLayoutTest(분리·되돌리기·닫으면 해제·복원 목록·자리 글), TabsLayoutGuiTest(분리하면 둘 다 보임·나란히 반씩·되돌리면 겹침·복원 설정), 41a~41c |

## C 사이클 뷰(3단계)

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| C-01 | 기록 엔진 | #199 | 완료 | #259 | D-073, RecordingTest(실제 값·재실행·입력 바꿈·뒤 버리기·상한·체크포인트 줄이기), RecorderTest(원조 Simulator 틱·리셋·편집), RecordingPerformanceTest, PERFORMANCE.md |
| C-02 | 사이클 표 | #200 | 완료 | #260 | D-074, CycleModelTest(PC·.s 원래 줄·디스어셈블·줄 값), MipsTextTest(tests/asm 원래 줄·분기 라벨), CycleViewGuiTest, 25a·25b |
| C-03 | 열 클릭과 뒤로 가기 | #201 | 완료 | #260 | D-074, RecorderViewTest(지난 스텝 보기·지금으로 돌아오기·서브회로 안 시점·지난 스텝에서 진행·입력 바꿈·ref-mips factorial), CycleViewGuiTest(열 누르기·상태 표시줄·Next Cycle), 25c·25d |
| C-04 | Run Until | #202 | 완료 | #261 | D-075, RunUntilTest(PC·다음 jr·exit·줄 바뀜·최대 사이클·E 발생·PC 글 읽기), CycleViewGuiTest.runUntilButtonRunsAndReports, 26a~26c |
| C-05 | 레지스터 패널 | #203 | 완료 | #262 | D-076, RegisterFileTest(라벨 숫자·이름·위치 추정, 수동 대응, 저장·다시 열기, 되돌리기, 표시 없을 때 나열), CycleViewGuiTest.registerPanelWithAMarkedRegisterFile, 27a~27c·27e |
| C-06 | 메모리 패널(#98) | #204 | 완료 | #262 | D-076, MachineStateTest(factorial $sp·깊이 56·복귀 0·Stack 화살표·최고 수위·Data 라벨), StackRegionTest.panelAccessorsReadWithoutChanging, CycleViewGuiTest.memoryPanelShowsTheStack, 27d·27f |
| C-07 | 명령어 필드 색 | #205 | 완료 | #264 | D-078, FieldPathsTest(rs→RR1, rt→RR2, 합친 버스·RD1/RD2로 안 번짐, 형식에 없는 필드), InstructionPanelTest, CycleViewGuiTest.instructionTabShowsFieldColors, 29a~29c |
| C-08 | 버스 값 칩과 활성 경로 | #206 | 완료 | (이 PR) | D-079, BusValuesTest(진법·떠 있음·배치 폭·이름 없는 버스), ActivePathOverlayTest(MemtoReg 0/1/미확정), CycleViewGuiTest.activePathAndBusValues, 30a~30d. 함께: 사이클 표 빈 줄 안내 바탕(C-05 검토), 필드 띠가 부품 몸체를 칠하지 않게(C-07 검토) |
| C-09 | Console 탭과 .s 자동 재로드 | #207 | 완료 | #263 | D-077, ProgramReloadTest(바뀐 때만·되돌리기·오류는 옛 내용, Console 글·exit), ConsoleDemoTest, CycleViewGuiTest.consoleTabAndReloadWatcher, 28a~28c |
| C-10 | 사이클 뷰 테스트 | #208 | 완료 | (이 PR) | docs/TESTING.md "사이클 뷰(C-10)" 절(재생=재실행·뒤로 가기 뒤 진행·Run Until·레지스터 대응·factorial $sp·halt에서 멈춤), LabelsTest.chipsKeepAPixelAwayFromBodies, 28a |

## D 동적 진단(4단계)

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| D-01 | E·X 출처 추적 | #209 | 완료 | (이 PR) | D-080, OriginTraceTest(구동자 없음·충돌·서브회로 안팎·MUX 고른 입력·기록값 지난 스텝), DynamicCheckTest(E 한 번·원인 하나·Find E/X Origin), 31a~31e |
| D-02 | 진동 | #210 | 완료 | (이 PR) | D-081, FaultCollectionTest.oscillationReplacesTheStaticLoopAndOffersReset(정적 루프 → 진동 한 줄, Reset 단추, 리셋 뒤 걷힘), 32a~32b |
| D-03 | X 기록 감지 | #211 | 완료 | (이 PR) | D-080, DynamicCheckTest(en 떠 있음·D 떠 있음·값 그대로·정상 회로 0건·지난 스텝 다시 쓰기·스텝당 0.03ms) |
| D-04 | MIPS 부품 값 의존 검사(#41) | #212 | 완료 | (이 PR) | D-081, FaultCollectionTest(정렬·영역 밖·스택 한계·IMem 정렬·syscall), mipsMessagesUseTheBodyText, StackRegionTest·ConsoleTest(lib-mips 접근자), 32c~32d |
| D-05 | 메시지 클릭과 사이클 뷰 | #213 | 완료 | (이 PR) | D-080, CycleViewGuiTest.dynamicMessageGoesToItsCycleAndCause(사이클·서브회로 인스턴스·원인 선택), 31c |
| D-06 | 동적 고장 회로 모음 | #214 | 완료 | (이 PR) | D-081, tests/circ/faults 18개(정적 9·동적 4·MIPS 5), FaultCollectionTest.everyFaultCircuitGivesItsOneMessage |

## E 편의 기능(4b)

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| E-01 | N개 복제 | #215 | 완료 | (이 PR) | D-084, ArrangeTest(라벨 번호·N개 복제와 되돌리기 한 번·닿으면 거절), 35a~35c |
| E-02 | 정렬·같은 간격, 선택 필터 | #216 | 완료 | (이 PR) | D-084, ArrangeTest(정렬·같은 간격·이어진 부품은 두기·선택 필터), 35d~35e |
| E-03 | 버스 폭 표시와 선 색 범례 | #217 | 완료 | (이 PR) | D-086, BusStyleTest(버스만 굵게·넷마다 비트 수 자리·범례·색 뜻·설정), LabelsTest(값 없는 선은 색 줄 없음), 37a~37c |
| E-04 | 신호 그룹 색 | #218 | 완료 | (이 PR) | D-087, SignalGroupsTest(control 출력은 Control·정한 그룹 되돌리기·확장 정보 저장과 원조 바이트 동일·넷이 사라지면 지움·보기는 환경설정), 38a~38c |
| E-05 | Undo History | #219 | 완료 | (이 PR) | D-083, UndoHistoryTest(목록 차례·되돌리기·다시 실행), 34a |
| E-06 | Create Submission | #220 | 완료 | (이 PR) | D-085, SubmissionTest(.circ·.s·jar를 상대 경로로, 점검은 알리기만, 번들 jar 대신 넣기), 36a |
| E-07 | Export Image | #221 | 완료 | (이 PR) | D-085, ImageExportTest(PNG 배율·SVG XML·PDF 구조와 pdfinfo·고른 부분·글자 외곽선·잘라내기), 36b~36c |
| E-08 | 미니맵과 영역 메모 | #222 | 완료 | (이 PR) | 미니맵은 S-11(D-072), 영역 메모는 D-088, AreaMemosTest(둘레 상자·기본 상자·더하기·고치기·지우기·되돌리기·겹치면 안쪽·확장 정보 저장과 원조 바이트 동일·그리기), 39a~39d |
| E-09 | 단축키 설정 창 | #223 | 완료 | (이 PR) | D-083, KeyBindingsTest(기본 키·Shift 반대·바꾸기·겹침·기본으로·창 키 다시 달기), ShortcutsTest(? 표가 지금 키), 34b~34c |
| E-10 | 첫 실행 튜토리얼 | #224 | 완료 | (이 PR) | D-089, TourTest(두 언어 문구·영어 제목·말풍선 자리), TourGuiTest(실제 창에서 모든 단계의 대상·말풍선이 대상을 안 가림·닫으면 유리판 복구), 40a~40c |
| E-11 | About 창 | #225 | 완료 | (이 PR) | D-082, AppIdentityTest(이름·버전·엠블럼·캐릭터·탭 둘, LICENSE·NOTICE 번들), 33a~33b |
| E-12 | 앱 아이콘과 창 제목 | #226 | 완료 | (이 PR) | D-082, AppIdentityTest(아이콘 여섯 크기, 창 제목) |

## R 배포

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| R-01 | Windows 패키지 | #227 | 완료 | (이 PR) | D-094, tools/package-windows.ps1(jpackage zip+MSI, 파일 연결 없음), CI windows 잡 |
| R-02 | Windows 실제 실행 검증 | #228 | 완료 | (이 PR) | D-094, CI: 패키지 실행 파일 tty + tools/winsmoke/Smoke(창·.s·10사이클·PC 변화·100%·150% 화면), 아티팩트 windows-smoke |
| R-03 | 트랙 A 라이브러리 zip | #229 | 완료 | (이 PR) | D-094, CI track-a-java8(원조 2.7.1 + Temurin 8 + hcs-mips.jar로 demo-datapath tty), package-track-a.sh |
| R-04 | 자동 저장 복구 테스트 | #230 | 완료 | (이 PR) | D-094, RecoveryTest(강제 종료 뒤 목록·편집 복구·원조 로더·원본 불변) |
| R-05 | 문서 | #231 | 완료 | (이 PR) | docs/GUIDE-ko.md, docs/TA-GUIDE-ko.md, README 다운로드·빠른 시작·스크린샷 |
| R-06 | 릴리스 노트 | #232 | 완료 | (이 PR) | docs/RELEASE-NOTES.md(기능 요약·원조와의 차이·알려진 한계·설치) |
| R-07 | v1.0.0 공개 릴리스 게시 | #233 | 완료 | (이 PR) | 태그 v1.0.0(main 1310ef5), CI release 잡이 만든 draft를 공개로 게시: Windows zip·MSI, 트랙 A zip(windows·linux), hcs-mips.jar, hcs-asm(.exe), 안내 md. v0.1.0 draft 삭제. https://github.com/ars2323/hallym-circuit-studio/releases/tag/v1.0.0 |

## Q 품질 게이트

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| Q-01 | 게이트: 추적표 완료 | #234 | 완료 | (이 PR) | 모든 ID 완료(needs-human 대기 없음) |
| Q-02 | 게이트: 모든 CI 통과 | #235 | 완료 | (이 PR) | main 5caaa69 CI 성공(linux: 전체 테스트·guiTest·엔진 회귀·어셈블 일치·성능 상한, windows: 패키지·실행 검증, track-a-java8) |
| Q-03 | 게이트: 최종 스크린샷 세트 | #236 | 완료 | (이 PR) | https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release-3/README.md — 장면 01~43 전체 + 원조 비교, ui-reviewer 3인 분할 검토 위반 0건(1차 위반 1건은 D-095로 고치고 재촬영) |
| Q-04 | 게이트: main 전체 compat 검토 | #237 | 완료 | (이 PR) | compat-reviewer main 5caaa69 전체: 위반 0건, 확인 필요 0건(엔진 234파일 원본 일치·허용 패치 D-007 하나, vendor 269파일, 확장 정보 단일 경로, SPIM 프로세스 분리, 자산 49파일, 패키지 34개 모두 테스트) |
| Q-05 | 게이트: 진단 기대값 | #238 | 완료 | (이 PR) | StaticCheckTest·DynamicCheckTest(정상 회로 0건), FaultCollectionTest(고장 회로 18개 각 기대 메시지 한 건) — main CI 통과 |

## V v1.0.1 패치(v1.0.0 검토, 마일스톤 v1.0.1)

| ID | 제목 | 이슈 | 상태 | PR | 스크린샷·근거 |
| --- | --- | --- | --- | --- | --- |
| V-01 | 새 파일에서도 Hallym MIPS가 보이고 바로 쓰임 | #283 | 완료 | #293 | D-096, MipsShadowTest(7), 44a~44e |
| V-02 | 진단 문구 정확성(E 원인 종류, 내부 포트 이름 숨김) | #284 | 완료 | #294 | D-097, FaultCollectionTest 문구 기대 파일(ko·en)·내부 포트 이름 0건, 31a·31e |
| V-03 | 메시지를 누르면 원인이 사이클 표에 | #285 | 진행 |  |  |
| V-04 | 활성 경로는 가지만 칠함 | #286 | 대기 |  |  |
| V-05 | 같은 이름 파일 탭 구분 | #287 | 대기 |  |  |
| V-06 | Signal Flow 터널 호가 부품·라벨을 피함 | #288 | 대기 |  |  |
| V-07 | 빈 캔버스 안내와 예제 메뉴 | #289 | 대기 |  |  |
| V-08 | 상태 표시줄 PC·Mark as PC, Tunnels 외톨이 표시 | #290 | 대기 |  |  |
| V-09 | 스크린샷 실행기 위생과 데모 값 | #291 | 대기 |  |  |
| V-10 | v1.0.1 공개 릴리스 | #292 | 대기 |  |  |
