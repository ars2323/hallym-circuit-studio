/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.gui.GuiTestSupport;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * P-06 GUI: 두 창을 탭으로 겹쳐 두었을 때 하나를 분리하면 둘 다 보이고 비켜 놓이며, 나란히 보기는 화면을 반씩 나누고,
 * 되돌리면 다시 겹친다. 복원 목록에 분리한 파일과 자리가 남는다.
 */
@Tag("gui")
class TabsLayoutGuiTest {
    @TempDir
    Path tmp;
    private Frame a;
    private Frame b;

    @AfterEach
    void clear() throws Exception {
        Settings.get().setList(FileTabs.DETACHED, Collections.emptyList());
        Settings.get().setList(FileTabs.DETACHED_BOUNDS, Collections.emptyList());
        for (Frame f : new Frame[] {a, b}) {
            if (f != null) {
                SwingUtilities.invokeAndWait(f::dispose);
            }
        }
    }

    Frame frame(String name, int x) throws Exception {
        File file = tmp.resolve(name).toFile();
        LogisimFile lf = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder.save(lf, file);
        Project proj = new Project(lf);
        AtomicReference<Frame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame f = new Frame(proj);
            proj.setFrame(f);
            f.setBounds(x, 0, 900, 700);
            f.setVisible(true);
            fr.set(f);
        });
        if (FileTabs.get().model().find(file) == null) {
            FileTabs.get().model().add(proj, file, name); // 앱에서는 Projects 알림이 더한다
        }
        return fr.get();
    }

    /** V-05: 같은 이름의 파일 둘은 탭·창 제목에 폴더가 붙고, 같은 파일을 다시 열면 새 탭 대신 기존 탭으로 간다. */
    @Test
    void sameNamedFilesShowTheirFolderAndReopeningGoesToTheExistingTab() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        FileTabs.get().install();
        java.nio.file.Files.createDirectories(tmp.resolve("circ"));
        java.nio.file.Files.createDirectories(tmp.resolve("hw3"));
        a = frame("circ/demo-datapath.circ", 0);
        b = frame("hw3/demo-datapath.circ", 100);
        Project pa = a.getProject();
        Project pb = b.getProject();
        settle();
        java.util.Map<Project, String> d = FileTabs.get().suffixes();
        assertEquals("circ", d.get(pa));
        assertEquals("hw3", d.get(pb));
        assertEquals("demo-datapath \u2014 circ", FileTabs.displayName(pa));
        SwingUtilities.invokeAndWait(() -> {
            a.recomputeTitle();
            b.recomputeTitle();
        });
        assertTrue(a.getTitle().startsWith("demo-datapath \u2014 circ"), a.getTitle());
        assertTrue(b.getTitle().startsWith("demo-datapath \u2014 hw3"), b.getTitle());
        // 같은 파일을 다시 열면 기존 프로젝트로(원조 findProjectFor)
        int before = com.cburch.logisim.proj.Projects.getOpenProjects().size();
        AtomicReference<Project> again = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> again.set(com.cburch.logisim.proj.ProjectActions.doOpen(a, pa,
                tmp.resolve("hw3/demo-datapath.circ").toFile())));
        settle();
        assertEquals(before, com.cburch.logisim.proj.Projects.getOpenProjects().size(), "no new tab");
        assertTrue(again.get() == pb || again.get() == null, "the existing project");
        assertEquals(pb, FileTabs.get().model().active(), "the existing tab is active");
    }

    static void settle() throws Exception {
        for (int i = 0; i < 5; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(60);
        }
    }

    @Test
    void detachShowsBothWindowsAndAttachOverlapsAgain() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (xvfb-run)");
        GuiTestSupport.keepAlive();
        FileTabs.get().install(); // 앱 시작 때처럼 탭 모델을 창에 잇는다(한 번만)
        a = frame("a.circ", 0);
        b = frame("b.circ", 0);
        FileTabs tabs = FileTabs.get();
        Project pa = a.getProject();
        Project pb = b.getProject();
        tabs.model().activate(pb);
        settle();
        assertTrue(b.isVisible());
        assertFalse(a.isVisible(), "overlapping group shows only the active tab");

        SwingUtilities.invokeAndWait(() -> tabs.detach(pb));
        settle();
        assertTrue(tabs.model().isDetached(pb));
        tabs.model().activate(pa); // 무리 창을 누른 것처럼(테스트 JVM에는 keepAlive 창의 탭도 있다)
        settle();
        assertTrue(a.isVisible() && b.isVisible(), "the group shows its other tab, the detached one stays: a="
                + a.isVisible() + " b=" + b.isVisible() + " active=" + (tabs.model().active() == pa ? "a" : "b")
                + " tabs=" + tabs.model().size());
        assertFalse(a.getBounds().equals(b.getBounds()), "the detached window is set off");
        assertTrue(Settings.get().getList(FileTabs.DETACHED).contains(tmp.resolve("b.circ").toFile().getAbsolutePath()));
        assertEquals(1, Settings.get().getList(FileTabs.DETACHED_BOUNDS).size());

        SwingUtilities.invokeAndWait(() -> tabs.sideBySide(pb));
        settle();
        Rectangle ra = a.getBounds();
        Rectangle rb = b.getBounds();
        assertTrue(ra.x + ra.width <= rb.x + 2, "left and right halves " + ra + " " + rb);
        assertTrue(a.isVisible() && b.isVisible());

        SwingUtilities.invokeAndWait(() -> tabs.attach(pb));
        settle();
        assertFalse(tabs.model().isDetached(pb));
        assertEquals(a.getBounds(), b.getBounds(), "attached: same place and size again");
        assertTrue(b.isVisible());
        assertFalse(a.isVisible());
        assertTrue(Settings.get().getList(FileTabs.DETACHED).isEmpty());
        tabs.model().remove(pa);
        tabs.model().remove(pb);
    }
}
