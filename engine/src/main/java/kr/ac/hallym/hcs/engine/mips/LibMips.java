/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.mips;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.BundledLibraries;
import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.mips.image.LoadReport;

/**
 * lib-mips의 불러오기 입구({@code kr.ac.hallym.hcs.mips.ProgramLoading}, D-147)를 부른다. lib-mips는 원조 JAR 라이브러리
 * 방식(따로 된 클래스 로더)으로 불려서 엔진이 그 클래스를 컴파일 때 모른다. 그래서 파일의 MIPS 부품(또는 번들 Hallym MIPS
 * 라이브러리의 도구)이 쓰는 클래스 로더에서 이름으로 찾는다. 인자와 결과는 원조 Logisim 형과 공용 형({@link LoadReport},
 * 부모 클래스 로더가 먼저 찾아 엔진과 같은 형, D-125)뿐이다. 파싱·배치는 여기서 하지 않는다.
 */
public final class LibMips {
    static final String ENTRY_CLASS = "kr.ac.hallym.hcs.mips.ProgramLoading";
    private static final String PACKAGE = "kr.ac.hallym.hcs.mips.";

    private LibMips() {
    }

    /** 엔진에 lib-mips가 없음(번들 jar도 파일의 라이브러리도 없음). */
    public static final class Missing extends Exception {
        private static final long serialVersionUID = 1L;

        Missing(String message) {
            super(message);
        }
    }

    /** 파일의 MIPS 부품을 만든 클래스 로더. 부품이 없으면 파일(또는 번들 그림자) 라이브러리의 도구에서. */
    static ClassLoader loader(LogisimFile file) throws Missing {
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                if (x.getFactory().getClass().getName().startsWith(PACKAGE)) {
                    return x.getFactory().getClass().getClassLoader();
                }
            }
        }
        for (Library lib : MipsShadow.libraries(file)) {
            for (Tool t : lib.getTools()) {
                // 이미 만든 팩토리만 본다(지연 불러오기를 일으키지 않는다, D-134 7)
                ComponentFactory f = t instanceof AddTool ? ((AddTool) t).getFactory(false) : null;
                if (f != null && f.getClass().getName().startsWith(PACKAGE)) {
                    return f.getClass().getClassLoader();
                }
            }
        }
        throw new Missing("the Hallym MIPS library (" + BundledLibraries.MIPS_JAR + ") is not available");
    }

    /** 클래스 로더마다 찾은 메서드(부품 내용은 사실을 볼 때마다 읽는다). */
    private static final Map<ClassLoader, Map<String, Method>> METHODS = new java.util.WeakHashMap<>();

    private static synchronized Method method(ClassLoader loader, String name, Class<?>... types) throws Missing {
        Map<String, Method> known = METHODS.computeIfAbsent(loader, k -> new java.util.HashMap<>());
        Method m = known.get(name);
        if (m != null) {
            return m;
        }
        try {
            m = Class.forName(ENTRY_CLASS, true, loader).getMethod(name, types);
        } catch (ReflectiveOperationException e) {
            throw new Missing("the Hallym MIPS library has no " + ENTRY_CLASS + "." + name + " (an older hcs-mips.jar?)");
        }
        known.put(name, m);
        return m;
    }

    private static Object call(Method m, Object... args) {
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            throw t instanceof RuntimeException ? (RuntimeException) t : new IllegalStateException(t);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code ProgramLoading.load}: 트랙 A 메뉴와 같은 읽기·계획. 아무것도 바꾸지 않는다. */
    public static LoadReport load(LogisimFile file, File hmx, File circ, Circuit clickedCircuit, Component clicked,
            Map<String, Component> picks) throws Missing {
        Method m = method(loader(file), "load", File.class, File.class, List.class, Circuit.class, Component.class,
                Map.class);
        return (LoadReport) call(m, hmx, circ, file.getCircuits(), clickedCircuit, clicked, picks);
    }

    /** {@code ProgramLoading.reload}: source 속성을 가진 부품에 그 이미지를 다시 넣을 계획. */
    public static LoadReport reload(LogisimFile file, File hmx, File circ, String source) throws Missing {
        Method m = method(loader(file), "reload", File.class, File.class, List.class, String.class);
        return (LoadReport) call(m, hmx, circ, file.getCircuits(), source);
    }

    /** 메모리 부품의 초기 내용(주소 → 워드). MIPS 메모리 부품이 아니면 null. */
    @SuppressWarnings("unchecked")
    public static SortedMap<Long, Integer> contents(Component c) {
        if (!c.getFactory().getClass().getName().startsWith(PACKAGE)) {
            return null;
        }
        try {
            Method m = method(c.getFactory().getClass().getClassLoader(), "contents", Component.class);
            return (SortedMap<Long, Integer>) call(m, c);
        } catch (Missing e) {
            return null;
        }
    }
}
