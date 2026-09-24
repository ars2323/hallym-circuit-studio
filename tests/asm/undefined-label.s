        .text
        .globl main
main:   beq   $zero, $zero, nowhere
        li    $v0, 10
        syscall
