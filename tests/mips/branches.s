# beq/bne 앞·뒤, blt/bge/bgt/ble(slt + bne/beq), b(bgez), bltz/bgez를 지나가며 글자를 찍는다.
        .text
        .globl main
main:   li    $sp, 0x7fffeffc
        li    $t0, 3
        li    $t1, -2
        li    $v0, 11
back:   li    $a0, 'a'
        syscall
        addi  $t0, $t0, -1
        bne   $t0, $zero, back
        beq   $t0, $zero, fwd1
        li    $a0, 'X'
        syscall
fwd1:   li    $a0, 'b'
        syscall
        blt   $t1, $t0, fwd2
        li    $a0, 'X'
        syscall
fwd2:   bge   $t1, $t0, bad
        bgt   $t0, $t1, fwd3
bad:    li    $a0, 'X'
        syscall
fwd3:   ble   $t0, $t1, bad
        li    $a0, 'c'
        syscall
        bltz  $t1, fwd4
        li    $a0, 'X'
        syscall
fwd4:   bgez  $t1, bad
        b     done
        li    $a0, 'X'
        syscall
done:   li    $a0, 'd'
        syscall
        li    $v0, 10
        syscall
