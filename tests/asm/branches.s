# beq/bne 앞·뒤 분기: 교재 정의 목적지 = PC+4 + offset×4
        .text
        .globl main
main:   li    $t0, 0
        li    $t1, 5
loop:   addi  $t0, $t0, 1          # 뒤로 가는 분기의 목적지
        bne   $t0, $t1, loop       # 뒤로: offset = -2
        beq   $t0, $t1, skip       # 앞으로: offset = 1
        addi  $t2, $zero, 99
skip:   beq   $zero, $zero, far    # 앞으로 여러 줄
        nop
        nop
        nop
far:    bne   $t0, $zero, main     # 뒤로 멀리
        li    $v0, 10
        syscall
