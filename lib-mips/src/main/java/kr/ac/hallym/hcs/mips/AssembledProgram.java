/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** hcs-asm의 JSON 출력(docs/hcs-asm.md). */
final class AssembledProgram {
    static final class Word {
        final long addr;
        final int word;
        final int line; // 0: 모름(예외 처리기 코드)
        final String source;

        Word(long addr, int word, int line, String source) {
            this.addr = addr;
            this.word = word;
            this.line = line;
            this.source = source;
        }
    }

    static final class Message {
        final int line; // 0: 줄과 무관
        final String text;
        final String context;

        Message(int line, String text, String context) {
            this.line = line;
            this.text = text;
            this.context = context;
        }

        @Override
        public String toString() {
            return (line > 0 ? line + ": " : "") + text + (context != null ? "  (" + context + ")" : "");
        }
    }

    final Map<String, Object> settings;
    final Long entry;
    final List<Word> text;
    final Map<Long, Integer> data;
    final Map<String, Long> labels;
    final List<Message> errors;
    final List<Message> warnings;

    private AssembledProgram(Map<String, Object> settings, Long entry, List<Word> text, Map<Long, Integer> data,
            Map<String, Long> labels, List<Message> errors, List<Message> warnings) {
        this.settings = settings;
        this.entry = entry;
        this.text = text;
        this.data = data;
        this.labels = labels;
        this.errors = errors;
        this.warnings = warnings;
    }

    @SuppressWarnings("unchecked")
    static AssembledProgram fromJson(String json) {
        Map<String, Object> root = (Map<String, Object>) Json.parse(json);
        List<Word> text = new ArrayList<Word>();
        for (Object o : (List<Object>) root.get("text")) {
            Map<String, Object> w = (Map<String, Object>) o;
            Object line = w.get("line");
            text.add(new Word(hex(w.get("addr")), (int) hex(w.get("word")),
                    line == null ? 0 : ((Long) line).intValue(), (String) w.get("source")));
        }
        Map<Long, Integer> data = new TreeMap<Long, Integer>();
        for (Object o : (List<Object>) root.get("data")) {
            Map<String, Object> d = (Map<String, Object>) o;
            data.put(hex(d.get("addr")), (int) hex(d.get("word")));
        }
        Map<String, Long> labels = new LinkedHashMap<String, Long>();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) root.get("labels")).entrySet()) {
            labels.put(e.getKey(), hex(e.getValue()));
        }
        Object entry = root.get("entry");
        return new AssembledProgram((Map<String, Object>) root.get("settings"),
                entry == null ? null : hex(entry), text, data, labels,
                messages(root.get("errors")), messages(root.get("warnings")));
    }

    @SuppressWarnings("unchecked")
    private static List<Message> messages(Object list) {
        List<Message> out = new ArrayList<Message>();
        for (Object o : (List<Object>) list) {
            Map<String, Object> m = (Map<String, Object>) o;
            Object line = m.get("line");
            out.add(new Message(line == null ? 0 : ((Long) line).intValue(),
                    (String) m.get("message"), (String) m.get("context")));
        }
        return Collections.unmodifiableList(out);
    }

    private static long hex(Object s) {
        String t = (String) s;
        return Long.parseLong(t.startsWith("0x") ? t.substring(2) : t, 16);
    }

    WordImage textImage() {
        Map<Long, Integer> words = new TreeMap<Long, Integer>();
        for (Word w : text) {
            words.put(w.addr, w.word);
        }
        return WordImage.of(words);
    }

    WordImage dataImage() {
        return WordImage.of(data);
    }

    /** 프로그램이 쓰는 명령어 이름들(알파벳 순, PLAN.md 6.6). 예외 처리기 코드는 뺀다. */
    List<String> usedInstructions() {
        TreeSet<String> names = new TreeSet<String>();
        for (Word w : text) {
            if (w.line > 0) {
                String m = Disassembler.mnemonic(w.word);
                names.add(m == null ? "?" : m);
            }
        }
        return new ArrayList<String>(names);
    }
}
