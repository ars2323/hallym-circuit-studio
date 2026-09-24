# 의사 명령어는 SPIM과 똑같이 실제 명령어로 펼친다
        .data
val:    .word 0x12345678
        .text
        .globl main
main:   li    $t0, 5              # 작은 양수: ori 하나
        li    $t1, -5             # 작은 음수: addiu 하나
        li    $t2, 0x12345678     # 큰 값: lui + ori
        la    $t3, val            # lui (+ ori)
        move  $t4, $t0
        not   $t5, $t0
        neg   $t6, $t0
        blt   $t0, $t1, done      # slt + bne
        bgt   $t0, $t1, next      # slt + bne
next:   ble   $t0, $t1, done
        bge   $t0, $t1, done
        b     done
done:   li    $v0, 10
        syscall
