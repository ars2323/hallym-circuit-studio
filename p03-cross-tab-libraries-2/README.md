# 2026-09-26 탭 간 라이브러리(P-03) 재촬영

- 기준: feat/cross-tab-libraries `763de50`
- ui-reviewer 1차 위반 1건(20c 진단 테두리·점이 캡션 칩을 덮음)과 확인 필요(20f 메뉴가 인스턴스를 덮음, 라이브러리 회로에 모양 편집 항목, 대화 상자 제목 언어)를 반영했다.
  - 진단 테두리와 점은 라벨 칩 자리에 그리지 않는다(칩이 위).
  - 다른 파일의 회로에는 Edit Appearance·Auto Appearance 대신 Edit Original File만 둔다.
  - 대화 상자 제목은 영어 이름 "Connections Will Be Cut"(스크린샷에는 창 테두리가 없는 가상 화면이라 제목 줄이 보이지 않는다).
  - 20f는 인스턴스 오른쪽 아래 모서리를 눌러 메뉴가 부품을 덮지 않는다.
  - 20c의 속성 패널: 검색 팔레트로 놓은 부품은 고르지 않는다(기존 팔레트 동작, 도구는 Edit로 돌아온다).
- 원조 2.7.1에는 탭도 탭 간 라이브러리도 없어 -orig 비교가 없다. 원조와의 호환은 원조 jar로 상대 경로 라이브러리를 쓴 회로를 돌리는 테스트(LibraryFixturesTest)로 보인다.
- 파일 탭을 캔버스로 끌어 놓기는 GUI 테스트(LibrarySyncGuiTest)로 확인한다.

### 20a-ripple-uses-adder.png
- ripple_carry.circ(tests/circ/libs의 복사본)가 1bit_adder.circ를 라이브러리로 쓴다: 트리에 1bit_adder, 인스턴스 fa0~fa3. 자리올림은 윗변 cin과 아랫변 cout을 잇는 세로선
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries-2/20a-ripple-uses-adder.png

### 20b-open-files-search.png
- 새 파일에서 Ctrl+K "adder": 기본 Adder 다음에 "1bit_adder Open Files · 1bit_adder.circ"(다른 탭의 회로). 한 칸 내려 고른 상태
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries-2/20b-open-files-search.png

### 20c-loaded-and-placed.png
- Enter: Load Library가 자동으로 되어 트리에 1bit_adder가 생기고 누른 자리에 인스턴스가 놓였다. 이어지지 않은 입력 포트는 Messages에 있고, 진단 테두리는 캡션 칩 밑으로 지나간다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries-2/20c-loaded-and-placed.png

### 20d-port-change-warning.png
- 1bit_adder 탭에서 출력 핀 s를 지우고 저장: "저장하면 ripple_carry.circ의 fa0, fa1, fa2, fa3 연결 20곳이 끊깁니다"(기본 모양이 줄어 모든 포트가 움직인다). 기본 Cancel(여기서는 취소 후 되돌림)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries-2/20d-port-change-warning.png

### 20e-updated-badge.png
- 속만 고쳐(NOT 게이트 추가) 저장: ripple_carry 탭에 " · Updated"(보고 있는 1bit_adder 탭에는 없다)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries-2/20e-updated-badge.png

### 20f-edit-original-menu.png
- ripple_carry의 fa0 우클릭(오른쪽 아래 모서리): "Edit Original File (1bit_adder.circ)". 다른 파일의 회로라 모양 편집 항목은 없다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p03-cross-tab-libraries-2/20f-edit-original-menu.png

