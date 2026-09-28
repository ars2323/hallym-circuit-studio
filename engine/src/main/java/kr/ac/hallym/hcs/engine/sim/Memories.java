/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.sim;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.proj.Action;
import com.cburch.hex.HexModel;
import com.cburch.hex.HexModelListener;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.memory.Rom;
import com.cburch.logisim.tools.MenuExtender;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * RAM·ROM의 우클릭 항목(원조 {@code MemMenu}: Edit Contents…, Clear Contents, Load Image…, Save Image…)을 창 없이
 * (N-10, D-157). 16진 편집기(화면)가 읽고 고치는 내용은 원조와 같은 곳이다.
 * <ul>
 * <li>RAM: 시뮬레이션 상태의 내용(원조 HexFrame이 RAM 상태를 고치는 것과 같다): .circ에 남지 않고 되돌리기에 들지
 * 않는다. 내용 객체는 원조 상태 객체에서 읽기만 해서 얻는다(반사는 읽기만, D-133).</li>
 * <li>ROM: 부품의 Contents 속성(.circ에 저장). 원조 메뉴처럼 원조 {@code RomContentsListener}를 달아
 * ({@code MemMenu.configureMenu}) 고칠 때마다 원조 "Edit ROM Contents" 동작이 되돌리기에 든다(편집 의도
 * edit.memContents).</li>
 * </ul>
 */
public final class Memories {
    /** 한 번에 읽는 워드 수의 상한. */
    public static final int MAX_READ = 4096;

    private Memories() {
    }

    public static boolean isRam(Component x) {
        return x.getFactory() instanceof Ram;
    }

    public static boolean isRom(Component x) {
        return x.getFactory() instanceof Rom;
    }

    /** RAM이 보이는 상태(root에서 path로 내려간 인스턴스). */
    static CircuitState state(Doc d, Circuit root, List<Component> path) throws RpcError {
        CircuitState rootState = d.project().getCircuitState(root);
        CircuitState s = path.isEmpty() ? rootState : InstancePaths.stateFor(rootState, path);
        if (s == null) {
            throw RpcError.notFound("instance path", String.valueOf(path.size()));
        }
        return s;
    }

    /** 부품의 내용: RAM은 상태, ROM은 속성. RAM 상태가 아직 없으면(전파 전) -32602가 아니라 오류 4 {@code notReady}. */
    static HexModel contents(Doc d, Circuit root, List<Component> path, Component x) throws RpcError {
        if (isRom(x)) {
            return romContents(x);
        }
        if (!isRam(x)) {
            throw RpcError.params("component " + d.ids().of(x) + " is not a RAM or a ROM");
        }
        Object data = state(d, root, path).getData(x);
        HexModel m = data == null ? null : readContents(data);
        if (m == null) {
            throw RpcError.simState("notReady", "the RAM has no state yet (the simulation has not reached it)");
        }
        return m;
    }

