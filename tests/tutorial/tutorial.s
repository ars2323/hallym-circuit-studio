# assemble: no exception handler
# The Computer Architecture tutorial's program (Hallym Circuit Studio N-18, D-161): Hallym MIPS's data.s, assembled
# without the exception handler, so the image holds only these words -- no start-up code -- and main, at 0x00400000,
# is its entry.  It sums an array of words, prints a string and the sum, and ends itself (li $v0, 10; syscall).
        .data
msg:    .asciiz "sum = "
        .align 2
nums:   .word 3, 5, 7, -1
count:  .word 4
        .text
        .globl main
main:   la    $s0, nums
        lw    $s1, count
        li    $t0, 0
next:   lw    $t1, 0($s0)
        add   $t0, $t0, $t1
        addi  $s0, $s0, 4
        addi  $s1, $s1, -1
        bnez  $s1, next
        la    $a0, msg
        li    $v0, 4
        syscall
        move  $a0, $t0
        li    $v0, 1
        syscall
        li    $v0, 10
        syscall
