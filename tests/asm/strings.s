# .data 문자열과 여러 지시어 (한글 주석은 UTF-8)
        .data
hello:  .asciiz "Hello, MIPS!\n"
raw:    .ascii  "ab"
        .align 2
bytes:  .byte 1, 2, 3, 0xff
halves: .half 0x1234, -1
words:  .word 7
gap:    .space 8
tail:   .word 0xdeadbeef
        .text
        .globl main
main:   la    $a0, hello
        li    $v0, 4
        syscall
        li    $a0, 42
        li    $v0, 1
        syscall
        li    $a0, 'A'
        li    $v0, 11
        syscall
        li    $v0, 10
        syscall