    /** 원조 MemState의 contents를 읽는다(쓰지 않는다). */
    static HexModel readContents(Object memState) {
        for (Class<?> k = memState.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                java.lang.reflect.Field f = k.getDeclaredField("contents");
                if (!HexModel.class.isAssignableFrom(f.getType())) {
                    return null;
                }
                f.setAccessible(true);
                return (HexModel) f.get(memState);
            } catch (NoSuchFieldException e) {
                continue;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /** mem.read: from부터 count 워드. 값은 부호 없는 수. */
    public static JsonObject read(Doc d, Circuit root, List<Component> path, Component x, long from, int count)
            throws RpcError {
        HexModel m = contents(d, root, path, x);
        long total = m.getLastOffset() + 1;
        if (count < 1 || count > MAX_READ) {
            throw RpcError.params("count must be 1.." + MAX_READ);
        }
        if (from < 0 || from >= total) {
            throw RpcError.params("from must be 0.." + (total - 1));
        }
        int n = (int) Math.min(count, total - from);
        long mask = m.getValueWidth() >= 32 ? 0xffffffffL : (1L << m.getValueWidth()) - 1;
        JsonArray words = new JsonArray();
        for (int i = 0; i < n; i++) {
            words.add(m.get(from + i) & mask);
        }
        JsonObject o = new JsonObject();
        o.addProperty("kind", isRom(x) ? "rom" : "ram");
        o.addProperty("addrBits", Long.numberOfTrailingZeros(total));
        o.addProperty("dataBits", m.getValueWidth());
        o.addProperty("from", from);
        o.addProperty("total", total);
        o.add("words", words);
        return o;
    }

    /** 고칠 값 하나하나가 폭 안인지(원조 HexFrame은 폭을 넘는 글자를 받지 않는다). */
    static int[] checked(HexModel m, long addr, List<Long> values) throws RpcError {
        long total = m.getLastOffset() + 1;
        if (addr < 0 || addr + values.size() > total) {
            throw RpcError.params("addresses must be within 0.." + (total - 1));
        }
        long max = m.getValueWidth() >= 32 ? 0xffffffffL : (1L << m.getValueWidth()) - 1;
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            long v = values.get(i);
            if (v < 0 || v > max) {
                JsonObject data = new JsonObject();
                data.addProperty("reason", "badValue");
                throw new RpcError(RpcError.INVALID_PARAMS, "value " + v + " does not fit in " + m.getValueWidth() + " bits",
                        data);
            }
            out[i] = (int) v;
        }
        return out;
    }

    // ---- RAM: 시뮬레이션 상태(SimGate 안, 원조 전파와 겹치지 않게) ----

    /** mem.write(RAM). */
    public static void writeRam(Doc d, Circuit root, List<Component> path, Component x, long addr, List<Long> values)
            throws RpcError {
        HexModel m = ram(d, root, path, x);
        m.set(addr, checked(m, addr, values)); // 원조 MemListener가 부품을 다시 전파할 곳으로 둔다
    }

    /** mem.clear(RAM, 원조 doClear: 확인은 화면이 묻는다). */
    public static boolean clearRam(Doc d, Circuit root, List<Component> path, Component x) throws RpcError {
        HexModel m = ram(d, root, path, x);
        return changes(m, () -> clear(m));
    }

    /** mem.loadImage(RAM, 원조 Mem.loadImage: HexFile.open). */
    public static void loadRam(Doc d, Circuit root, List<Component> path, Component x, String file) throws RpcError {
        HexModel m = ram(d, root, path, x);
        try {
            HexFile.open(m, new File(file));
        } catch (IOException e) {
            throw RpcError.file(file, "unreadable", "cannot load the image: " + e.getMessage());
        }
    }

    private static HexModel ram(Doc d, Circuit root, List<Component> path, Component x) throws RpcError {
        if (!isRam(x)) {
            throw RpcError.params("component " + d.ids().of(x) + " is not a RAM (ROM contents: edit.memContents)");
        }
        return contents(d, root, path, x);
    }

    /** mem.saveImage(RAM·ROM, 원조 doSave: HexFile.save). 모델은 그대로. */
    public static void save(Doc d, Circuit root, List<Component> path, Component x, String file) throws RpcError {
        HexModel m = contents(d, root, path, x);
        try {
            HexFile.save(new File(file), m);
        } catch (IOException e) {
            throw RpcError.file(file, "writeFailed", "cannot save the image: " + e.getMessage());
        }
    }

    // ---- ROM: 편집 의도(edit.memContents, 되돌리기) ----

    /**
     * ROM 내용 고치기: 값 쓰기(addr, values), 비우기(clear), 이미지 읽기(path). 원조 RomContentsListener가 바뀐 곳마다
     * 원조 동작("Edit ROM Contents")을 되돌리기에 넣는다(붙은 곳은 한 단계로 합친다, 원조 Change.shouldAppendTo).
     * 바뀐 것이 없으면 false.
     */
    public static boolean editRom(Doc d, Circuit c, Component x, Long addr, List<Long> values, boolean clear,
            String file) throws RpcError {
        if (!isRom(x)) {
            throw RpcError.params("component " + d.ids().of(x) + " is not a ROM");
        }
        HexModel m = romContents(x);
        listen(d, x);
        d.show(c);
        Action before = d.project().getLastAction();
        if (clear) {
            clear(m);
        } else if (file != null) {
            try {
                HexFile.open(m, new File(file));
            } catch (IOException e) {
                throw RpcError.file(file, "unreadable", "cannot load the image: " + e.getMessage());
            }
        } else if (addr != null && values != null) {
            m.set(addr, checked(m, addr, values));
        } else {
            throw RpcError.params("give addr and values, clear, or path");
        }
        return d.project().getLastAction() != before;
    }

    /** ROM의 Contents 속성 값(원조 Rom.CONTENTS_ATTR, 원조 내용 객체는 HexModel로 다룬다). */
    static HexModel romContents(Component x) {
        Attribute<?> a = x.getAttributeSet().getAttribute("contents");
        Object v = a == null ? null : x.getAttributeSet().getValue(a);
        return (HexModel) v;
    }

    /** 원조 MemContents.clear와 같은 결과(모든 워드 0): 페이지마다 원조 알림(되돌리기)이 따른다. */
    static void clear(HexModel m) {
        m.fill(0, m.getLastOffset() + 1, 0);
    }

    /** body가 내용을 바꿨는가(원조 내용 객체의 알림을 센다). */
    static boolean changes(HexModel m, Runnable body) {
        boolean[] changed = {false};
        HexModelListener l = new HexModelListener() {
            @Override
            public void metainfoChanged(HexModel source) {
            }

            @Override
            public void bytesChanged(HexModel source, long start, long numBytes, int[] oldValues) {
                changed[0] = true;
            }
        };
        m.addHexModelListener(l);
        try {
            body.run();
        } finally {
            m.removeHexModelListener(l);
        }
        return changed[0];
    }

    /** 원조 MemMenu.configureMenu가 하는 것처럼 ROM 내용에 원조 되돌리기 청취자를 단다(RomAttributes.setProject). */
    static void listen(Doc d, Component x) {
        Object ext = x.getFeature(MenuExtender.class);
        if (ext instanceof MenuExtender) {
            ((MenuExtender) ext).configureMenu(new JPopupMenu(), d.project());
        }
    }
}
