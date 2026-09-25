# 기록 엔진 성능 측정용(C-01): 끝나지 않는 이중 반복. 매 반복 메모리 쓰기·읽기, 덧셈, 비교, 분기.
        .data
arr:    .space 400
        .text
        .globl main
main:   li    $t0, 0
outer:  li    $t1, 0
        la    $s0, arr
inner:  sw    $t1, 0($s0)
        lw    $t2, 0($s0)
        add   $t3, $t3, $t2
        addi  $s0, $s0, 4
        addi  $t1, $t1, 1
        slti  $t4, $t1, 100
        bne   $t4, $zero, inner
        addi  $t0, $t0, 1
        j     outer
