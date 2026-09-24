/*
 * hcs-asm — assemble a MIPS .s file with the unmodified SPIM 9.1.24 core and
 * print the result as one JSON object (docs/hcs-asm.md).
 * Copyright (c) 2026 AIAC Lab, Hallym University. BSD 3-Clause License, see LICENSE.
 *
 * Exit status: 0 assembled, 1 assembly errors (JSON still printed),
 * 2 usage or I/O error (message on stderr).
 */
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <set>
#include <sstream>
#include <string>
#include <vector>

#if defined(_WIN32)
#include <fcntl.h>
#include <io.h>
#include <windows.h>
#elif defined(__linux__)
#include <unistd.h>
#endif

#include "frontend.h"

// The core's headers have no include guards: include each exactly once.
#include "spim.h"
#include "string-stream.h"
#include "spim-utils.h"
#include "inst.h"
#include "reg.h"
#include "mem.h"
#include "sym-tbl.h"
#include "scanner.h"
#include "parser.h"
#include "data.h"
#include "version.h"

static const char *HCS_ASM_VERSION = "0.1.0";

namespace {

struct Options {
  std::string input;
  bool pseudo = true;
  bool exception = false;
  std::string exception_file;  // empty: exceptions.s next to the executable
};

struct TextWord {
  mem_addr addr;
  uint32 word;
  int line;  // 0: unknown
  std::string source;
  bool handler;  // from the exception handler, not the input file
};

struct Label {
  std::string name;
  mem_addr addr;
  bool global;
};

struct Message {
  int line;  // 0: not tied to a line
  std::string text;
  std::string context;  // the source line the parser was reading, if given
};

void usage(FILE *out) {
  std::fprintf(out,
      "usage: hcs-asm [options] <file.s>\n"
      "Assemble <file.s> with the SPIM %s core and print JSON on stdout.\n"
      "\n"
      "The machine code is exactly what QtSpim and Hallym MIPS produce with their\n"
      "default settings (extended machine, delayed branches off).\n"
      "\n"
      "options (names follow the spim command line):\n"
      "  -pseudo / -nopseudo  accept pseudo instructions (default: -pseudo)\n"
      "  -exception           load the exception handler (default off)\n"
      "  -noexception         do not load the exception handler (default)\n"
      "  -exception_file <f>  exception handler file (implies -exception)\n"
      "  -version             print version and exit\n",
      SPIM_VERSION);
}

bool parse_args(int argc, char **argv, Options *o) {
  for (int i = 1; i < argc; i += 1) {
    std::string a = argv[i];
    if (a == "-pseudo" || a == "-p") {
      o->pseudo = true;
    } else if (a == "-nopseudo" || a == "-np") {
      o->pseudo = false;
    } else if (a == "-exception" || a == "-e") {
      o->exception = true;
    } else if (a == "-noexception" || a == "-ne") {
      o->exception = false;
    } else if ((a == "-exception_file" || a == "-ef") && i + 1 < argc) {
      o->exception = true;
      o->exception_file = argv[++i];
    } else if (a == "-version" || a == "--version") {
      std::printf("hcs-asm %s (SPIM %s core)\n", HCS_ASM_VERSION, SPIM_VERSION);
      std::exit(0);
    } else if (a == "-h" || a == "-help" || a == "--help") {
      usage(stdout);
      std::exit(0);
    } else if (!a.empty() && a[0] == '-') {
      std::fprintf(stderr, "hcs-asm: unknown option %s\n", a.c_str());
      return false;
    } else if (o->input.empty()) {
      o->input = a;
    } else {
      std::fprintf(stderr, "hcs-asm: only one input file is allowed\n");
      return false;
    }
  }
  if (o->input.empty()) {
    std::fprintf(stderr, "hcs-asm: no input file\n");
    return false;
  }
  return true;
}

std::string directory_of(const std::string &path) {
  std::string::size_type slash = path.find_last_of("/\\");
  return slash == std::string::npos ? std::string(".") : path.substr(0, slash);
}

// exceptions.s next to the running executable.
std::string default_exception_file(const char *argv0) {
  std::string exe = argv0;
#if defined(_WIN32)
  char buf[4096];
  DWORD n = GetModuleFileNameA(NULL, buf, sizeof(buf));
  if (n > 0 && n < sizeof(buf)) exe.assign(buf, n);
#elif defined(__linux__)
  char buf[4096];
  ssize_t n = readlink("/proc/self/exe", buf, sizeof(buf));
  if (n > 0 && n < (ssize_t)sizeof(buf)) exe.assign(buf, n);
#endif
  return directory_of(exe) + "/exceptions.s";
}

// Parse print_symbols() output: "g\tname at 0x%08x\n" or "\tname at 0x%08x\n".
std::vector<Label> parse_symbols(const std::string &listing) {
  std::vector<Label> labels;
  std::istringstream in(listing);
  std::string line;
  while (std::getline(in, line)) {
    Label l;
    l.global = line.compare(0, 2, "g\t") == 0;
    std::string rest = line.substr(l.global ? 2 : (line.empty() ? 0 : 1));
    std::string::size_type at = rest.rfind(" at 0x");
    if (at == std::string::npos) continue;
    l.name = rest.substr(0, at);
    l.addr = (mem_addr)std::strtoul(rest.c_str() + at + 6, 0, 16);
    labels.push_back(l);
  }
  return labels;
}

// "spim: (parser) <msg> on line <N> of file <path>\n<context>" -> {N, <msg>}.
// Other core messages keep their text and have no line.
Message to_message(const std::string &raw) {
  Message m;
  m.line = 0;
  std::string first = raw.substr(0, raw.find('\n'));
  const std::string prefix = "spim: (parser) ";
  if (first.compare(0, prefix.size(), prefix) == 0) first = first.substr(prefix.size());
  std::string::size_type on = first.rfind(" on line ");
  if (on != std::string::npos) {
    m.line = std::atoi(first.c_str() + on + 9);
    first = first.substr(0, on);
  }
  while (!first.empty() && (first[first.size() - 1] == '\n' || first[first.size() - 1] == ' ')) {
    first.erase(first.size() - 1);
  }
  m.text = first;
  // The parser appends the offending source line (then a caret line).
  std::string::size_type nl = raw.find('\n');
  if (nl != std::string::npos) {
    std::string ctx = raw.substr(nl + 1, raw.find('\n', nl + 1) - nl - 1);
    std::string::size_type b = ctx.find_first_not_of(" \t");
    m.context = b == std::string::npos ? std::string() : ctx.substr(b);
  }
  return m;
}

// SOURCE(inst) is "<line>: <text>" on the first instruction of a source line.
bool split_source(const char *s, int *line, std::string *text) {
  if (s == 0) return false;
  char *end = 0;
  long n = std::strtol(s, &end, 10);
  if (end == s || *end != ':') return false;
  *line = (int)n;
  const char *t = end + 1;
  while (*t == ' ' || *t == '\t') t += 1;
  *text = t;
  while (!text->empty() && ((*text)[text->size() - 1] == '\n' || (*text)[text->size() - 1] == '\r')) {
    text->erase(text->size() - 1);
  }
  return true;
}

std::string hex32(uint32 v) {
  char buf[11];
  std::snprintf(buf, sizeof(buf), "0x%08x", v);
  return buf;
}

std::string json_string(const std::string &s) {
  std::string out = "\"";
  for (std::string::size_type i = 0; i < s.size(); i += 1) {
    unsigned char c = (unsigned char)s[i];
    switch (c) {
      case '"': out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default:
        if (c < 0x20) {
          char buf[7];
          std::snprintf(buf, sizeof(buf), "\\u%04x", c);
          out += buf;
        } else {
          out += (char)c;  // UTF-8 passes through
        }
    }
  }
  return out + "\"";
}

}  // namespace

