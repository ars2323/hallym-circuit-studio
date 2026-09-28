/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.record;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;

import kr.ac.hallym.hcs.mips.disasm.Disassembler;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxParser;

/**
 * 회로에 올린 실행 이미지(.hmx)의 기호(N-14, D-144). 사이클 표·Instruction 패널의 디스어셈블(`jal 0x00400024 [main]`),
 * Memory 패널의 라벨, Run Until의 라벨 PC에 쓴다. Instruction Memory(없으면 Data Memory)의 {@code source} 속성이 가리키는
 * .hmx를 읽기만 한다(불러오기·부품 내용은 건드리지 않는다: N-16의 몫). 파일이 없거나 .hmx가 아니거나(.s를 가리키는 옛
 * 파일, D-141) 읽을 수 없으면 기호가 없다. 같은 파일(경로·크기·수정 시각)은 다시 읽지 않는다.
 */
final class ProgramSymbols {
    static final ProgramSymbols NONE = new ProgramSymbols(null, 0, 0, Collections.<String, Long>emptyMap());

    final File file;
    private final long length;
    private final long modified;
    /** 이름 → 주소(파일 순서). */
    final Map<String, Long> byName;
    /** 주소 → 이름(디스어셈블러 모양, 한 주소에 여럿이면 사전순 앞). */
    final Map<Integer, String> byAddress;
    /** 주소 → 이름들(파일 순서, Memory 표). */
    final Map<Long, List<String>> labels;

    private ProgramSymbols(File file, long length, long modified, Map<String, Long> symbols) {
        this.file = file;
        this.length = length;
        this.modified = modified;
        this.byName = Collections.unmodifiableMap(symbols);
        this.byAddress = Collections.unmodifiableMap(Disassembler.byAddress(symbols));
        Map<Long, List<String>> l = new TreeMap<>();
        for (Map.Entry<String, Long> e : symbols.entrySet()) {
            l.computeIfAbsent(e.getValue() & 0xffffffffL, k -> new ArrayList<>()).add(e.getKey());
        }
        this.labels = Collections.unmodifiableMap(l);
    }

    /** circFile(.circ) 옆 기준으로 root 아래의 첫 Instruction Memory(없으면 Data Memory)가 가리키는 .hmx. */
    static ProgramSymbols of(File circFile, Circuit root, ProgramSymbols cached) {
        File f = sourceFile(circFile, root);
        if (f == null || !f.getName().toLowerCase(Locale.ROOT).endsWith(".hmx") || !f.isFile()) {
            return NONE;
        }
        if (cached != null && f.equals(cached.file) && f.length() == cached.length
                && f.lastModified() == cached.modified) {
            return cached;
        }
        try {
            HmxParser.Result r = HmxParser.read(f);
            if (r.image == null) {
                return NONE;
            }
            ExecutableImage img = r.image;
            return new ProgramSymbols(f, f.length(), f.lastModified(), img.symbols());
        } catch (IOException | RuntimeException e) {
            return NONE;
        }
    }

    /** 이름 또는 주소 글의 주소. 이름이 없으면 null. */
    Long address(String name) {
        return byName.get(name);
    }

    static File sourceFile(File circFile, Circuit root) {
        Component imem = find(root, "Instruction Memory", new java.util.HashSet<Circuit>());
        String src = source(imem);
        if (src == null) {
            src = source(find(root, "Data Memory", new java.util.HashSet<Circuit>()));
        }
        if (src == null) {
            return null;
        }
        File f = new File(src);
        if (!f.isAbsolute() && circFile != null && circFile.getAbsoluteFile().getParentFile() != null) {
            f = new File(circFile.getAbsoluteFile().getParentFile(), src);
        }
        return f;
    }

    private static String source(Component c) {
        if (c == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) c.getAttributeSet().getAttribute("source");
        Object v = a == null ? null : c.getAttributeSet().getValue(a);
        return v == null || v.toString().isEmpty() ? null : v.toString();
    }

    /** 최상위 먼저, 그다음 서브회로 안(위→아래, 왼쪽→오른쪽)에서 처음 만나는 그 부품. */
    private static Component find(Circuit c, String factory, java.util.Set<Circuit> seen) {
        if (c == null || !seen.add(c)) {
            return null;
        }
        List<Component> subs = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            if (factory.equals(x.getFactory().getName())) {
                return x;
            }
            if (x.getFactory() instanceof SubcircuitFactory) {
                subs.add(x);
            }
        }
        subs.sort(java.util.Comparator.<Component>comparingInt(x -> x.getLocation().getY())
                .thenComparingInt(x -> x.getLocation().getX()));
        for (Component s : subs) {
            Component found = find(((SubcircuitFactory) s.getFactory()).getSubcircuit(), factory, seen);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
