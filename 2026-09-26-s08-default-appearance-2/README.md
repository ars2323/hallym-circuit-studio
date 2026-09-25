# 2026-09-26 기본 모양 서브회로 안내(S-08) 재촬영

- 기준: feat/default-appearance-help `fceda2b`
- ui-reviewer 1차 위반 1건(22b 도움말이 캡션 칩 "blk"와 상자 모서리를 가림)을 반영했다. 편집 캔버스의 마우스 오버 도움말을 마우스 바로 아래가 아니라 부품 오른쪽 위(모자라면 왼쪽)에 띄운다.
- 확인 필요 항목:
  - 22c에만 빨간 진단 테두리가 있는 것은 모양이 바뀌어 진단을 다시 돌렸기 때문이다. 22a·22b는 회로를 만든 직후라 진단이 아직 돌지 않았다. 진단은 편집 뒤에 돈다.
  - 22c의 사용자 모양은 .circ에 저장되는 원조 모양이다. 원조와의 호환은 AutoAppearanceTest.originalLogisimReadsTheSamePorts가 원조 jar로 확인한다.
  - 이 장면은 200퍼센트만 찍었다. 빠른 속성 창과 도움말은 화면 크기 UI라 배율과 무관하다.
- 새 파일에 포트 넷(A, B, Sel → Result)짜리 회로 blk를 만들어 main에 놓고 200%로 찍었다. 원조 2.7.1에는 빠른 속성 창과 마우스 오버 정보가 없어 -orig 비교가 없다. 기본 모양 상자 자체는 원조 그림이다.

### 22a-quick-bar-auto-appearance.png
- blk를 고르면 빠른 속성 창 끝에 "Auto Appearance" 단추(파란 글씨, 다른 파일의 회로에는 없다)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s08-default-appearance-2/22a-quick-bar-auto-appearance.png

### 22b-hover-port-list.png
- 마우스를 올리면 부품 오른쪽에 도움말 "in: A, B, Sel · out: Result"(부품과 캡션 칩을 가리지 않는다)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s08-default-appearance-2/22b-hover-port-list.png

### 22c-after-auto-appearance.png
- 단추를 누른 뒤: 포트 이름이 보이는 사용자 모양. 빨간 테두리는 아무 데도 잇지 않은 입력 포트의 진단 표시다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s08-default-appearance-2/22c-after-auto-appearance.png
