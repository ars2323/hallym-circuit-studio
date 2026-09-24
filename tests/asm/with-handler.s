# -exception: QtSpim 기본값처럼 예외 처리기를 먼저 올리면 main이 0x00400024로 밀린다
        .text
        .globl main
main:   li    $v0, 10
        syscall
