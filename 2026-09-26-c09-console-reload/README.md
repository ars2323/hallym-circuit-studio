# 2026-09-26 Console 탭과 .s 자동 재로드(C-09)

- 기준: feat/console-reload `9f29ff4` (main `608bb7a` 위)
- 관련 이슈: C-09 #207
- 회로: 사람이 그린 작은 회로 `tests/circ/console-demo.circ`(사이클마다 'A'+count를 print_char, count 6에서 exit)와 demo-datapath. 체크리스트 10.

### 28a-console-full.png
- console-demo를 exit까지 돌린 뒤 창 전체. 아래 Console 탭에 전체 출력 "ABCDEF"와 "-- exit --". 부품 몸통은 마지막 몇 줄만.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c09-console-reload/28a-console-full.png
### 28b-console-tab.png
- Console 탭만.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c09-console-reload/28b-console-tab.png
### 28c-reload-notice.png
- demo-datapath에 불러온 .s(임시 복사본 reload-demo.s)를 고쳐 저장한 뒤 상태 표시줄: "reload-demo.s 파일이 바뀌어 다시 불러오고 시뮬레이션을 리셋했습니다." 모달 창은 띄우지 않는다.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c09-console-reload/28c-reload-notice.png
