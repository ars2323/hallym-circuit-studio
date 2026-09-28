/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.doc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensionIO;
import kr.ac.hallym.hcs.app.ext.CircExtensions;

/**
 * 비정상 종료 복구 파일(N-19, D-152): 학생이 한 번이라도 저장한(경로가 있는) 파일 옆의 {@code <이름>.circ.hcs-recover}.
 * 내용은 그 파일을 지금 저장하면 쓰일 .circ 그대로다(원조 writer와 확장 정보, 새 부품을 안 쓴 파일은 원조와 바이트 호환).
 * 이 폴더 말고는 어디에도 쓰지 않는다(앱 전역 자리 없음, 실습실 규칙).
 *
 * <p>쓰기는 원조 {@code Loader.save}를 쓰지 않는다: 그것은 열린 파일의 이름과 저장 위치(main file)를 바꾸고 라이브러리
 * 관리자에 저장을 알린다. 그래서 {@code Loader.save}가 부르는 원조 writer({@code LogisimFile.write}, 패키지 전용)를
 * 반사로 그대로 부른다(v1 D-029 자동 저장과 같은 길). writer는 모델을 읽어 바이트로 쓸 뿐 바꾸지 않는다. 저장이 도구
 * 팩토리를 불러오는 원조 버릇은 {@link OwnTools}로 {@link Files#save}와 똑같이 되돌린다(D-149). 확장 정보는 저장처럼
 * 가리키는 것이 없어진 항목을 뺀 것(사본에서 빼고 원본은 그대로: 학생이 저장하기 전까지 되돌리기로 돌아올 수 있다).
 *
 * <p>상대 경로 라이브러리({@code jar#hcs-mips.jar#…})는 연 파일의 폴더 기준으로 적힌다. 복구 파일이 같은 폴더에 있으므로
 * 그 글자 그대로 풀린다.
 *
 * <p>쓰기는 같은 폴더의 {@code <이름>.circ.hcs-recover.tmp}에 쓴 뒤 옮긴다(전원이 나가도 반쯤 쓴 복구 파일이 남지 않게).
 * 옮기지 못하고 남은 조각은 다음 쓰기·지우기가 치운다.
 */
public final class RecoveryFiles {
    /** 복구 파일 이름: 학생 파일 이름 + 이것. */
    public static final String SUFFIX = ".hcs-recover";
    /** 쓰는 중의 조각. */
    static final String PART = ".tmp";

    private static final Method WRITE;

    static {
        try {
            Class<?> libLoader = Class.forName("com.cburch.logisim.file.LibraryLoader");
            WRITE = LogisimFile.class.getDeclaredMethod("write", OutputStream.class, libLoader);
            WRITE.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private RecoveryFiles() {
    }

    /** circ 옆의 복구 파일. */
    public static File of(File circ) {
        File abs = circ.getAbsoluteFile();
        return new File(abs.getParentFile(), abs.getName() + SUFFIX);
    }

    static File partOf(File circ) {
        File abs = circ.getAbsoluteFile();
        return new File(abs.getParentFile(), abs.getName() + SUFFIX + PART);
    }

    /** 복구 파일을 둘 수 있는 파일인가: 경로가 있고(한 번이라도 저장했거나 디스크에서 열었다) 읽기 전용이 아니다. */
    public static File target(Doc d) {
        File main = d.loader().getMainFile();
        return main == null || d.isReadOnly() ? null : main.getAbsoluteFile();
    }

    /** 지금 저장하면 쓰일 .circ 바이트(원조 writer + 정리한 확장 정보). */
    public static byte[] bytes(Doc d) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        d.loader().drainErrors();
        Set<Tool> untouched = OwnTools.untouched(d.loader(), d.file());
        try {
            WRITE.invoke(d.file(), out, d.loader());
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            throw c instanceof IOException ? (IOException) c : new IOException("cannot write the circuit: " + c, c);
        } catch (IllegalAccessException e) {
            throw new IOException("cannot write the circuit", e);
        } finally {
            OwnTools.afterSave(d.loader(), d.file(), untouched);
        }
        List<String> errs = d.loader().drainErrors();
        if (!errs.isEmpty() || out.size() == 0) {
            throw new IOException(errs.isEmpty() ? "nothing written" : String.join("\n", errs));
        }
        return CircExtensionIO.withExtension(out.toByteArray(), prunedCopy(d.file()));
    }

    /** 저장이 쓸 확장 정보: 원본의 사본에서 없어진 것을 가리키는 항목을 뺀 것. */
    static CircExtension prunedCopy(LogisimFile file) {
        CircExtension ext = CircExtensions.of(file);
        CircExtension copy = new CircExtension();
        for (String circuit : ext.circuits()) {
            for (CircExtension.Item item : ext.items(circuit)) {
                copy.add(circuit, new CircExtension.Item(item.kind(), new java.util.LinkedHashMap<>(item.attrs())));
            }
        }
        for (CircExtensions.Pruner p : Files.PRUNERS) {
            p.prune(file, copy);
        }
        return copy;
    }

    /**
     * 복구 파일을 쓴다(없으면 만들고 있으면 바꾼다). 둘 곳이 없는 파일(새 파일, 읽기 전용으로 연 파일)이나 쓸 수 없는
     * 폴더(읽기 전용 매체, 권한 없음)면 아무것도 하지 않고 null(조용히 건너뛴다).
     */
    public static File write(Doc d) throws IOException {
        File circ = target(d);
        if (circ == null || !writableFolder(circ)) {
            return null;
        }
        byte[] b = bytes(d);
        File dest = of(circ);
        File part = partOf(circ);
        try {
            java.nio.file.Files.write(part.toPath(), b);
            try {
                java.nio.file.Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(part.toPath());
        }
        return dest;
    }

    /** circ가 든 폴더에 파일을 만들 수 있는가. */
    static boolean writableFolder(File circ) {
        File dir = circ.getAbsoluteFile().getParentFile();
        return dir != null && java.nio.file.Files.isDirectory(dir.toPath()) && java.nio.file.Files.isWritable(dir.toPath());
    }

    /** circ 옆의 복구 파일(과 남은 조각)을 지운다. 지운 것이 있으면 true. */
    public static boolean delete(File circ) {
        if (circ == null) {
            return false;
        }
        boolean any = false;
        for (File f : new File[] {of(circ), partOf(circ)}) {
            try {
                any |= java.nio.file.Files.deleteIfExists(f.toPath());
            } catch (IOException e) {
                // 지우지 못하면 다음에 열 때 한 번 더 묻는다
            }
        }
        return any;
    }

    /** 파일 이름에서 확장자(.circ)를 뺀 원조 프로젝트 이름(Loader.toProjectName과 같다). */
    static String projectName(File circ) {
        String n = circ.getName();
        return n.endsWith(".circ") ? n.substring(0, n.length() - ".circ".length()) : n;
    }

    /** 복구 파일로 열 때 원조 Loader에 줄 바꿔 읽기 표: 학생 파일 자리에서 복구 파일의 내용을 읽는다. */
    static Map<File, File> substitution(File circ) {
        return Map.of(circ, of(circ));
    }
}
