/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LibraryManager;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.BundledLibraries;

/**
 * 새 파일에서도 보이는 Hallym MIPS(V-01, D-096). 번들 jar의 라이브러리를 "그림자"로 두고 부품 목록과 검색에 늘
 * 보인다. 파일에 실제로 들어가는(저장되는) 때는 MIPS 부품을 처음 놓거나 붙여넣는 순간뿐이다. 그림자 라이브러리의
 * 저장 설명자는 {@code jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary}(.circ 옆의 jar, 원조 2.7.1이 여는
 * 모양)이고, 실제로 읽는 jar는 번들 jar다(D-007 JarDescriptor의 named/source 분리).
 *
 * <p>MIPS 부품을 쓰지 않은 파일에는 아무것도 들어가지 않으므로 저장 결과는 원조와 바이트 동일하다(D-006).
 */
public final class MipsShadow {
    /** 원조에서 열리는 저장 설명자. */
    public static final String DESCRIPTOR = "jar#" + BundledLibraries.MIPS_JAR + "#" + BundledLibraries.MIPS_CLASS;

    private static LoadedLibrary shadow; // LibraryManager는 약한 참조만 갖는다
    private static final Map<Project, Boolean> AUTO_ADDED = new WeakHashMap<>();
    private static final Map<Project, File> NOTICED = new WeakHashMap<>();

    private MipsShadow() {
    }

    /** 번들 jar에서 읽은 그림자 라이브러리. 번들이 없으면(개발 중, 트랙 A) null. */
    public static synchronized LoadedLibrary shadow(Loader loader) {
        if (shadow != null) {
            return shadow;
        }
        File jar = BundledLibraries.mipsJar();
        if (jar == null || !jar.canRead()) {
            return null;
        }
        shadow = LibraryManager.instance.loadJarLibrary(loader, new File(BundledLibraries.MIPS_JAR), jar,
                BundledLibraries.MIPS_CLASS);
        return shadow;
    }

    /** 파일에 들어 있는 MIPS 라이브러리(설명자가 어떤 경로든). 없으면 null. */
    public static Library inFile(LogisimFile file) {
        for (Library lib : file.getLibraries()) {
            if (isMips(file, lib)) {
                return lib;
            }
        }
        return null;
    }

    static boolean isMips(LogisimFile file, Library lib) {
        if (!(lib instanceof LoadedLibrary)) {
            return false;
        }
        String desc;
        try {
            desc = file.getLoader().getDescriptor(lib);
        } catch (RuntimeException e) {
            return false;
        }
        return desc != null && desc.endsWith("#" + BundledLibraries.MIPS_CLASS);
    }

    /** 파일에 아직 추가되지 않은 채 목록에만 보이는 라이브러리인가(트리가 흐리게 그린다). */
    public static boolean isPending(LogisimFile file, Object node) {
        return node instanceof Library && node == shadow && !file.getLibraries().contains(node);
    }

    /** 부품 목록·검색이 쓰는 라이브러리들: 파일의 라이브러리 + (없으면) 그림자. */
    public static List<Library> libraries(LogisimFile file) {
        List<Library> libs = new ArrayList<>(file.getLibraries());
        if (inFile(file) == null) {
            LoadedLibrary s = shadow(file.getLoader());
            if (s != null) {
                libs.add(s);
            }
        }
        return libs;
    }

    /** 부품 목록 트리의 뿌리 아래 항목들: 파일의 도구·라이브러리 + (없으면) 그림자. */
    public static List<?> treeElements(LogisimFile file) {
        List<Object> out = new ArrayList<>(file.getElements());
        if (inFile(file) == null) {
            LoadedLibrary s = shadow(file.getLoader());
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** 파일의 회로 어딘가에 그림자 라이브러리의 부품이 있는가. */
    static boolean usesShadow(LogisimFile file) {
        if (shadow == null) {
            return false;
        }
        for (Circuit c : file.getCircuits()) {
            for (Component comp : c.getNonWires()) {
                if (shadow.contains(comp.getFactory())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 동작(또는 다시 실행) 뒤: 그림자 부품이 처음 놓였으면 라이브러리를 파일에 넣는다. 같은 동작의 일부이므로
     * 되돌리기 한 단계로 함께 사라진다({@link #afterUndo}).
     */
    public static void afterAction(Project proj) {
        LogisimFile file = proj.getLogisimFile();
        if (file == null || shadow == null || inFile(file) != null || !usesShadow(file)) {
            return;
        }
        file.addLibrary(shadow);
        AUTO_ADDED.put(proj, Boolean.TRUE);
    }

    /** 되돌리기 뒤: 자동으로 넣은 라이브러리인데 그 부품이 하나도 안 남았으면 라이브러리도 뺀다. */
    public static void afterUndo(Project proj) {
        LogisimFile file = proj.getLogisimFile();
        if (file == null || shadow == null || !Boolean.TRUE.equals(AUTO_ADDED.get(proj))) {
            return;
        }
        if (file.getLibraries().contains(shadow) && !usesShadow(file)) {
            file.removeLibrary(shadow);
        }
    }

    /** .circ 옆에 있어야 하는 jar. */
    public static File siblingJar(File circ) {
        return new File(circ.getAbsoluteFile().getParentFile(), BundledLibraries.MIPS_JAR);
    }

    /**
     * 저장 뒤: 파일이 MIPS 라이브러리를 쓰는데 .circ 옆에 hcs-mips.jar가 없으면 알림이 필요하다(원조 2.7.1은 그
     * jar가 있어야 연다). 한 파일에 한 번만. 몰래 복사하지 않는다(D-007).
     */
    public static boolean needsJarNotice(Project proj, File saved) {
        LogisimFile file = proj.getLogisimFile();
        if (file == null || saved == null || inFile(file) == null || siblingJar(saved).exists()) {
            return false;
        }
        File before = NOTICED.get(proj);
        if (before != null && before.getAbsoluteFile().equals(saved.getAbsoluteFile())) {
            return false;
        }
        NOTICED.put(proj, saved);
        return true;
    }

    /** 번들 jar를 .circ 옆으로 복사한다([Copy hcs-mips.jar Here]). */
    public static File copyJarBeside(File circ) throws IOException {
        File jar = BundledLibraries.mipsJar();
        if (jar == null || !jar.canRead()) {
            throw new IOException("no bundled " + BundledLibraries.MIPS_JAR);
        }
        File dest = siblingJar(circ);
        Files.copy(jar.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    /** 테스트용: 그림자와 프로젝트별 기록을 비운다. */
    public static synchronized void reset() {
        shadow = null;
        AUTO_ADDED.clear();
        NOTICED.clear();
    }
}
