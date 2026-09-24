# 1부터 10까지 더해 출력한다.
        .data
label:  .asciiz "sum="
        .text
        .globl main
main:   li    $sp, 0x7fffeffc
        li    $t0, 1
        li    $t1, 0
        li    $t2, 11
loop:   add   $t1, $t1, $t0
        addi  $t0, $t0, 1
        bne   $t0, $t2, loop
        la    $a0, label
        li    $v0, 4
        syscall
        move  $a0, $t1
        li    $v0, 1
        syscall
        li    $a0, '\n'
        li    $v0, 11
        syscall
        li    $v0, 10
        syscall
