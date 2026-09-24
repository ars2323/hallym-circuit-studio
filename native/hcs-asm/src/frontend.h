/*
 * hcs-asm — Hallym Circuit Studio assembler front end for the SPIM core.
 * Copyright (c) 2026 AIAC Lab, Hallym University. BSD 3-Clause License, see LICENSE.
 *
 * The SPIM core (CPU/) expects its front end to define a few globals and the
 * error/output hooks declared in spim.h. frontend.cpp does that for a
 * command-line tool: errors are collected, output is dropped unless captured.
 */
#ifndef HCS_ASM_FRONTEND_H
#define HCS_ASM_FRONTEND_H

#include <string>
#include <vector>

/* Messages passed to error() and run_error() since the last call. */
std::vector<std::string> take_core_errors();

/* While non-null, write_output() to message_out appends here. */
extern std::string *hcs_output_capture;

#endif
