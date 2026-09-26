/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.SelectTool;
import com.cburch.logisim.tools.Tool;

/**
 * Signal Flow 켜고 끄기와 애니메이션(P-07). 캔버스마다 하나.
 * <ul>
 * <li>시작: 선택(편집) 도구로 부품·선을 누르고 끌지 않고 놓으면 약 150ms 뒤에 시작한다. 그 사이 두 번째 누름이나
 * 끌기가 오면 취소한다(더블클릭·끌기·사각 선택을 방해하지 않는다). Shift를 누르고 누르면 Backward. 출력 포트 가까이를
 * 누르면 그 포트에서만. 우클릭 메뉴와 명령으로도 시작한다. 프로그램이 선택을 바꾼 경우(찾기, Messages)는 마우스를
 * 거치지 않으므로 시작하지 않는다.</li>
 * <li>멈춤: Esc, 빈 곳 누름, 다른 대상 누름(그 대상으로 새로 시작), 회로 편집(어떤 변경 사건이든), 탭·서브회로
 * 전환, 파일 닫기. 멈추면 Timer를 완전히 세우고 한 번만 다시 그려 흔적을 지운다.</li>
 * <li>창이 최소화되거나 캔버스가 가려지면 일시정지하고, 다시 보이면 이어서 흐른다.</li>
 * <li>매 프레임 경로를 감싼 상자만 다시 그린다. 경로·상자는 시작할 때 한 번 계산한다(Active Path Only는 값이 바뀌면
 * 짧게 기다렸다가 다시 계산한다).</li>
 * </ul>
 */
public final class FlowController {
    /** 누르고 놓은 뒤 시작까지(ms). */
    static final int CLICK_DELAY = 150;
    /** 이만큼(화면 px) 움직이면 끌기로 본다. */
    static final int DRAG_SLOP = 4;
    /** Active Path Only 값 변경 뒤 다시 계산하기까지(ms). */
    static final int RECOMPUTE_DELAY = 120;

    private static final Map<Canvas, FlowController> ALL = new WeakHashMap<>();

    private final Canvas canvas;
    private SignalFlowPath path;
    private Circuit shownCircuit;
    /** 시작 대상(Active Path Only 다시 계산에 쓴다). */
    private Component startComponent;
    private int startEnd = -1;
    private Wire startWire;
    private Location startClick;
    private boolean backward;
    private Rectangle box;
    private final Timer timer;
    private final Timer clickDelay;
    private final Timer recompute;
    /** 흐른 시간(ms). 일시정지하면 멈춘다. */
    private long elapsedMs;
    private long lastTickNanos;
    private boolean paused;
    /** 테스트·스크린샷: null이 아니면 이 시각(회로 단위)에 멈춘 장면. */
    private Double frozen;
    private MouseEvent pressed;
    private boolean dragged;
    private final CircuitListener edits = this::circuitChanged;
    private Circuit listening;
    private final ProjectListener projectListener = this::projectChanged;
    /** 클럭 틱·전파가 끝나면(값이 바뀌면) Active Path Only를 다시 계산한다. 약한 참조라 붙잡아 둔다. */
    private final java.util.concurrent.atomic.AtomicBoolean valuesPending =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final com.cburch.logisim.circuit.SimulatorListener simListener =
            new com.cburch.logisim.circuit.SimulatorListener() {
                @Override
                public void propagationCompleted(com.cburch.logisim.circuit.SimulatorEvent e) {
                    // 전파마다 요청을 하나로 모은다(D-091: 빠른 클럭·발진에서 EDT가 넘치지 않게)
                    if (valuesPending.compareAndSet(false, true)) {
                        SwingUtilities.invokeLater(() -> {
                            valuesPending.set(false);
                            valuesChanged();
                        });
                    }
                }

                @Override
                public void tickCompleted(com.cburch.logisim.circuit.SimulatorEvent e) {
                }

                @Override
                public void simulatorStateChanged(com.cburch.logisim.circuit.SimulatorEvent e) {
                }
            };

    private FlowController(Canvas canvas) {
        this.canvas = canvas;
        timer = new Timer(1000 / FlowSettings.fps(), e -> tick());
        timer.setCoalesce(true);
        clickDelay = new Timer(CLICK_DELAY, e -> fireClick());
        clickDelay.setRepeats(false);
        recompute = new Timer(RECOMPUTE_DELAY, e -> recomputeActive());
        recompute.setRepeats(false);
    }

    public static synchronized FlowController of(Canvas canvas) {
        return ALL.computeIfAbsent(canvas, FlowController::new);
    }

    /** 이 캔버스의 컨트롤러(없으면 null, 그리기용). */
    static synchronized FlowController peek(Canvas canvas) {
        return ALL.get(canvas);
    }

