# 재귀 팩토리얼. 호출마다 $ra와 인자를 스택에 8바이트 넣고 복귀할 때 꺼낸다.
        .data
msg:    .asciiz "6! = "
        .text
        .globl main
main:   li    $sp, 0x7fffeffc
        li    $a0, 6
        jal   fact
        move  $s0, $v0
        la    $a0, msg
        li    $v0, 4
        syscall
        move  $a0, $s0
        li    $v0, 1
        syscall
        li    $v0, 10
        syscall

fact:   addi  $sp, $sp, -8
        sw    $ra, 4($sp)
        sw    $a0, 0($sp)
        bne   $a0, $zero, recurse
        li    $v0, 1
        addi  $sp, $sp, 8
        jr    $ra
recurse:
        addi  $a0, $a0, -1
        jal   fact
        lw    $a0, 0($sp)
        lw    $ra, 4($sp)
        addi  $sp, $sp, 8
        mul   $v0, $a0, $v0
        jr    $ra
