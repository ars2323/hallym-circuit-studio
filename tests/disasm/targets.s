# Branches and jumps to labels: SPIM prints [label] after a jump and [label-0x<pc>] after a
# branch (tools/gen-disasm-golden.sh, D-127). Offsets forward, backward and zero.
	.text
	.globl main
main:	j	fwd1
back:	jal	callee
	beq	$t0, $t1, back
	bne	$zero, $ra, fwd1
self:	beq	$0, $0, self
	blez	$a0, back
	bgtz	$s7, fwd2
	bltz	$v1, main
	bgez	$31, fwd2
	bltzal	$t2, callee
	bgezal	$zero, callee
fwd1:	beql	$t0, $t1, back
	bnel	$t0, $0, fwd2
	blezl	$t3, fwd1
	bgtzl	$t4, fwd2
	bltzl	$t5, back
	bgezl	$t6, fwd2
	bltzall	$t7, callee
	bgezall	$s0, callee
fwd2:	bc1f	fwd3
	bc1t	back
	bc1f	3, fwd3
	bc1t	7, fwd2
	bc1fl	fwd3
	bc1tl	2, back
	b	fwd3
	bal	callee
	beqz	$t0, back
	bnez	$t1, fwd3
	blt	$t0, $t1, fwd3
	bge	$t0, $t1, back
	bgt	$t0, $t1, fwd3
	ble	$t0, $t1, back
	bltu	$t0, $t1, fwd3
	bgeu	$t0, $t1, back
	bgtu	$t0, $t1, fwd3
	bleu	$t0, $t1, back
	beq	$t0, 5, fwd3
	bne	$t0, -1, back
fwd3:	jal	main
	j	back
	j	fwd3
callee:	jr	$ra
	jalr	$t9
	jalr	$ra, $t9
