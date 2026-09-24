# 산술·논리·시프트·즉값 명령. 결과 레지스터를 SPIM과 비교한다.
        .text
        .globl main
main:   li    $sp, 0x7fffeffc
        li    $t0, 0x12345678
        li    $t1, -7
        add   $s0, $t0, $t1
        addu  $s1, $t0, $t1
        sub   $s2, $t1, $t0
        subu  $s3, $t0, $t1
        and   $s4, $t0, $t1
        or    $s5, $t0, $t1
        xor   $s6, $t0, $t1
        nor   $s7, $t0, $t1
        slt   $t2, $t1, $t0
        sltu  $t3, $t1, $t0
        sll   $t4, $t0, 4
        srl   $t5, $t1, 3
        sra   $t6, $t1, 3
        li    $t7, 9
        sllv  $t8, $t0, $t7
        srlv  $t9, $t1, $t7
        srav  $v1, $t1, $t7
        andi  $a1, $t1, 0xff00
        ori   $a2, $t0, 0x00ff
        xori  $a3, $t0, 0xffff
        slti  $k0, $t1, -3
        sltiu $k1, $t1, 5
        lui   $fp, 0xabcd
        addiu $gp, $zero, 77
        mul   $ra, $t1, $t0
        move  $a0, $s0
        li    $v0, 1
        syscall
        li    $v0, 10
        syscall
