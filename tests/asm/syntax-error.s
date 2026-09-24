# 3번째 줄에 문법 오류가 있다. 파서는 첫 문법 오류에서 멈춘다.
        .text
main:   addi  $t0, $t0,
        li    $v0, 10
        syscall
