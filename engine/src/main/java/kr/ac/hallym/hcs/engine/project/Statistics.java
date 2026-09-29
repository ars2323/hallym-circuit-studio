/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/**
 * Get Circuit Statistics(N-21, D-162): 원조 {@code FileStatistics.compute}와 같은 셈(Simple, Unique, Recursive, 두 합계)과
 * 같은 줄 차례. 다른 것은 하나다: 원조 {@code sortCounts}는 줄 차례를 정하려고 모든 라이브러리 도구의
 * {@code AddTool.getFactory()}를 불러 아직 불러오지 않은 부품 종류(ROM 등)까지 불러온다. 불러온 도구는 저장할 때 그 도구의
 * 속성을 {@code <tool>}로 적어 .circ 바이트가 바뀐다(원조도 같다). 여기서는 불러온 도구만 본다
 * ({@code getFactory(false)}): 회로에 놓인 부품의 종류는 파일을 읽을 때 이미 불러왔으므로 줄과 차례는 같고, 읽기만 한 뒤의
 * 저장은 원래 바이트 그대로다(OpenSaveParityTest).
 */
final class Statistics {
    static final class Count {
        final ComponentFactory factory;
        Library library;
        int simple;
        int unique;
        int recursive;

        Count(ComponentFactory factory) {
            this.factory = factory;
        }
    }

    final List<Count> counts;
    final Count without;
    final Count with;

    private Statistics(List<Count> counts, Count without, Count with) {
        this.counts = counts;
        this.without = without;
        this.with = with;
    }

    static Statistics compute(LogisimFile file, Circuit circuit) {
        Set<Circuit> include = new HashSet<>(file.getCircuits());
        Map<Circuit, Map<ComponentFactory, Count>> countMap = new HashMap<>();
        recursive(circuit, include, countMap);
        unique(countMap.get(circuit), countMap);
        List<Count> list = sort(countMap.get(circuit), file);
        return new Statistics(list, total(list, include), total(list, null));
    }

    private static Map<ComponentFactory, Count> recursive(Circuit circuit, Set<Circuit> include,
            Map<Circuit, Map<ComponentFactory, Count>> countMap) {
        Map<ComponentFactory, Count> have = countMap.get(circuit);
        if (have != null) {
            return have;
        }
        Map<ComponentFactory, Count> counts = new LinkedHashMap<>();
        for (Component comp : circuit.getNonWires()) {
            counts.computeIfAbsent(comp.getFactory(), Count::new).simple++;
        }
        countMap.put(circuit, counts);
        for (Count c : counts.values()) {
            c.unique = c.simple;
            c.recursive = c.simple;
        }
        for (Circuit sub : include) {
            SubcircuitFactory subFactory = sub.getSubcircuitFactory();
            Count uses = counts.get(subFactory);
            if (uses != null) {
                int multiplier = uses.simple;
                Map<ComponentFactory, Count> subCounts = recursive(sub, include, countMap);
                for (Count sc : new ArrayList<>(subCounts.values())) {
                    Count sup = counts.computeIfAbsent(sc.factory, Count::new);
                    sup.recursive += multiplier * sc.recursive;
                }
            }
        }
        return counts;
    }

    private static void unique(Map<ComponentFactory, Count> counts, Map<Circuit, Map<ComponentFactory, Count>> countMap) {
        for (Count c : counts.values()) {
            int unique = 0;
            for (Map<ComponentFactory, Count> m : countMap.values()) {
                Count sub = m.get(c.factory);
                if (sub != null) {
                    unique += sub.simple;
                }
            }
            c.unique = unique;
        }
    }

    private static List<Count> sort(Map<ComponentFactory, Count> counts, LogisimFile file) {
        List<Count> out = new ArrayList<>();
        for (AddTool tool : file.getTools()) {
            Count c = counts.get(tool.getFactory(false));
            if (c != null) {
                c.library = file;
                out.add(c);
            }
        }
        for (Library lib : file.getLibraries()) {
            for (Tool tool : lib.getTools()) {
                if (tool instanceof AddTool) {
                    ComponentFactory f = ((AddTool) tool).getFactory(false); // 불러오지 않는다(위 설명)
                    Count c = f == null ? null : counts.get(f);
                    if (c != null) {
                        c.library = lib;
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }

    private static Count total(List<Count> counts, Set<Circuit> exclude) {
        Count ret = new Count(null);
        for (Count c : counts) {
            Circuit sub = c.factory instanceof SubcircuitFactory ? ((SubcircuitFactory) c.factory).getSubcircuit() : null;
            if (exclude == null || !exclude.contains(sub)) {
                ret.simple += c.simple;
                ret.unique += c.unique;
                ret.recursive += c.recursive;
            }
        }
        return ret;
    }
}
