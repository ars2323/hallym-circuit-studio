/*
 * hcs-asm tests — Copyright (c) 2026 AIAC Lab, Hallym University. BSD 3-Clause License, see LICENSE.
 *
 * Forced into a test-only compile of the SPIM core's inst.cpp (never into the
 * shipped hcs-asm, vendor/ is not changed): qsort becomes hcs_qsort_ties, which
 * orders elements with equal keys by HCS_QSORT_TIES. SPIM sorts its opcode
 * tables with qsort, and qsort may order equal keys in any way (glibc keeps
 * table order, the Windows C runtime does not). A disassembler golden must not
 * depend on that order (D-127).
 */
#ifndef HCS_QSORT_TIES_H
#define HCS_QSORT_TIES_H
#include <stdlib.h>
#ifdef __cplusplus
extern "C"
#endif
void hcs_qsort_ties(void *base, size_t n, size_t size, int (*cmp)(const void *, const void *));
#define qsort hcs_qsort_ties
#endif
