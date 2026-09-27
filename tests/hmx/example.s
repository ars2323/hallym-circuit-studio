# 실행 이미지 명세 예시(docs/hmx.md)의 원본. "Hello MIPS"를 찍고 끝난다.
        .data
str:    .asciiz "Hello MIPS\n"
        .text
        .globl main
main:   la    $a0, str
        li    $v0, 4
        syscall
        li    $v0, 10
        syscall
