/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.parity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code tests/parity/<장면>.intents} 읽기(N-01, D-136). 한 줄에 JSON 객체 하나, {@code #}으로 시작하는 줄과 빈 줄은
 * 설명이다. 객체의 {@code method}는 docs/engine-api.md의 메서드 이름이고 나머지 키는 그 params다. 모르는 메서드와
 * 모르는 키, 빠진 키는 읽을 때 오류로 알린다(오타가 조용히 무시되지 않게).
 */
final class IntentScript {
    /** 메서드 → 받는 키. 앞에 !를 붙인 키는 꼭 있어야 한다. */
    static final Map<String, List<String>> METHODS = new LinkedHashMap<>();

    static {
        spec("file.open", "!path");
        spec("view.zoom", "!factor");
        spec("edit.addComponent", "lib", "!name", "!loc", "attrs", "as", "circuit");
        spec("edit.setToolAttr", "lib", "!name", "!attr", "!value");
        spec("edit.addWire", "!points", "circuit");
        spec("edit.select", "ids", "rect", "add", "filter", "circuit");
        spec("edit.move", "ids", "!dx", "!dy", "keepConnected", "circuit");
        spec("edit.delete", "ids", "circuit");
        spec("edit.setAttr", "ids", "!attr", "!value", "circuit");
        spec("edit.setCircuitAttr", "!target", "!attr", "!value");
        spec("edit.undo");
        spec("edit.redo");
        spec("edit.copy", "ids", "circuit");
        spec("edit.cut", "ids", "circuit");
        spec("edit.paste", "circuit");
        spec("edit.duplicate", "ids", "circuit");
        spec("edit.duplicateN", "ids", "!count", "!direction", "spacing", "number", "circuit");
        spec("edit.align", "!ids", "!mode", "circuit");
        spec("edit.distribute", "!ids", "!axis", "circuit");
        spec("edit.createCircuit", "!name");
        spec("edit.setMainCircuit", "!target");
        spec("edit.portOrder", "!target", "!order", "confirm", "circuit");
        spec("edit.autoAppearance", "!target", "confirm", "circuit");
        spec("edit.importCircuits", "!path", "!circuits");
        spec("edit.loadLibrary", "!kind", "name", "path");
        spec("edit.unloadLibrary", "!name");
        spec("edit.tunnelColor", "!id", "color", "circuit");
        spec("edit.signalGroup", "!wire", "group", "circuit");
        spec("edit.areaMemo", "!at", "ids", "text", "color", "bounds", "delete", "circuit");
        spec("edit.splitterEdit", "!id", "!ranges", "names", "lsbTop", "circuit");
        spec("edit.splitterSplit", "!wire", "!at", "!ranges", "names", "lsbTop", "circuit");
    }

    private static void spec(String method, String... keys) {
        METHODS.put(method, Collections.unmodifiableList(Arrays.asList(keys)));
    }

    final String name;
    final List<Intent> intents;

    private IntentScript(String name, List<Intent> intents) {
        this.name = name;
        this.intents = intents;
    }

    static IntentScript read(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String name = fileName.endsWith(".intents") ? fileName.substring(0, fileName.length() - 8) : fileName;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Intent> out = new ArrayList<>();
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Object v;
            try {
                v = Json.parse(line);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(fileName + ":" + (n + 1) + ": " + e.getMessage(), e);
            }
            if (!(v instanceof Map)) {
                throw new IllegalArgumentException(fileName + ":" + (n + 1) + ": a JSON object per line");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) v);
            Object method = m.remove("method");
            if (!(method instanceof String) || !METHODS.containsKey(method)) {
                throw new IllegalArgumentException(fileName + ":" + (n + 1) + ": unknown method " + method);
            }
            check(fileName, n + 1, (String) method, m, out.isEmpty());
            out.add(new Intent(fileName, n + 1, (String) method, m));
        }
        return new IntentScript(name, out);
    }

    private static void check(String file, int line, String method, Map<String, Object> params, boolean first) {
        Set<String> allowed = new HashSet<>();
        for (String k : METHODS.get(method)) {
            String key = k.startsWith("!") ? k.substring(1) : k;
            allowed.add(key);
            if (k.startsWith("!") && !params.containsKey(key)) {
                throw new IllegalArgumentException(file + ":" + line + ": " + method + " needs '" + key + "'");
            }
        }
        for (String k : params.keySet()) {
            if (!allowed.contains(k)) {
                throw new IllegalArgumentException(file + ":" + line + ": " + method + " has no '" + k + "'");
            }
        }
        if (method.equals("file.open") && !first) {
            throw new IllegalArgumentException(file + ":" + line + ": file.open only as the first intent");
        }
    }

    /** 모델을 바꾸는(또는 바꿀 수 있는) 의도의 수. file.open·view.zoom은 세지 않는다. */
    int editCount() {
        int n = 0;
        for (Intent i : intents) {
            if (i.method.startsWith("edit.")) {
                n++;
            }
        }
        return n;
    }
}
