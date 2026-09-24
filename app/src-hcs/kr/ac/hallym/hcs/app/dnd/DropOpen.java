/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.dnd;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.ProjectActions;

/**
 * 창에 .circ 파일을 끌어 놓으면 연다(#70, PLAN.md 11.13). 이미 열린 파일이면 그 탭으로 간다(ProjectActions가
 * 처리). .circ가 아닌 파일은 받지 않는다.
 */
public final class DropOpen extends DropTargetAdapter {
    private final Frame frame;

    private DropOpen(Frame frame) {
        this.frame = frame;
    }

    /** 창과 그 안의 주요 부분(캔버스 등)에 붙인다. */
    public static void install(Frame frame, Component... parts) {
        DropOpen d = new DropOpen(frame);
        new DropTarget(frame.getRootPane(), DnDConstants.ACTION_COPY, d, true);
        for (Component c : parts) {
            new DropTarget(c, DnDConstants.ACTION_COPY, d, true);
        }
    }

    /** 끌어 온 것 중 .circ 파일. */
    static List<File> circFiles(Transferable t) {
        List<File> ret = new ArrayList<>();
        if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return ret;
        }
        try {
            for (Object o : (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor)) {
                if (o instanceof File && ((File) o).getName().toLowerCase().endsWith(Loader.LOGISIM_EXTENSION)) {
                    ret.add((File) o);
                }
            }
        } catch (UnsupportedFlavorException | IOException e) {
            // 읽을 수 없는 끌기는 무시한다
        }
        return ret;
    }

    @Override
    public void dragEnter(DropTargetDragEvent e) {
        if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            e.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            e.rejectDrag();
        }
    }

    @Override
    public void drop(DropTargetDropEvent e) {
        e.acceptDrop(DnDConstants.ACTION_COPY);
        List<File> files = circFiles(e.getTransferable());
        e.dropComplete(!files.isEmpty());
        for (File f : files) {
            ProjectActions.doOpen(frame, frame.getProject(), f);
        }
    }
}
