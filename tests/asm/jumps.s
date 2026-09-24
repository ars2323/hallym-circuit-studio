# j, jal, jr: 함수 호출과 복귀
        .text
        .globl main
main:   li    $a0, 3
        jal   square
        move  $s0, $v0
        j     end
square: mult  $a0, $a0
        mflo  $v0
        jr    $ra
end:    li    $v0, 10
        syscall
