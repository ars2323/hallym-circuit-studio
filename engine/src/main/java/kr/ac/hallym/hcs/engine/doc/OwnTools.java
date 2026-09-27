/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.doc;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.cburch.logisim.Main;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/**
 * 파일마다 제 도구 객체를 갖게 한다(D-149). 원조 2.7.1은 {@code <lib>} 아래 {@code <tool>} 속성(도구 기본값)을 도구
 * 객체에 두는데, 두 곳에서 그 객체가 파일의 글자와 어긋난다. 엔진은 원조 코드를 바꾸지 않고 도구 객체만 바꿔 끼운다
 * (라이브러리의 도구 목록 자리에 같은 부품의 새 도구).
 * <ol>
 *   <li><b>파일 사이에 나눠 쓰는 도구.</b> Wiring의 앞 일곱 도구(Splitter, Pin, Probe, Tunnel, Pull Resistor, Clock,
 *       Constant)는 원조가 static 배열 하나를 모든 Wiring에 넣는다. 한 파일의 {@code <tool name="Splitter">} 설정이
 *       같은 JVM에서 연 다른 파일의 저장에 끼어든다. 엔진의 Loader마다 새 도구로 바꾼다({@link #unshare}).</li>
 *   <li><b>저장이 불러오는 도구.</b> 원조 XmlWriter는 부품의 라이브러리를 {@code Library.contains}로 찾고, 그것은
 *       앞 라이브러리 도구마다 팩토리를 불러오며 속성 묶음을 만든다. 만들어진 ROM 도구는 기본 {@code contents}가
 *       자기 자신과만 같아서 다음 저장부터 {@code <tool name="ROM">}으로 적힌다(Memory 뒤 라이브러리 — I/O, Base,
 *       JAR, .circ — 의 부품을 쓰는 파일을 두 번 저장하면). 저장 앞에 손대지 않은 도구를 적어 두고 저장이 속성
 *       묶음을 만든 것만 새 도구로 되돌린다({@link #untouched}, {@link #afterSave}).</li>
 * </ol>
 * 둘 다 원조를 새로 켜고 그 파일 하나를 열어 저장한 결과(D-006 기준)와 같게 하려는 것이다.
 */
public final class OwnTools {
    private OwnTools() {
    }

    /** 새 Loader의 내장 라이브러리에서 JVM 전체가 나눠 쓰는 도구를 이 Loader만의 새 도구로 바꾼다. */
    public static void unshare(Builtin builtin) {
        Builtin other = new Builtin();
        for (Library lib : builtin.getLibraries()) {
            Library twin = other.getLibrary(lib.getName());
            if (twin == null) {
                continue;
            }
            List<Tool> mine = tools(lib);
            List<? extends Tool> theirs = twin.getTools();
            for (int i = 0; i < mine.size() && i < theirs.size(); i++) {
                Tool t = mine.get(i);
                ComponentFactory f = t instanceof AddTool ? ((AddTool) t).getFactory(false) : null;
                if (t == theirs.get(i) && f != null) {
                    mine.set(i, new AddTool(f));
                }
            }
        }
    }

    /** 저장 앞: 파일의 내장 라이브러리에서 아직 속성 묶음이 없는 도구들(원조가 기본값으로 보고 적지 않는 것). */
    public static Set<Tool> untouched(Loader loader, LogisimFile file) {
        Set<Tool> ret = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Library lib : file.getLibraries()) {
            if (isBuiltin(loader, lib)) {
                for (Tool t : lib.getTools()) {
                    if (isUntouched(t)) {
                        ret.add(t);
                    }
                }
            }
        }
        return ret;
    }

    /** 저장 뒤: 저장이 속성 묶음을 만든 도구를 같은 부품의 새 도구로 바꾼다(다음 저장도 첫 저장과 같다). */
    public static void afterSave(Loader loader, LogisimFile file, Set<Tool> untouched) {
        Builtin fresh = null;
        for (Library lib : file.getLibraries()) {
            if (!isBuiltin(loader, lib)) {
                continue;
            }
            List<Tool> mine = tools(lib);
            for (int i = 0; i < mine.size(); i++) {
                Tool t = mine.get(i);
                if (!untouched.contains(t) || isUntouched(t)) {
                    continue;
                }
                if (fresh == null) {
                    fresh = new Builtin();
                }
                Tool twin = fresh.getLibrary(lib.getName()).getTools().get(i);
                ComponentFactory f = twin instanceof AddTool ? ((AddTool) twin).getFactory(false) : null;
                if (f != null) {
                    twin = new AddTool(f); // 나눠 쓰는 도구는 다시 나눠 쓰지 않는다
                }
                if (twin.getName().equals(t.getName()) && isUntouched(twin)) {
                    mine.set(i, twin);
                }
            }
        }
    }

    private static boolean isUntouched(Tool t) {
        return t instanceof AddTool && t.isAllDefaultValues(t.getAttributeSet(), Main.VERSION);
    }

    private static boolean isBuiltin(Loader loader, Library lib) {
        return loader.getBuiltin().getLibraries().contains(lib);
    }

    /** 원조 내장 라이브러리의 도구 목록은 그 라이브러리가 가진 목록 자체다(ArrayList·Arrays.asList: 자리 바꾸기 가능). */
    @SuppressWarnings("unchecked")
    private static List<Tool> tools(Library lib) {
        return (List<Tool>) lib.getTools();
    }
}
