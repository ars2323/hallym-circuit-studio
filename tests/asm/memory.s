# lw/sw와 .data 워드
        .data
arr:    .word 10, 20, 30, 40
out:    .word 0
        .text
        .globl main
main:   la    $s0, arr
        lw    $t0, 0($s0)
        lw    $t1, 4($s0)
        lw    $t2, 12($s0)
        add   $t3, $t0, $t1
        add   $t3, $t3, $t2
        sw    $t3, 16($s0)
        addi  $s1, $s0, 16
        lw    $t4, -4($s1)
        sw    $t4, 0($s1)
        lw    $t5, out
        li    $v0, 10
        syscall
