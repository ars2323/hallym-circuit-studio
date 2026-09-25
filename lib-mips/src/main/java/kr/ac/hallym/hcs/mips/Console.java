/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.GraphicsUtil;

/**
 * Console(PLAN.md 6.9). 학생 회로가 {@code syscall}을 디코드해 {@code Syscall}을 1로 만들면, {@code clk}
 * 상승 에지에 {@code V0}의 번호대로 처리한다.
 *
 * <table>
 *   <tr><td>1</td><td>print_int: {@code A0}를 부호 있는 10진수로</td></tr>
 *   <tr><td>4</td><td>print_string: {@code A0} 주소부터 0 바이트까지. Data Memory·Stack에서 바이트 단위로 읽는다</td></tr>
 *   <tr><td>11</td><td>print_char: {@code A0}의 하위 8비트</td></tr>
 *   <tr><td>10</td><td>exit: {@code Exit} 출력을 1로 하고 시뮬레이션 클럭을 멈춘다. 그 뒤 syscall은 처리하지 않는다</td></tr>
 * </table>
 *
 * <p>그 밖의 번호, 떠 있는 {@code Syscall}, 정의되지 않은 {@code V0}·{@code A0}는 처리하지 않고 부품 안에 표시한다.
 * {@code Exit}은 원조 2.7.1 {@code -tty} 모드에서 {@code halt} 핀에 이어 프로그램 끝에서 멈추는 데 쓴다.
 */
final class Console extends InstanceFactory {
    static final int SYSCALL = 0;
    static final int V0 = 1;
    static final int A0 = 2;
    static final int CLK = 3;
    static final int EXIT = 4;

    static final int MAX_STRING = 4096;
    static final int ROWS = 7;
    static final int COLUMNS = 25;
    /** 출력 칸의 왼쪽·오른쪽 여백(포트 이름 자리). */
    static final int OUT_LEFT = 72;
    static final int OUT_RIGHT = 48;

    private static final BitWidth W32 = BitWidth.create(32);
    private static final Font TEXT_FONT = new Font("Monospaced", Font.PLAIN, 9);

    /** 한 시뮬레이션의 출력. 바이트로 모아 UTF-8로 읽는다(.s의 한글 문자열). */
    static final class State implements InstanceData, Cloneable {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Value lastClock = Value.UNKNOWN;
        boolean exited;
        boolean syscallFloating;
        String status; // 마지막 syscall의 문제

