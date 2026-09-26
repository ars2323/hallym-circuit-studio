/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tutorial;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.sim.SimControls;

/**
 * Help › Examples(V-07, D-102): 번들된 예제 .circ를 임시 폴더에 풀어 읽기 전용으로 연다. 저장하면 다른 이름으로
 * 저장을 묻고, 다른 자리에 저장한 뒤로는 보통 파일이다.
 */
public final class Examples {
    static final String DIR = "/kr/ac/hallym/hcs/app/examples/";
    /** 번들된 예제 이름(파일 이름에서 .circ를 뺀 것). */
    public static final List<String> NAMES = Collections.unmodifiableList(Arrays.asList("demo-datapath",
            "console-demo", "stack-demo"));

    private static final Map<Project, File> OPEN = Collections.synchronizedMap(new WeakHashMap<>());

    private Examples() {
    }

    /** 예제를 임시 폴더에 풀어 그 파일을 돌려준다. 이름마다 새 폴더라 서로 겹치지 않는다. */
    public static File extract(String name) throws IOException {
        if (!NAMES.contains(name)) {
            throw new IOException("no example " + name);
        }
        try (InputStream in = Examples.class.getResourceAsStream(DIR + name + ".circ")) {
            if (in == null) {
                throw new IOException("example not bundled: " + name);
            }
            File dir = Files.createTempDirectory("hcs-example-" + name).toFile();
            File f = new File(dir, name + ".circ");
            Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            f.setReadOnly();
            return f;
        }
    }

    /** 예제를 새 탭에 연다(GUI). 읽기 전용 표시를 남기고 상태 표시줄에 알린다. */
    public static Project open(java.awt.Component parent, Project base, String name) {
        File f;
        try {
            f = extract(name);
        } catch (IOException e) {
            SimControls.notice(base, Messages.get("examples.failed", e.getMessage()));
            return null;
        }
        Project p = ProjectActions.doOpen(parent, base, f);
        if (p != null) {
            mark(p, f);
            SimControls.notice(p, Messages.get("examples.readOnly"));
        }
        return p;
    }

    /** 이 프로젝트가 읽기 전용 예제인가(다른 이름으로 저장하기 전까지). */
    public static boolean isExample(Project p) {
        return p != null && OPEN.containsKey(p);
    }

    static void mark(Project p, File f) {
        OPEN.put(p, f);
    }

    /** 저장 뒤: 예제의 임시 파일이 아닌 곳에 저장했으면 보통 파일이 된다. */
    public static void saved(Project p, File to) {
        File f = OPEN.get(p);
        if (f != null && to != null && !f.getAbsoluteFile().equals(to.getAbsoluteFile())) {
            OPEN.remove(p);
        }
    }

    /** Save가 다른 이름으로 저장을 물어야 하는가: 아직 예제인 프로젝트. */
    public static boolean interceptsSave(Project p) {
        return isExample(p);
    }

    /** 테스트용. */
    static void clear() {
        OPEN.clear();
    }
}
