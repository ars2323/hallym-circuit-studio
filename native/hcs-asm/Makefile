# hcs-asm: SPIM 9.1.24 코어(vendor/spim-9.1.24/CPU/, 수정 없음)로 .s를 어셈블해 JSON으로 내는 명령줄 도구.
# 파서·스캐너는 CPU/의 parser.y, scanner.l에서 build/ 안에 생성한다. vendor/에는 아무것도 쓰지 않는다.

CPU_DIR   := ../../vendor/spim-9.1.24/CPU
BUILD_DIR := build

CXX      ?= g++
BISON    ?= bison
FLEX     ?= flex
CXXFLAGS ?= -O2
# CPU/는 문자열 리터럴을 char*로 넘기고 경고가 많다. 우리 코드만 -Wall -Wextra로 본다.
CORE_FLAGS := -std=c++11 -Wno-write-strings -w
OWN_FLAGS  := -std=c++11 -Wall -Wextra -Wno-write-strings
INCLUDES   := -I$(BUILD_DIR) -I$(CPU_DIR)

CORE_SRCS := data.cpp display-utils.cpp inst.cpp mem.cpp run.cpp spim-utils.cpp \
             string-stream.cpp sym-tbl.cpp syscall.cpp
CORE_OBJS := $(addprefix $(BUILD_DIR)/core/,$(CORE_SRCS:.cpp=.o)) \
             $(BUILD_DIR)/core/parser_yacc.o $(BUILD_DIR)/core/lex.scanner.o
OWN_OBJS  := $(BUILD_DIR)/hcs-asm.o $(BUILD_DIR)/frontend.o

TARGET := $(BUILD_DIR)/hcs-asm

.PHONY: all clean test
all: $(TARGET) $(BUILD_DIR)/exceptions.s

$(TARGET): $(CORE_OBJS) $(OWN_OBJS)
	$(CXX) -o $@ $^ $(LDFLAGS) -lm

# 기본 예외 처리기(-exception)는 실행 파일 옆의 exceptions.s를 쓴다.
$(BUILD_DIR)/exceptions.s: $(CPU_DIR)/exceptions.s | $(BUILD_DIR)
	cp $< $@

$(BUILD_DIR)/parser_yacc.cpp $(BUILD_DIR)/parser_yacc.h: $(CPU_DIR)/parser.y | $(BUILD_DIR)
	$(BISON) -d --defines=$(BUILD_DIR)/parser_yacc.h --output=$(BUILD_DIR)/parser_yacc.cpp -p yy $<

$(BUILD_DIR)/lex.scanner.cpp: $(CPU_DIR)/scanner.l $(BUILD_DIR)/parser_yacc.h | $(BUILD_DIR)
	$(FLEX) -I -8 -Pyy -o $@ $<

$(BUILD_DIR)/core/%.o: $(CPU_DIR)/%.cpp $(BUILD_DIR)/parser_yacc.h | $(BUILD_DIR)/core
	$(CXX) $(CXXFLAGS) $(CORE_FLAGS) $(INCLUDES) -c -o $@ $<

$(BUILD_DIR)/core/%.o: $(BUILD_DIR)/%.cpp | $(BUILD_DIR)/core
	$(CXX) $(CXXFLAGS) $(CORE_FLAGS) $(INCLUDES) -x c++ -c -o $@ $<

$(BUILD_DIR)/%.o: src/%.cpp $(BUILD_DIR)/parser_yacc.h | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) $(OWN_FLAGS) $(INCLUDES) -c -o $@ $<

$(BUILD_DIR) $(BUILD_DIR)/core:
	mkdir -p $@

test: all oracle
	python3 tests/check.py

clean:
	rm -rf $(BUILD_DIR)

# 테스트 오라클: 원본 spim 명령줄(vendor/spim-9.1.24/spim/spim.cpp, 수정 없음). 배포하지 않는다.
ORACLE := $(BUILD_DIR)/oracle/spim
$(BUILD_DIR)/oracle:
	mkdir -p $@
$(BUILD_DIR)/oracle/spim.o: $(CPU_DIR)/../spim/spim.cpp $(BUILD_DIR)/parser_yacc.h | $(BUILD_DIR)/oracle
	$(CXX) $(CXXFLAGS) $(CORE_FLAGS) $(INCLUDES) -DDEFAULT_EXCEPTION_HANDLER='"$(abspath $(CPU_DIR))/exceptions.s"' -c -o $@ $<
$(ORACLE): $(CORE_OBJS) $(BUILD_DIR)/oracle/spim.o
	$(CXX) -o $@ $^ $(LDFLAGS) -lm
.PHONY: oracle
oracle: $(ORACLE)
