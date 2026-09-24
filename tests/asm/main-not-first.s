# main이 .text의 처음이 아니면 경고한다(PC 시작값은 학생이 회로에서 정한다)
        .text
helper: jr    $ra
        .globl main
main:   jal   helper
        li    $v0, 10
        syscall
