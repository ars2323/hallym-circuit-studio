# 산술·논리 명령어 (R형, I형, 시프트)
        .text
        .globl main
main:   addi  $t0, $zero, 7
        addi  $t1, $zero, -3
        add   $t2, $t0, $t1
        sub   $t3, $t0, $t1
        addu  $t4, $t0, $t1
        subu  $t5, $t0, $t1
        and   $s0, $t0, $t1
        or    $s1, $t0, $t1
        nor   $s2, $t0, $t1
        xor   $s3, $t0, $t1
        slt   $s4, $t1, $t0
        sltu  $s5, $t1, $t0
        slti  $s6, $t1, 0
        andi  $s7, $t0, 0xff
        ori   $a0, $t0, 0x8000
        xori  $a1, $t0, 1
        lui   $a2, 0x1234
        sll   $a3, $t0, 4
        srl   $v1, $t1, 28
        sra   $v1, $t1, 1
        sllv  $t6, $t0, $t0
        mult  $t0, $t1
        mflo  $t7
        mfhi  $t8
        div   $t0, $t1
        mflo  $t9
        li    $v0, 10
        syscall
