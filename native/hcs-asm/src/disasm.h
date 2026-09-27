/*
 * hcs-asm — Hallym Circuit Studio assembler front end for the SPIM core.
 * Copyright (c) 2026 AIAC Lab, Hallym University. BSD 3-Clause License, see LICENSE.
 *
 * -disasm: the text segment as SPIM's own instruction printer shows it. The
 * output is the golden that the Java disassembler (kr.ac.hallym.hcs.mips.disasm)
 * is compared with (tests/disasm/, D-127).
 */
#ifndef HCS_ASM_DISASM_H
#define HCS_ASM_DISASM_H

#include <string>
#include <utility>
#include <vector>

/*
 * One "label <addr> <name>" line per label (as given), then one
 * "<addr> <word> <text>" line per instruction in the text segment between
 * FROM and TO. <text> is what SPIM's format_an_inst() prints after the machine
 * word, without the source-line comment. Addresses and words are 8 lower-case
 * hex digits.
 */
std::string disasm_listing(const std::vector<std::pair<std::string, unsigned> > &labels,
                           unsigned from, unsigned to);

#endif
