# 2026-09-26 탭 간 라이브러리(P-03)

- 기준: feat/cross-tab-libraries `b086d30`
- 원조 2.7.1에는 탭도 탭 간 라이브러리도 없어 -orig 비교가 없다. 원조와의 호환은 원조 jar로 상대 경로 라이브러리를 쓴 회로를 돌리는 테스트(LibraryFixturesTest)로 보인다.
- 파일 탭을 캔버스로 끌어 놓기는 GUI 테스트(LibrarySyncGuiTest)로 확인한다.

### 20a-ripple-uses-adder.png
- ripple_carry.circ(tests/circ/libs의 복사본)가 1bit_adder.circ를 라이브러리로 쓴다: 트리에 1bit_adder, 인스턴스 fa0~fa3. 자리올림은 윗변 cin과 아랫변 cout을 잇는 세로선
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries/20a-ripple-uses-adder.png

### 20b-open-files-search.png
- 새 파일에서 Ctrl+K "adder": 기본 Adder 다음에 "1bit_adder Open Files · 1bit_adder.circ"(다른 탭의 회로). 한 칸 내려 고른 상태
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries/20b-open-files-search.png

### 20c-loaded-and-placed.png
- Enter: Load Library가 자동으로 되어 트리에 1bit_adder가 생기고 누른 자리에 인스턴스가 놓였다. 이어지지 않은 입력 포트는 Messages에
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries/20c-loaded-and-placed.png

### 20d-port-change-warning.png
- 1bit_adder 탭에서 출력 핀 s를 지우고 저장: "저장하면 ripple_carry.circ의 fa0, fa1, fa2, fa3 연결 20곳이 끊깁니다"(기본 모양이 줄어 모든 포트가 움직인다). 기본 Cancel(여기서는 취소 후 되돌림)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries/20d-port-change-warning.png

### 20e-updated-badge.png
- 속만 고쳐(NOT 게이트 추가) 저장: ripple_carry 탭에 " · Updated"(보고 있는 1bit_adder 탭에는 없다)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries/20e-updated-badge.png

### 20f-edit-original-menu.png
- ripple_carry의 fa0 우클릭: "Edit Original File (1bit_adder.circ)"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries/20f-edit-original-menu.png

