/*
 * hcs-asm — Hallym Circuit Studio assembler front end for the SPIM core.
 * Copyright (c) 2026 AIAC Lab, Hallym University. BSD 3-Clause License, see LICENSE.
 */
#include "disasm.h"

#include <cstdio>
#include <cstdlib>

// The core's headers have no include guards: include each exactly once.
#include "spim.h"
#include "string-stream.h"
#include "inst.h"
#include "reg.h"
#include "mem.h"

namespace {

std::string hex8(unsigned v) {
  char buf[9];
  std::snprintf(buf, sizeof(buf), "%08x", v);
  return buf;
}

// SPIM's printer output for INST at ADDR, after "[0x<addr>]\t" and the
// "0x<word>  " column (an unknown word has no word column), without the
// newline. The source comment is left out by printing a copy without it.
std::string spim_text(instruction *inst, mem_addr addr) {
  instruction copy = *inst;
  SET_SOURCE(&copy, NULL);
  str_stream ss;
  ss_init(&ss);
  format_an_inst(&ss, &copy, addr);
  char *s = ss_to_string(&ss);
  std::string line = s;
  std::free(s);
  const std::string head = "[0x" + hex8(addr) + "]\t";
  if (line.compare(0, head.size(), head) == 0) line = line.substr(head.size());
  const std::string word = "0x" + hex8((uint32)ENCODING(inst)) + "  ";
  if (line.compare(0, word.size(), word) == 0) line = line.substr(word.size());
  while (!line.empty() && (line[line.size() - 1] == '\n' || line[line.size() - 1] == '\r')) {
    line.erase(line.size() - 1);
  }
  return line;
}

}  // namespace

std::string disasm_listing(const std::vector<std::pair<std::string, unsigned> > &labels,
                           unsigned from, unsigned to) {
  std::string out;
  for (size_t i = 0; i < labels.size(); i += 1) {
    out += "label " + hex8(labels[i].second) + " " + labels[i].first + "\n";
  }
  for (mem_addr a = from; a < to; a += BYTES_PER_WORD) {
    instruction *inst = read_mem_inst(a);
    if (inst == 0) continue;
    out += hex8(a) + " " + hex8((uint32)ENCODING(inst)) + " " + spim_text(inst, a) + "\n";
  }
  return out;
}
