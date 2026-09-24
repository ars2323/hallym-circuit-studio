/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.autosave;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.Timer;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.Projects;

import kr.ac.hallym.hcs.app.AppDirs;
import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.ext.CircExtensionIO;
import kr.ac.hallym.hcs.app.ext.CircExtensions;

/**
 * 자동 저장과 복구(#70). 몇 분마다 저장 안 된 변경이 있는 열린 파일을 앱 설정 폴더에 따로 저장한다.
 * 원본 .circ는 건드리지 않는다. 정상 저장하거나 닫으면 그 자동 저장을 지운다. 다음 실행 때 남아 있는 자동
 * 저장이 있으면 복구를 제안한다. 복구한 창에서 저장하면 원래 자리로 저장할지 묻는다.
 *
 * <p>내용은 원조 writer(LogisimFile.write, 패키지 전용)를 리플렉션으로 불러 쓴다. Loader.save는 열린 파일의 이름과
 * 저장 위치를 바꾸므로 쓰지 않는다. 엔진 코드는 바꾸지 않는다.
 */
public final class AutoSave {
    static final String MINUTES = "autosave.minutes";
    static final int DEFAULT_MINUTES = 2;

    private static final AutoSave INSTANCE = new AutoSave();

    private final AutoSaveStore store = new AutoSaveStore(new File(AppDirs.config(), "autosave"));
    private final Map<Project, String> keys = new IdentityHashMap<>();
    /** 복구한 창: 원래 파일. */
    private final Map<Project, File> recovered = new IdentityHashMap<>();
    private Timer timer;
    private int untitled;
    /** Projects는 리스너를 약한 참조로 두므로 여기서 붙잡아 둔다. */
    private final java.beans.PropertyChangeListener projectList = e -> forgetClosed();

    private AutoSave() {
    }

    public static AutoSave get() {
        return INSTANCE;
    }

    public AutoSaveStore store() {
        return store;
    }

    /** 앱 시작 때: 타이머를 켠다. */
    public synchronized void start() {
        if (timer != null) {
            return;
        }
        int min = Math.max(1, Settings.get().getInt(MINUTES, DEFAULT_MINUTES));
        int ms = min * 60 * 1000;
        String sec = System.getProperty("hcs.autosaveSeconds"); // 테스트·스모크용
        if (sec != null) {
            ms = Math.max(1, Integer.parseInt(sec)) * 1000;
        }
        timer = new Timer(ms, e -> saveAll());
        timer.setRepeats(true);
        timer.start();
        Projects.addPropertyChangeListener(Projects.projectListProperty, projectList);
    }

    synchronized String keyFor(Project p) {
        return keys.computeIfAbsent(p, q -> AutoSaveStore.key(mainFile(q), "w" + (++untitled)));
    }

    static File mainFile(Project p) {
        return p.getLogisimFile() == null ? null : p.getLogisimFile().getLoader().getMainFile();
    }

    /** 저장 안 된 변경이 있는 열린 파일을 모두 자동 저장한다. */
    public void saveAll() {
        for (Project p : new ArrayList<>(Projects.getOpenProjects())) {
            if (p.isFileDirty()) {
                try {
                    saveNow(p, System.currentTimeMillis());
                } catch (IOException | RuntimeException e) {
                    // 다음 주기에 다시 한다
                }
            }
        }
    }

    AutoSaveStore.Entry saveNow(Project p, long now) throws IOException {
        File original = recovered.containsKey(p) ? recovered.get(p) : mainFile(p);
        if (store.contains(original)) {
            original = null;
        }
        LogisimFile file = p.getLogisimFile();
        // 사용자가 지정한 추가 정보(hcs:ext)도 함께 둔다
        byte[] xml = CircExtensionIO.withExtension(serialize(file), CircExtensions.of(file));
        return store.write(keyFor(p), original, file.getDisplayName(), xml, now);
    }

    /** 원조 writer로 쓴 .circ 바이트(원래 파일 위치 기준 상대 경로). */
    static byte[] serialize(LogisimFile file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Class<?> libLoader = Class.forName("com.cburch.logisim.file.LibraryLoader");
            Method write = LogisimFile.class.getDeclaredMethod("write", OutputStream.class, libLoader);
            write.setAccessible(true);
            write.invoke(file, out, file.getLoader());
        } catch (ReflectiveOperationException e) {
            throw new IOException("cannot write circuit", e);
        }
        return out.toByteArray();
    }

    /** 정상 저장 뒤: 자동 저장을 지우고, 복구한 창이면 이제 원래 파일로 본다. */
    public void saved(Project p) {
        String key;
        synchronized (this) {
            key = keys.remove(p);
            recovered.remove(p);
        }
        if (key != null) {
            try {
                store.delete(key);
            } catch (IOException e) {
                // 다음 실행 때 복구 제안이 한 번 더 뜰 뿐이다
            }
        }
    }

    /** 닫힌 창의 자동 저장은 지운다(닫을 때 저장하지 않기를 고른 것이다). */
    private void forgetClosed() {
        List<Project> open = Projects.getOpenProjects();
        List<Project> closed = new ArrayList<>();
        synchronized (this) {
            for (Project p : keys.keySet()) {
                if (!open.contains(p)) {
                    closed.add(p);
                }
            }
        }
        for (Project p : closed) {
            saved(p);
        }
    }

    /** 자동 저장에서 복구한 창인가(저장할 때 어디에 저장할지 묻는다). */
    public synchronized boolean isRecovered(Project p) {
        return recovered.containsKey(p);
    }

    /** 다른 이름으로 저장 창에 미리 고를 파일: 복구한 창이면 원래 파일, 자동 저장 폴더의 파일은 고르지 않는다. */
    public synchronized File saveAsTarget(Project p) {
        if (recovered.containsKey(p)) {
            return recovered.get(p);
        }
        File f = mainFile(p);
        return store.contains(f) ? null : f;
    }

    /** 시작할 때: 남은 자동 저장이 있으면 복구를 제안한다. 복구한 창 수. */
    public int offerRecovery() {
        List<AutoSaveStore.Entry> left = store.list();
        if (left.isEmpty()) {
            return 0;
        }
        StringBuilder names = new StringBuilder();
        DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (AutoSaveStore.Entry e : left) {
            names.append("\n  • ").append(e.title).append("  (").append(fmt.format(new Date(e.savedAt))).append(')');
        }
        Object[] options = {Messages.get("autosave.recover"), Messages.get("autosave.discard")};
        int choice = JOptionPane.showOptionDialog(Projects.getTopFrame(),
                Messages.get("autosave.found", names.toString()), Messages.get("autosave.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        int n = 0;
        for (AutoSaveStore.Entry e : left) {
            String key = e.circ.getName().replaceAll("\\.circ$", "");
            if (choice == 0) {
                Project p = ProjectActions.doOpen(Projects.getTopFrame(), null, e.circ);
                if (p != null) {
                    synchronized (this) {
                        keys.put(p, key);
                        recovered.put(p, e.original);
                    }
                    p.getLogisimFile().setName(Messages.get("autosave.recoveredTitle", e.title)); // 자동 저장 파일 이름 대신
                    p.getLogisimFile().setDirty(true);
                    n++;
                    continue;
                }
            }
            try {
                store.delete(key);
            } catch (IOException ex) {
                // 무시
            }
        }
        return n;
    }
}