        @Override
        public State clone() {
            try {
                State s = (State) super.clone();
                s.bytes = new ByteArrayOutputStream();
                s.bytes.write(bytes.toByteArray(), 0, bytes.size());
                return s;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        String text() {
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }

        void print(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            bytes.write(b, 0, b.length);
        }
    }

    Console() {
        super("Console", Text.name("Console"));
        setAttributes(new Attribute<?>[] {StdAttr.LABEL, StdAttr.LABEL_FONT},
                new Object[] {"", StdAttr.DEFAULT_LABEL_FONT});
        setOffsetBounds(Bounds.create(-260, -60, 260, 120));
        Port syscall = new Port(-260, -40, Port.INPUT, 1);
        syscall.setToolTip(Text.of("Syscall: 1 when this cycle is a syscall", "Syscall: 이번 사이클이 syscall이면 1"));
        Port v0 = new Port(-260, 0, Port.INPUT, W32);
        v0.setToolTip(Text.of("V0: $v0 (service number)", "V0: $v0 (syscall 번호)"));
        Port a0 = new Port(-260, 40, Port.INPUT, W32);
        a0.setToolTip(Text.of("A0: $a0 (argument)", "A0: $a0 (인자)"));
        Port clk = new Port(-220, 60, Port.INPUT, 1);
        clk.setToolTip(Text.name("clk"));
        Port exit = new Port(0, 0, Port.OUTPUT, 1);
        exit.setToolTip(Text.of("Exit: 1 after exit (syscall 10)", "Exit: exit(syscall 10) 뒤 1"));
        setPorts(new Port[] {syscall, v0, a0, clk, exit});
    }

    static State state(InstanceState s) {
        State st = (State) s.getData();
        if (st == null) {
            st = new State();
            s.setData(st);
        }
        return st;
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        Bounds b = instance.getBounds();
        instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT,
                b.getX() + b.getWidth() / 2, b.getY() - 3, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
    }

    @Override
    public void propagate(InstanceState s) {
        State st = state(s);
        Value clk = s.getPort(CLK);
        boolean rising = Value.FALSE.equals(st.lastClock) && Value.TRUE.equals(clk);
        st.lastClock = clk;
        Value syscall = s.getPort(SYSCALL);
        st.syscallFloating = !syscall.isFullyDefined();
        if (rising && Value.TRUE.equals(syscall) && !st.exited) {
            st.status = service(s, st);
        }
        s.setPort(EXIT, st.exited ? Value.TRUE : Value.FALSE, 1);
    }

    /** syscall 하나를 처리한다. 문제가 있으면 표시할 문구를 돌려준다. */
    private static String service(InstanceState s, State st) {
        Value v0 = s.getPort(V0);
        Value a0 = s.getPort(A0);
        if (!v0.isFullyDefined()) {
            return Text.of("V0 undefined", "V0가 정의되지 않음").get();
        }
        int number = v0.toIntValue();
        if (number == 10) {
            st.exited = true;
            stopClock(s.getProject());
            return null;
        }
        if (number != 1 && number != 4 && number != 11) {
            return Text.of("syscall " + number + " not supported", "syscall " + number + " 지원 안 함").get();
        }
        if (!a0.isFullyDefined()) {
            return Text.of("A0 undefined", "A0가 정의되지 않음").get();
        }
        int arg = a0.toIntValue();
        if (number == 1) {
            st.print(Integer.toString(arg));
        } else if (number == 11) {
            st.bytes.write(arg & 0xff);
        } else {
            return printString(s.getProject(), st, arg);
        }
        return null;
    }

    private static String printString(Project project, State st, int addr) {
        for (int i = 0; i < MAX_STRING; i += 1) {
            int a = addr + i;
            MemoryRegistry.View mem = MemoryRegistry.find(project, a);
            if (mem == null) {
                return Text.of("string address " + hex(a) + " not in memory",
                        "문자열 주소 " + hex(a) + "가 메모리에 없음").get();
            }
            if (!mem.isDefined(a)) {
                return Text.of("undefined byte at " + hex(a), hex(a) + "의 바이트가 정의되지 않음").get();
            }
            int b = mem.readByte(a);
            if (b == 0) {
                return null;
            }
            st.bytes.write(b);
        }
        return Text.of("string longer than " + MAX_STRING, "문자열이 " + MAX_STRING + "바이트보다 김").get();
    }

    private static String hex(int a) {
        return "0x" + WordImage.hex(a);
    }

    private static void stopClock(Project project) {
        // 기록 엔진의 재실행(복제본)이 실제 시뮬레이터의 클럭을 멈추지 않게
        if (project != null && project.getSimulator() != null && !MemoryRegistry.isReplay()) {
            project.getSimulator().setIsTicking(false);
        }
    }

    /** 출력의 마지막 rows줄. 줄이 columns보다 길면 접는다. */
    static List<String> lastLines(String text, int rows, int columns) {
        List<String> lines = new ArrayList<String>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) {
                lines.add("");
            }
            for (int i = 0; i < line.length(); i += columns) {
                lines.add(line.substring(i, Math.min(line.length(), i + columns)));
            }
        }
        return lines.subList(Math.max(0, lines.size() - rows), lines.size());
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        Bounds b = painter.getBounds();
        painter.drawBounds();
        painter.drawLabel();
        g.setColor(Color.BLACK);
        g.setFont(MemoryFactory.TITLE_FONT);
        GraphicsUtil.drawCenteredText(g, Text.name("Console").get(), b.getX() + b.getWidth() / 2,
                b.getY() + 10);
        State st = painter.getShowState() ? (State) painter.getData() : null;
        // 출력 칸: 왼쪽 포트 이름(Syscall·V0·A0)과 오른쪽 Exit에서 떨어진 안쪽
        int x = b.getX() + OUT_LEFT;
        int y = b.getY() + 32;
        g.setColor(new Color(0xF4, 0xF6, 0xF8));
        g.fillRect(x - 4, b.getY() + 20, b.getWidth() - OUT_LEFT - OUT_RIGHT + 8, 78);
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(x - 4, b.getY() + 20, b.getWidth() - OUT_LEFT - OUT_RIGHT + 8, 78);
        g.setColor(Color.BLACK);
        g.setFont(TEXT_FONT);
        if (st != null) {
            for (String line : lastLines(st.text(), ROWS, COLUMNS)) {
                GraphicsUtil.drawText(g, line, x, y, GraphicsUtil.H_LEFT, GraphicsUtil.V_BASELINE);
                y += 11;
            }
            String status = st.exited ? Text.name("-- exit --").get()
                    : st.syscallFloating ? Text.of("Syscall floating", "Syscall 떠 있음").get() : st.status;
            if (status != null) {
                g.setColor(st.exited ? Color.GRAY : MemoryFactory.STATUS_COLOR);
                GraphicsUtil.drawText(g, status, x + (b.getWidth() - OUT_LEFT - OUT_RIGHT) / 2, b.getY() + 110,
                        GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
                g.setColor(Color.BLACK);
            }
        }
        MemoryFactory.drawPortInside(painter, SYSCALL, "Syscall");
        MemoryFactory.drawPortInside(painter, V0, "V0");
        MemoryFactory.drawPortInside(painter, A0, "A0");
        painter.drawClock(CLK, Direction.NORTH);
        MemoryFactory.drawPortInside(painter, EXIT, "Exit");
    }

    @Override
    public void paintIcon(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        g.setColor(Color.BLACK);
        g.drawRect(2, 3, 16, 13);
        g.setFont(new Font("Monospaced", Font.BOLD, 8));
        GraphicsUtil.drawCenteredText(g, ">_", 10, 9);
    }
}
