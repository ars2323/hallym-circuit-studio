/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tabs;

import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.proj.Projects;

import kr.ac.hallym.hcs.app.Settings;

/**
 * 파일 탭(#68, PLAN.md 11.1). Logisim은 프로젝트(열린 파일)마다 창이 하나다. 이 창들을 같은 자리·크기에
 * 겹쳐 두고 활성 탭의 창만 보이게 해서 한 창에 탭이 있는 것처럼 쓴다. 되돌리기 기록, 시뮬레이션 상태는
 * 원래 프로젝트마다 따로라 탭마다 따로가 된다. 탭 목록은 {@link TabModel} 하나이고 창마다 {@link FileTabBar}가
 * 그것을 그린다. 열린 파일 목록은 앱 환경설정에 남겨 다음 실행 때 복원한다. 종료(File › Exit)는 창을 닫지 않고
 * 끝나므로 목록이 남고, 탭을 하나씩 닫으면 목록에서 빠진다.
 */
public final class FileTabs {
    static final String OPEN = "tabs.open";
    static final String ACTIVE = "tabs.active";
    /** 분리한 창(P-06): 경로 목록과 같은 차례의 창 자리 "x,y,w,h". */
    static final String DETACHED = "tabs.detached";
    static final String DETACHED_BOUNDS = "tabs.detachedBounds";

    private static final FileTabs INSTANCE = new FileTabs();

    private final TabModel<Project> model = new TabModel<>();
    /** 프로젝트마다 붙인 리스너. Logisim은 리스너를 약한 참조로 두므로 탭이 열려 있는 동안 여기서 붙잡는다. */
    private final Map<Project, List<Object>> watched = new HashMap<>();
    /** Projects는 리스너를 약한 참조로 두므로 여기서 붙잡아 둔다. */
    private final java.beans.PropertyChangeListener projectList = e -> sync();
    private boolean installed;

    private FileTabs() {
    }

    public static FileTabs get() {
        return INSTANCE;
    }

    public TabModel<Project> model() {
        return model;
    }

