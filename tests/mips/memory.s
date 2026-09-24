# .data 배열을 lw로 더하고 sw로 결과와 뒤집은 배열을 쓴다. 문자열은 print_string으로.
        .data
arr:    .word 3, -1, 40, 7, 1000
n:      .word 5
total:  .word 0
rev:    .space 20
hello:  .asciiz "Hallym 회로 "
        .text
        .globl main
main:   li    $sp, 0x7fffeffc
        la    $s0, arr
        lw    $s1, n
        li    $t0, 0            # i
        li    $t1, 0            # sum
        la    $s2, rev
        sll   $t5, $s1, 2
        add   $s3, $s2, $t5     # rev 끝
sumloop:
        sll   $t2, $t0, 2
        add   $t3, $s0, $t2
        lw    $t4, 0($t3)
        add   $t1, $t1, $t4
        addi  $s3, $s3, -4
        sw    $t4, 0($s3)
        addi  $t0, $t0, 1
        slt   $t6, $t0, $s1
        bne   $t6, $zero, sumloop
        sw    $t1, total
        la    $a0, hello
        li    $v0, 4
        syscall
        lw    $a0, total
        li    $v0, 1
        syscall
        li    $v0, 10
        syscall