int main(int argc, char **argv) {
#if defined(_WIN32)
  _setmode(_fileno(stdout), _O_BINARY);  // JSON은 LF로 낸다(Linux와 같은 바이트)
#endif
  Options opt;
  if (!parse_args(argc, argv, &opt)) {
    usage(stderr);
    return 2;
  }

  FILE *file = std::fopen(opt.input.c_str(), "rt");
  if (file == 0) {
    std::fprintf(stderr, "hcs-asm: cannot open %s\n", opt.input.c_str());
    return 2;
  }

  std::string handler_path;
  if (opt.exception) {
    handler_path = opt.exception_file.empty() ? default_exception_file(argv[0]) : opt.exception_file;
    FILE *h = std::fopen(handler_path.c_str(), "rt");
    if (h == 0) {
      std::fprintf(stderr, "hcs-asm: cannot open exception handler %s\n", handler_path.c_str());
      return 2;
    }
    std::fclose(h);
  }

  message_out.i = 1;
  console_out.i = 2;
  console_in.i = 0;
  // QtSpim defaults: extended machine, no delayed branches or loads. The tool
  // never changes how SPIM encodes instructions (D-010).
  bare_machine = false;
  accept_pseudo_insts = opt.pseudo;
  delayed_branches = false;
  delayed_loads = false;
  mapped_io = false;
  quiet = false;

  initialize_world(opt.exception ? (char *)handler_path.c_str() : 0, false);
  std::vector<std::string> handler_errors = take_core_errors();
  if (!handler_errors.empty()) {
    std::fprintf(stderr, "hcs-asm: exception handler %s: %s", handler_path.c_str(),
                 handler_errors[0].c_str());
    return 2;
  }

  std::set<std::string> handler_labels;
  {
    std::string listing;
    hcs_output_capture = &listing;
    print_symbols();
    hcs_output_capture = 0;
    std::vector<Label> ls = parse_symbols(listing);
    for (size_t i = 0; i < ls.size(); i += 1) {
      if (ls[i].addr != 0) handler_labels.insert(ls[i].name);
    }
  }
  const mem_addr user_text_start = current_text_pc();
  const mem_addr user_data_start = current_data_pc();

  // read_assembly_file() in CPU/spim-utils.cpp, except that the symbol table
  // is listed before flush_local_labels() drops the local labels.
  std::string listing;
  char *input_name = (char *)opt.input.c_str();
  initialize_scanner(file);
  initialize_parser(input_name);
  while (!yyparse())
    ;
  std::fclose(file);
  hcs_output_capture = &listing;
  print_symbols();
  hcs_output_capture = 0;
  flush_local_labels(!parse_error_occurred);
  end_of_assembly_file();

  std::vector<Message> errors;
  std::vector<std::string> raw_errors = take_core_errors();
  for (size_t i = 0; i < raw_errors.size(); i += 1) errors.push_back(to_message(raw_errors[i]));

  // Labels the input file defines. Labels still at address 0 are only referenced.
  std::vector<Label> labels;
  {
    std::vector<Label> ls = parse_symbols(listing);
    std::map<std::string, Label> by_name;
    for (size_t i = 0; i < ls.size(); i += 1) {
      if (ls[i].addr == 0 || handler_labels.count(ls[i].name)) continue;
      by_name[ls[i].name] = ls[i];
    }
    for (std::map<std::string, Label>::iterator it = by_name.begin(); it != by_name.end(); ++it) {
      labels.push_back(it->second);
    }
  }
  char *undefined = undefined_symbol_string();
  if (undefined != 0) {
    std::istringstream in(undefined);
    std::string name;
    while (std::getline(in, name)) {
      if (name.empty() || handler_labels.count(name)) continue;
      if (name == "main" && opt.exception) continue;  // reported below as a warning
      Message m;
      m.line = 0;
      m.text = "undefined symbol: " + name;
      errors.push_back(m);
    }
    std::free(undefined);
  }

  std::vector<TextWord> text;
  int line = 0;
  std::string source;
  for (mem_addr a = TEXT_BOT; a < text_top; a += BYTES_PER_WORD) {
    instruction *inst = read_mem_inst(a);
    if (inst == 0) continue;
    TextWord w;
    w.addr = a;
    w.word = (uint32)ENCODING(inst);
    w.handler = a < user_text_start;
    // A pseudo instruction's source is on its first word only; later words inherit it.
    if (!split_source(SOURCE(inst), &line, &source) && !text.empty() && text.back().handler != w.handler) {
      line = 0;
      source.clear();
    }
    w.line = line;
    w.source = source;
    text.push_back(w);
  }

  std::vector<std::pair<mem_addr, uint32> > data;
  for (mem_addr a = DATA_BOT; a < user_data_start; a += BYTES_PER_WORD) {
    uint32 v = (uint32)read_mem_word(a);  // $gp area (.extern, small data)
    if (v != 0) data.push_back(std::make_pair(a, v));
  }
  mem_addr data_end = (current_data_pc() + BYTES_PER_WORD - 1) & ~(mem_addr)(BYTES_PER_WORD - 1);
  for (mem_addr a = user_data_start; a < data_end; a += BYTES_PER_WORD) {
    data.push_back(std::make_pair(a, (uint32)read_mem_word(a)));
  }

  std::vector<Message> warnings;
  for (mem_addr a = K_TEXT_BOT; !opt.exception && a < k_text_top; a += BYTES_PER_WORD) {
    if (read_mem_inst(a) != 0) {
      Message m;
      m.line = 0;
      m.text = "kernel text segment (.ktext) is not loaded into the circuit";
      warnings.push_back(m);
      break;
    }
  }
  const Label *main_label = 0;
  for (size_t i = 0; i < labels.size(); i += 1) {
    if (labels[i].name == "main") main_label = &labels[i];
  }
  if (main_label == 0) {
    Message m;
    m.line = 0;
    m.text = "no main label";
    warnings.push_back(m);
  } else if (main_label->addr != user_text_start) {
    Message m;
    m.line = 0;
    m.text = "main is at " + hex32(main_label->addr) + ", not at the start of .text " + hex32(user_text_start);
    warnings.push_back(m);
  }

  // Output. Field order is fixed so that tests can compare text.
  std::string out;
  out += "{\n";
  out += "  \"tool\": " + json_string(std::string("hcs-asm ") + HCS_ASM_VERSION) + ",\n";
  out += "  \"spim\": " + json_string(SPIM_VERSION) + ",\n";
  out += std::string("  \"settings\": {\"bare_machine\": false")
         + ", \"accept_pseudo_insts\": " + (opt.pseudo ? "true" : "false") +
         ", \"exception_handler\": " + (opt.exception ? "true" : "false") +
         ", \"delayed_branches\": false},\n";
  out += "  \"entry\": " + (main_label ? json_string(hex32(main_label->addr)) : std::string("null")) + ",\n";
  out += "  \"text\": [";
  for (size_t i = 0; i < text.size(); i += 1) {
    const TextWord &w = text[i];
    out += i == 0 ? "\n" : ",\n";
    out += "    {\"addr\": " + json_string(hex32(w.addr)) + ", \"word\": " + json_string(hex32(w.word));
    if (w.handler) {
      out += ", \"handler\": true";
    } else {
      out += ", \"line\": " + (w.line > 0 ? std::to_string(w.line) : std::string("null"));
      out += ", \"source\": " + json_string(w.source);
    }
    out += "}";
  }
  out += text.empty() ? "],\n" : "\n  ],\n";
  out += "  \"data\": [";
  for (size_t i = 0; i < data.size(); i += 1) {
    out += i == 0 ? "\n" : ",\n";
    out += "    {\"addr\": " + json_string(hex32(data[i].first)) + ", \"word\": " + json_string(hex32(data[i].second)) + "}";
  }
  out += data.empty() ? "],\n" : "\n  ],\n";
  out += "  \"labels\": {";
  for (size_t i = 0; i < labels.size(); i += 1) {
    out += i == 0 ? "\n" : ",\n";
    out += "    " + json_string(labels[i].name) + ": " + json_string(hex32(labels[i].addr));
  }
  out += labels.empty() ? "},\n" : "\n  },\n";
  const std::vector<Message> *lists[2] = {&errors, &warnings};
  const char *names[2] = {"errors", "warnings"};
  for (int k = 0; k < 2; k += 1) {
    out += std::string("  \"") + names[k] + "\": [";
    for (size_t i = 0; i < lists[k]->size(); i += 1) {
      const Message &m = (*lists[k])[i];
      out += i == 0 ? "\n" : ",\n";
      out += "    {\"line\": " + (m.line > 0 ? std::to_string(m.line) : std::string("null")) +
             ", \"message\": " + json_string(m.text);
      if (!m.context.empty()) out += ", \"context\": " + json_string(m.context);
      out += "}";
    }
    out += lists[k]->empty() ? "]" : "\n  ]";
    out += k == 0 ? ",\n" : "\n";
  }
  out += "}\n";
  std::fwrite(out.data(), 1, out.size(), stdout);
  return errors.empty() ? 0 : 1;
}
