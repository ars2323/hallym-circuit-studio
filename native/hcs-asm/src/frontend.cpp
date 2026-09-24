/*
 * hcs-asm — Hallym Circuit Studio assembler front end for the SPIM core.
 * Copyright (c) 2026 AIAC Lab, Hallym University. BSD 3-Clause License, see LICENSE.
 */
#include "frontend.h"

#include <cstdarg>
#include <cstdio>
#include <cstdlib>

#include "spim.h"

bool bare_machine;
bool accept_pseudo_insts;
bool delayed_branches;
bool delayed_loads;
bool quiet;
char *exception_file_name = 0;
bool mapped_io;
int spim_return_value;

port message_out;
port console_out;
port console_in;

std::string *hcs_output_capture = 0;

static std::vector<std::string> collected_errors;

std::vector<std::string> take_core_errors() {
  std::vector<std::string> errors;
  errors.swap(collected_errors);
  return errors;
}

static std::string formatted(const char *fmt, va_list args) {
  char buffer[10000];
  vsnprintf(buffer, sizeof(buffer), fmt, args);
  return std::string(buffer);
}

void error(char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  collected_errors.push_back(formatted(fmt, args));
  va_end(args);
}

void run_error(char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  collected_errors.push_back(formatted(fmt, args));
  va_end(args);
}

void fatal_error(char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  std::string message = formatted(fmt, args);
  va_end(args);
  std::fprintf(stderr, "hcs-asm: SPIM core fatal error: %s\n", message.c_str());
  std::exit(2);
}

void write_output(port fp, char *fmt, ...) {
  if (hcs_output_capture != 0 && fp.i == message_out.i) {
    va_list args;
    va_start(args, fmt);
    hcs_output_capture->append(formatted(fmt, args));
    va_end(args);
  }
}

void read_input(char *str, int n) {
  if (n > 0) str[0] = '\0';
}

int console_input_available() { return 0; }

char get_console_char() { return 0; }

void put_console_char(char) {}