    /** 앱 시작 때 한 번. Projects의 열린 프로젝트 목록을 따라간다. */
    public synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        Projects.addPropertyChangeListener(Projects.projectListProperty, projectList); // 약한 참조로 보관된다
        model.addListener(this::showActive);
        model.addListener(this::saveRestoreList);
    }

    /** 새 창을 보이기 전에: 지금 보이는 탭 창과 같은 자리·크기로 둔다. */
    public void placeLikeActive(Frame frame) {
        Project active = model.active();
        Frame cur = active == null ? null : active.getFrame();
        if (cur != null && cur != frame && cur.isShowing()) {
            copyBounds(cur, frame);
        }
    }

    /** 탭을 닫는다. 창 닫기와 같은 확인(저장할지 묻기)을 거친다. */
    public void close(Project proj) {
        Frame f = proj.getFrame();
        if (f == null) {
            return;
        }
        model.activate(proj);
        f.dispatchEvent(new WindowEvent(f, WindowEvent.WINDOW_CLOSING));
    }

    private void sync() {
        List<Project> open = new ArrayList<>(Projects.getOpenProjects());
        for (TabModel.Tab<Project> t : model.tabs()) {
            if (!open.contains(t.key())) {
                watched.remove(t.key());
                model.remove(t.key());
            }
        }
        Project opened = null;
        for (Project p : open) {
            if (!watched.containsKey(p)) {
                watched.put(p, watch(p));
                model.add(p, file(p), title(p));
                opened = p;
            }
            refresh(p);
        }
        if (opened != null) {
            model.activate(opened); // 새로 연 파일이 활성 탭
        }
        activateRestored();
    }

    private List<Object> watch(Project p) {
        List<Object> keep = new ArrayList<>();
        LibraryListener lib = e -> {
            if (e.getAction() == LibraryEvent.DIRTY_STATE || e.getAction() == LibraryEvent.SET_NAME) {
                SwingUtilities.invokeLater(() -> refresh(p));
            }
        };
        ProjectListener proj = e -> SwingUtilities.invokeLater(() -> refresh(p));
        p.addLibraryListener(lib);
        p.addProjectListener(proj);
        keep.add(lib);
        keep.add(proj);
        Frame f = p.getFrame();
        if (f != null) {
            f.addWindowListener(new WindowAdapter() {
                @Override
                public void windowActivated(WindowEvent e) {
                    if (f.isVisible()) {
                        model.activate(p); // 창 메뉴 등으로 다른 창이 앞에 오면 그 탭으로
                    }
                }
            });
        }
        return keep;
    }

    void refresh(Project p) {
        model.update(p, file(p), title(p), p.isFileDirty());
    }

    static File file(Project p) {
        return p.getLogisimFile() == null ? null : p.getLogisimFile().getLoader().getMainFile();
    }

    static String title(Project p) {
        return p.getLogisimFile() == null ? "" : p.getLogisimFile().getDisplayName();
    }

    /** 무리에서 마지막으로 활성이었던 탭(P-06): 분리한 창이 활성이어도 무리 창은 이 탭을 보인다. */
    private Project groupActive;

    /**
     * 활성 탭의 창을 보인다. 무리(겹치는 창들)는 늘 한 창만 보이고, 분리한 창(P-06)은 늘 보이며 무리에 끼지 않는다.
     * 활성 탭을 실행 시점에 읽으므로(예약이 몰려도) 마지막 상태로 수렴한다.
     */
    private void showActive() {
        SwingUtilities.invokeLater(() -> {
            Project active = model.active();
            if (active == null) {
                return;
            }
            if (!model.isDetached(active)) {
                groupActive = active;
            }
            Project g = groupActive != null && model.has(groupActive) ? groupActive : null;
            if (g == null) {
                for (TabModel.Tab<Project> t : model.tabs()) {
                    if (!model.isDetached(t.key())) {
                        g = t.key();
                        break;
                    }
                }
                groupActive = g;
            }
            Frame show = g == null ? null : g.getFrame();
            if (show != null) {
                Frame from = null;
                for (TabModel.Tab<Project> t : model.tabs()) {
                    Frame f = t.key().getFrame();
                    if (f != null && f != show && f.isShowing() && !model.isDetached(t.key())) {
                        from = f;
                    }
                }
                if (from != null) {
                    copyBounds(from, show);
                }
                if (!show.isVisible()) {
                    show.setVisible(true);
                }
                for (TabModel.Tab<Project> t : model.tabs()) {
                    Frame f = t.key().getFrame();
                    if (f != null && f != show && f.isVisible() && !model.isDetached(t.key())) {
                        f.setVisible(false);
                    }
                }
            }
            Frame front = active.getFrame();
            if (front != null) {
                if (!front.isVisible()) {
                    front.setVisible(true);
                }
                front.toFront();
            }
        });
    }

    /** 겹치는 무리에서 지금 보이는 창(없으면 null). */
    Frame groupFrame() {
        for (TabModel.Tab<Project> t : model.tabs()) {
            Frame f = t.key().getFrame();
            if (f != null && f.isShowing() && !model.isDetached(t.key())) {
                return f;
            }
        }
        return null;
    }

    /** 탭을 제 창으로 분리한다(P-06): 무리 창에서 조금 비켜 둔 자리에 보인다. 무리는 이웃 탭을 보인다. */
    public void detach(Project p) {
        Frame f = p.getFrame();
        if (f == null || model.isDetached(p)) {
            return;
        }
        Frame group = groupFrame();
        Rectangle r = group == null ? f.getBounds() : group.getBounds();
        Rectangle screen = screenOf(f);
        Rectangle at = new Rectangle(Math.min(r.x + 60, screen.x + screen.width - r.width),
                Math.min(r.y + 60, screen.y + screen.height - r.height), r.width, r.height);
        if (groupActive == p) {
            groupActive = null; // 무리는 이웃 탭을 보인다(showActive가 고른다)
        }
        model.detach(p);
        f.setExtendedState(Frame.NORMAL);
        f.setBounds(at);
        f.setVisible(true);
        f.toFront();
        saveRestoreList();
    }

    /** 분리한 창을 무리로 되돌린다: 무리 창과 같은 자리·크기로 겹친다. */
    public void attach(Project p) {
        Frame f = p.getFrame();
        if (f == null || !model.isDetached(p)) {
            return;
        }
        Frame group = groupFrame();
        model.attach(p);
        if (group != null) {
            copyBounds(group, f);
        }
        model.activate(p);
        saveRestoreList();
    }

    /** 나란히 보기(P-06): p를 분리해 화면 오른쪽 반에, 무리 창을 왼쪽 반에 둔다. */
    public void sideBySide(Project p) {
        Frame f = p.getFrame();
        if (f == null) {
            return;
        }
        if (!model.isDetached(p)) {
            detach(p);
        }
        Rectangle s = screenOf(f);
        Rectangle left = new Rectangle(s.x, s.y, s.width / 2, s.height);
        Rectangle right = new Rectangle(s.x + s.width / 2, s.y, s.width - s.width / 2, s.height);
        for (TabModel.Tab<Project> t : model.tabs()) {
            Frame g = t.key().getFrame();
            if (g != null && !model.isDetached(t.key())) {
                g.setExtendedState(Frame.NORMAL);
                g.setBounds(left);
            }
        }
        f.setExtendedState(Frame.NORMAL);
        f.setBounds(right);
        f.toFront();
        saveRestoreList();
    }

    static Rectangle screenOf(Frame f) {
        java.awt.GraphicsConfiguration gc = f.getGraphicsConfiguration();
        if (gc == null) {
            return new Rectangle(0, 0, 1920, 1080);
        }
        Rectangle b = gc.getBounds();
        java.awt.Insets in = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    }

    /** 창 자리 "x,y,w,h". */
    static String bounds(Frame f) {
        Rectangle r = f.getBounds();
        return r.x + "," + r.y + "," + r.width + "," + r.height;
    }

    static Rectangle parseBounds(String s) {
        try {
            String[] a = s.split(",");
            return new Rectangle(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]),
                    Integer.parseInt(a[3]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void copyBounds(Frame from, Frame to) {
        int state = from.getExtendedState();
        Rectangle r = from.getBounds();
        if ((state & Frame.MAXIMIZED_BOTH) == 0) {
            to.setBounds(r);
        }
        to.setExtendedState(state & ~Frame.ICONIFIED);
    }

    private void saveRestoreList() {
        Settings s = Settings.get();
        s.setList(OPEN, model.restoreList());
        s.set(ACTIVE, model.restoreActive());
        List<String> det = model.detachedFiles();
        List<String> bounds = new ArrayList<>();
        for (String path : det) {
            Project p = model.find(new File(path));
            bounds.add(p == null || p.getFrame() == null ? "" : bounds(p.getFrame()));
        }
        s.setList(DETACHED, det);
        s.setList(DETACHED_BOUNDS, bounds);
        try {
            s.save();
        } catch (IOException e) {
            // 다음 실행 때 복원되지 않을 뿐이다
        }
    }

    private File restoreActive;
    private List<File> restoring = new ArrayList<>();
    private boolean restoreDone;

    /** 지난번에 열려 있던 파일(있는 것만). 그때 활성 탭은 {@link #afterRestore}에서 다시 활성으로 한다. */
    List<File> restoreFiles() {
        List<String> paths = Settings.get().getList(OPEN);
        int active = Settings.get().getInt(ACTIVE, -1);
        List<File> ret = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            File f = new File(paths.get(i));
            if (f.isFile()) {
                ret.add(f);
                restoring.add(f);
                if (i == active) {
                    restoreActive = f;
                }
            }
        }
        return ret;
    }

    /** 파일 하나를 연다. 실패하면 예외. */
    public interface Opener {
        void open(File f) throws Exception;
    }

    /**
     * 지난번에 열려 있던 파일을 연다. 없어졌거나 열 수 없는 파일은 건너뛴다(명령줄로 준 파일과 달리 프로그램을
     * 끝내지 않는다). 하나라도 열었으면 true.
     */
    public boolean openRestored(Opener opener) {
        boolean any = false;
        List<File> files = restoreFiles();
        for (File f : files) {
            try {
                opener.open(f);
                any = true;
            } catch (Exception | LinkageError e) {
                restoring.remove(f);
                if (f.equals(restoreActive)) {
                    restoreActive = null;
                }
            }
        }
        afterRestore();
        return any;
    }

    /** 복원한 파일을 모두 연 뒤: 지난번 활성 탭으로. 창이 열렸다는 알림이 늦게 오면 그때 한다. */
    void afterRestore() {
        restoreDone = true;
        SwingUtilities.invokeLater(this::activateRestored);
    }

    /** 지난번에 분리해 둔 창을 다시 분리하고 그 자리에 둔다(P-06). */
    void restoreDetached() {
        List<String> det = Settings.get().getList(DETACHED);
        List<String> bounds = Settings.get().getList(DETACHED_BOUNDS);
        for (int i = 0; i < det.size(); i++) {
            Project p = model.find(new File(det.get(i)));
            if (p == null || p.getFrame() == null) {
                continue;
            }
            model.detach(p);
            Rectangle r = i < bounds.size() ? parseBounds(bounds.get(i)) : null;
            if (r != null && r.width > 100 && r.height > 100) {
                p.getFrame().setExtendedState(Frame.NORMAL);
                p.getFrame().setBounds(r);
            }
            p.getFrame().setVisible(true);
        }
    }

    private void activateRestored() {
        if (!restoreDone || restoreActive == null) {
            return;
        }
        for (File f : restoring) {
            if (model.find(f) == null) {
                return; // 아직 열리는 중
            }
        }
        Project p = model.find(restoreActive);
        restoreActive = null;
        restoring.clear();
        restoreDetached();
        if (p != null) {
            // 마지막에 연 창의 활성화 알림이 지나간 뒤에 바꾼다
            javax.swing.Timer t = new javax.swing.Timer(400, e -> model.activate(p));
            t.setRepeats(false);
            t.start();
        }
    }
}