    /** Frame이 캔버스를 만든 뒤 부른다: 마우스·키·보임 변화를 듣는다. */
    public static void install(Canvas canvas) {
        FlowController c = of(canvas);
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                c.pressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                c.released(e);
            }
        });
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                c.dragged(e);
            }
        });
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && c.running()) {
                    c.stop();
                }
            }
        });
        canvas.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                c.visibilityChanged();
            }
        });
        SwingUtilities.invokeLater(() -> {
            java.awt.Window w = SwingUtilities.getWindowAncestor(canvas);
            if (w != null) {
                w.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowIconified(WindowEvent e) {
                        c.visibilityChanged();
                    }

                    @Override
                    public void windowDeiconified(WindowEvent e) {
                        c.visibilityChanged();
                    }

                    @Override
                    public void windowClosed(WindowEvent e) {
                        c.stop();
                    }
                });
            }
        });
        Project proj = canvas.getProject();
        if (proj != null) {
            proj.addProjectListener(c.projectListener);
            proj.getSimulator().addSimulatorListener(c.simListener);
        }
    }

    public boolean running() {
        return path != null;
    }

    public boolean timerRunning() {
        return timer.isRunning();
    }

    public SignalFlowPath path() {
        return path;
    }

    // ---- 시작 ----

    /** 부품 c(end ≥ 0이면 그 포트)에서 시작한다. */
    public void start(Component c, int end, boolean backward) {
        startComponent = c;
        startEnd = end;
        startWire = null;
        startClick = null;
        this.backward = backward;
        begin();
    }

    /** 선 w의 click 점에서 시작한다. */
    public void start(Wire w, Location click, boolean backward) {
        startComponent = null;
        startWire = w;
        startClick = click;
        this.backward = backward;
        begin();
    }

    private SignalFlowPath compute() {
        Circuit circuit = canvas.getCircuit();
        SignalFlowPath.Options o = new SignalFlowPath.Options();
        o.backward = backward;
        o.throughRegisters = FlowSettings.throughRegisters();
        if (FlowSettings.activePathOnly() && canvas.getProject() != null) {
            o.activeValues = ValueSource.of(canvas.getProject().getCircuitState());
        }
        if (startWire != null) {
            return SignalFlowPath.fromWire(circuit, startWire, startClick, o);
        }
        return SignalFlowPath.fromComponent(circuit, startComponent, startEnd, o);
    }

    private void begin() {
        stopTimerOnly();
        erase();
        path = compute();
        shownCircuit = canvas.getCircuit();
        box = FlowPainter.bounds(path, shownCircuit, zoom());
        listen(shownCircuit);
        elapsedMs = 0;
        lastTickNanos = System.nanoTime();
        paused = false;
        if (!FlowSettings.reduceMotion() && frozen == null) {
            timer.setDelay(1000 / FlowSettings.fps());
            timer.start();
        }
        visibilityChanged();
        repaintBox();
    }

    /** 설정을 바꾸면 흐르던 것을 새 설정으로 다시 시작한다(경로·속도·움직임). */
    public void settingsChanged() {
        if (path != null) {
            begin();
        }
    }

    // ---- 멈춤 ----

    public void stop() {
        if (path == null) {
            return;
        }
        stopTimerOnly();
        clickDelay.stop();
        recompute.stop();
        erase();
        path = null;
        box = null;
        unlisten();
    }

    private void stopTimerOnly() {
        timer.stop();
    }

    /** 멈추기 전 마지막 상자를 한 번 더 그려 흔적을 지운다. */
    private void erase() {
        if (box != null) {
            Rectangle old = box;
            box = null;
            repaintCircuitRect(old);
        }
    }

    private void listen(Circuit c) {
        unlisten();
        if (c != null) {
            c.addCircuitListener(edits);
            listening = c;
        }
    }

    private void unlisten() {
        if (listening != null) {
            listening.removeCircuitListener(edits);
            listening = null;
        }
    }

    private void circuitChanged(CircuitEvent e) {
        // 어떤 편집이든 멈춘다(모양이 바뀌면 경로가 틀린다). 이벤트 스레드에서 처리한다
        SwingUtilities.invokeLater(this::stop);
    }

    private void projectChanged(ProjectEvent e) {
        int a = e.getAction();
        if (a == ProjectEvent.ACTION_SET_CURRENT || a == ProjectEvent.ACTION_SET_FILE) {
            // 탭·서브회로 전환, 파일 닫기·바꾸기
            if (path != null && canvas.getCircuit() != shownCircuit) {
                stop();
            }
        } else if (a == ProjectEvent.ACTION_SET_STATE && path != null && FlowSettings.activePathOnly()) {
            valuesChanged();
        }
    }

    /** 값이 바뀌었을 때(클럭 틱) Active Path Only만 다시 계산한다. */
    public void valuesChanged() {
        // 클럭이 빠르면 계속 미루지 않고, 첫 변경부터 잠깐 뒤에 한 번 계산한다(디바운스)
        if (path != null && FlowSettings.activePathOnly() && !recompute.isRunning()) {
            recompute.start();
        }
    }

    private void recomputeActive() {
        if (path == null) {
            return;
        }
        Rectangle old = box;
        path = compute();
        box = FlowPainter.bounds(path, shownCircuit, zoom());
        if (old != null) {
            repaintCircuitRect(old);
        }
        repaintBox();
    }

    // ---- 일시정지 ----

    private void visibilityChanged() {
        boolean visible = canvas.isShowing();
        java.awt.Window w = SwingUtilities.getWindowAncestor(canvas);
        if (w instanceof java.awt.Frame && (((java.awt.Frame) w).getExtendedState() & java.awt.Frame.ICONIFIED) != 0) {
            visible = false;
        }
        if (path == null) {
            return;
        }
        if (!visible && timer.isRunning()) {
            paused = true;
            timer.stop();
        } else if (visible && paused && !FlowSettings.reduceMotion() && frozen == null) {
            paused = false;
            lastTickNanos = System.nanoTime();
            timer.start();
        }
    }

    // ---- 프레임 ----

    private void tick() {
        if (path == null) {
            timer.stop();
            return;
        }
        long now = System.nanoTime();
        elapsedMs += (now - lastTickNanos) / 1_000_000L;
        lastTickNanos = now;
        repaintBox();
    }

    /** 지금 시각(회로 단위): 화면 px/초 × 초 ÷ 배율. */
    public double time() {
        if (frozen != null) {
            return frozen;
        }
        return elapsedMs / 1000.0 * FlowSettings.speed().pxPerSecond / zoom();
    }

    /** 테스트·스크린샷: 시각 t에 멈춘 장면(null이면 다시 흐름). */
    public void freeze(Double t) {
        frozen = t;
        if (t != null) {
            timer.stop();
        } else if (path != null && !FlowSettings.reduceMotion()) {
            lastTickNanos = System.nanoTime();
            timer.start();
        }
        repaintBox();
    }

    private double zoom() {
        return canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
    }

    private void repaintBox() {
        // 상자는 배율에 따라 달라진다(끝점 칩은 화면 크기가 일정): 그릴 때마다 지금 배율로
        if (path != null && shownCircuit != null) {
            Rectangle now = FlowPainter.bounds(path, shownCircuit, zoom());
            if (now != null) {
                box = now;
            }
        }
        if (box != null) {
            repaintCircuitRect(box);
        }
    }

    private void repaintCircuitRect(Rectangle r) {
        double z = zoom();
        canvas.repaint((int) Math.floor(r.x * z) - 2, (int) Math.floor(r.y * z) - 2,
                (int) Math.ceil(r.width * z) + 4, (int) Math.ceil(r.height * z) + 4);
    }

    /** 다시 그리는 상자(테스트). */
    public Rectangle box() {
        return box == null ? null : new Rectangle(box);
    }

    // ---- 그리기(CanvasPainter) ----

    /** CanvasPainter: 영향 경로 다음에 부른다. */
    public static void paint(Canvas canvas, Graphics g0, Circuit circ) {
        FlowController c = peek(canvas);
        if (c == null || c.path == null || !(g0 instanceof Graphics2D)) {
            return;
        }
        List<Rectangle> chips = kr.ac.hallym.hcs.app.labels.LabelOverlay.textRects(canvas);
        c.obstacleHash = chips.hashCode();
        FlowPainter.paint((Graphics2D) g0, c.path, circ, c.time(), c.zoom(), FlowSettings.reduceMotion(),
                loc -> tunnelColor(canvas, circ, loc), chips);
    }

    /** 흐름을 그릴 때 피한 라벨 칩들(서명). */
    private int obstacleHash;

    /**
     * CanvasPainter: 라벨 칩을 그린 뒤. 라벨 칩은 흐름보다 나중에 자리를 정하므로(배율을 바꾼 첫 장면 등) 흐름이
     * 옛 칩 자리를 피했으면 한 번 더 그려 새 자리를 피하게 한다(멈춘 흐름도).
     */
    public static void afterLabels(Canvas canvas) {
        FlowController c = peek(canvas);
        if (c == null || c.path == null) {
            return;
        }
        int now = kr.ac.hallym.hcs.app.labels.LabelOverlay.textRects(canvas).hashCode();
        if (now != c.obstacleHash) {
            c.obstacleHash = now;
            SwingUtilities.invokeLater(c::repaintBox);
        }
    }

    /** 테스트: 마지막으로 그린 흐름이 지금 라벨 칩 자리를 피했는가. */
    boolean avoidsCurrentChips() {
        return obstacleHash == kr.ac.hallym.hcs.app.labels.LabelOverlay.textRects(canvas).hashCode();
    }

    /** 터널 색 팔레트(이름 해시 또는 학생이 고른 색). 없으면 null. */
    static Color tunnelColor(Canvas canvas, Circuit circ, Location at) {
        for (Component x : circ.getNonWires()) {
            if (x.getFactory().getName().equals("Tunnel") && x.getLocation().equals(at)) {
                String label = kr.ac.hallym.hcs.app.labels.TunnelColorStore.name(x);
                if (label == null || canvas.getProject() == null) {
                    return null;
                }
                return kr.ac.hallym.hcs.app.labels.TunnelColorStore.display(canvas.getProject().getLogisimFile(), circ,
                        label);
            }
        }
        return null;
    }

    // ---- 누름 ----

    private boolean clickTool() {
        Tool t = canvas.getProject() == null ? null : canvas.getProject().getTool();
        return t instanceof EditTool || t instanceof SelectTool;
    }

    private void pressed(MouseEvent e) {
        if (clickDelay.isRunning()) {
            clickDelay.stop(); // 두 번째 누름: 더블클릭은 원조 동작
            pressed = null;
            return;
        }
        pressed = SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1 && clickTool() ? e : null;
        dragged = false;
    }

    private void dragged(MouseEvent e) {
        // 좌표는 회로 좌표다: 끌기 판정은 화면 px로
        double z = zoom();
        if (pressed != null && (Math.abs(e.getX() - pressed.getX()) * z > DRAG_SLOP
                || Math.abs(e.getY() - pressed.getY()) * z > DRAG_SLOP)) {
            dragged = true;
            clickDelay.stop();
        }
    }

    private MouseEvent released;

    private void released(MouseEvent e) {
        if (pressed == null || dragged || !SwingUtilities.isLeftMouseButton(e)) {
            pressed = null;
            return;
        }
        released = e;
        pressed = null;
        clickDelay.restart();
    }

    /** 150ms 동안 다른 누름·끌기가 없었다. */
    private void fireClick() {
        MouseEvent e = released;
        released = null;
        if (e == null) {
            return;
        }
        // 원조 캔버스는 마우스 좌표를 회로 좌표로 바꿔 넘긴다(Canvas.repairMouseEvent)
        Location at = Location.create(e.getX(), e.getY());
        clickAt(at, (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0);
    }

    /** 사용자가 at을 눌렀다(150ms 뒤): 빈 곳이면 멈추고, 대상이면 그 대상으로 새로 시작한다. */
    void clickAt(Location at, boolean shift) {
        Target t = target(canvas.getCircuit(), at, canvas.getGraphics());
        if (t == null) {
            stop(); // 빈 곳
            return;
        }
        if (!FlowSettings.onClick()) {
            return;
        }
        if (t.wire != null) {
            start(t.wire, at, shift);
        } else {
            start(t.component, t.end, shift);
        }
    }

    /** 누른 대상: 부품(출력 포트 가까이면 그 포트만) 또는 선. */
    static final class Target {
        final Component component;
        final int end;
        final Wire wire;

        Target(Component component, int end, Wire wire) {
            this.component = component;
            this.end = end;
            this.wire = wire;
        }
    }

    static Target target(Circuit circuit, Location at, Graphics g) {
        if (circuit == null) {
            return null;
        }
        Collection<Component> here = g == null ? circuit.getAllContaining(at) : circuit.getAllContaining(at, g);
        Component comp = null;
        for (Component c : here) {
            if (!(c instanceof Wire)) {
                comp = c;
                break;
            }
        }
        if (comp == null) {
            for (Component c : here) {
                if (c instanceof Wire) {
                    return new Target(null, -1, (Wire) c);
                }
            }
            // 포트 바로 위(부품 경계 밖)
            for (Component c : circuit.getNonWires()) {
                int end = outputNear(c, at);
                if (end >= 0) {
                    return new Target(c, end, null);
                }
            }
            return null;
        }
        return new Target(comp, outputNear(comp, at), null);
    }

    /** at에서 5px 안의 출력 포트. 없으면 -1(몸통: 모든 출력). */
    static int outputNear(Component c, Location at) {
        for (int i = 0; i < c.getEnds().size(); i++) {
            Location p = c.getEnds().get(i).getLocation();
            if (c.getEnds().get(i).isOutput() && Math.abs(p.getX() - at.getX()) <= 5
                    && Math.abs(p.getY() - at.getY()) <= 5) {
                return i;
            }
        }
        return -1;
    }
}
