# 기록 엔진 재실행 격리 테스트(C-01): buf에 "A"를 쓰고 잠시 돈 뒤 "B"로 바꿔 출력한다.
# 재실행(지난 사이클 복제본)이 실제 시뮬레이션의 메모리 등록을 덮어쓰면 "A"가 나온다.
        .data
buf:    .word 0
        .text
        .globl main
main:   la    $s0, buf
        li    $t0, 65
        sw    $t0, 0($s0)
        li    $t1, 0
        li    $t2, 20
spin:   addi  $t1, $t1, 1
        bne   $t1, $t2, spin
        li    $t0, 66
        sw    $t0, 0($s0)
        la    $a0, buf
        li    $v0, 4
        syscall
        li    $v0, 10
        syscall
