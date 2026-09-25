/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.appear;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.draw.shapes.Text;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.appear.AppearanceAnchor;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.circuit.appear.CircuitAppearance;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Names;

/**
 * 서브회로 모양 자동 만들기(검토 2차 C, PLAN.md 11.10 J "모양 자동 정렬"). 원조 2.7.1의 사용자 모양(Project › Edit
 * Circuit Appearance로 그리는 것)과 같은 표준 도형만 쓴다: 사각형, 글자, 포트, 기준점. 그래서 원조 2.7.1에서도 같은
 * 모양·같은 포트 자리로 열린다.
 * <ul>
 * <li>상자: 포트 이름(핀 라벨)과 회로 이름이 들어가는 폭. 포트 간격 20px, 글자 SansSerif 10px.</li>
 * <li>포트 자리: 원조 기본 모양처럼 핀 방향으로 변을 정하고(동쪽을 보는 입력 핀은 서쪽 변), 한 변 안의 순서는 지금
 * 모양의 순서를 지킨다(기본 모양이면 원조 기본 순서).</li>
 * <li>기준점: 원조 기본 모양과 같은 규칙(동쪽 변 첫 포트, 없으면 북·서·남 순).</li>
 * </ul>
 * 포트 자리가 바뀌면 이 회로를 쓰는 인스턴스의 연결이 끊어질 수 있다. {@link #impact}로 미리 센다(11.10 포트 변경 영향).
 */
public final class AutoAppearance {
    /** 포트 간격. */
    static final int SPACING = 20;
    /** 변 안쪽 글자 여백. */
    static final int PAD = 5;
    /** 도형을 두는 자리(원조 기본 모양과 같은 50). */
    static final int ORIGIN = 50;
    /** 원조가 어느 PC에나 있는 논리 글꼴. */
    static final Font PORT_FONT = new Font("SansSerif", Font.PLAIN, 10);
    static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 10);

    private AutoAppearance() {
    }

    /** 회로의 핀(인스턴스)을 변별로: 원조 기본 모양처럼 핀이 보는 방향의 반대 변. */
    static Map<Direction, List<Instance>> sides(Circuit circuit) {
        CircuitAppearance appear = circuit.getAppearance();
        Map<Instance, Location> now = new HashMap<>();
        for (Map.Entry<Location, Instance> e : appear.getPortOffsets(Direction.EAST).entrySet()) {
            now.put(e.getValue(), e.getKey());
        }
        Map<Direction, List<Instance>> ret = new java.util.LinkedHashMap<>();
        for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            ret.put(d, new ArrayList<>());
        }
        for (Instance pin : appear.getCircuitPins().getPins()) {
            ret.get(pin.getAttributeValue(StdAttr.FACING).reverse()).add(pin);
        }
        for (Map.Entry<Direction, List<Instance>> e : ret.entrySet()) {
            boolean byX = e.getKey() == Direction.NORTH || e.getKey() == Direction.SOUTH;
            // 지금 모양의 순서(없는 핀은 회로 안 위치 순서로 뒤에)
            e.getValue().sort(Comparator.comparing((Instance p) -> now.containsKey(p) ? 0 : 1)
                    .thenComparingInt(p -> now.containsKey(p) ? (byX ? now.get(p).getX() : now.get(p).getY()) : 0)
                    .thenComparingInt(p -> byX ? p.getLocation().getX() : p.getLocation().getY())
                    .thenComparing(Instance::getLocation));
        }
        return ret;
    }

    static String portName(Instance pin) {
        String s = pin.getAttributeValue(StdAttr.LABEL);
        return s == null ? "" : s.trim();
    }

    private static FontMetrics metrics(Font f) {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            return g.getFontMetrics(f);
        } finally {
            g.dispose();
        }
    }

    private static int widest(List<Instance> pins, FontMetrics fm) {
        int w = 0;
        for (Instance p : pins) {
            w = Math.max(w, fm.stringWidth(portName(p)));
        }
        return w;
    }

    private static int up10(int v) {
        return (v + 9) / 10 * 10;
    }

    /** 새 모양의 도형들(아래부터 그리는 순서). */
    public static List<CanvasObject> build(Circuit circuit) {
        Map<Direction, List<Instance>> side = sides(circuit);
        List<Instance> west = side.get(Direction.WEST);
        List<Instance> east = side.get(Direction.EAST);
        List<Instance> north = side.get(Direction.NORTH);
        List<Instance> south = side.get(Direction.SOUTH);
        FontMetrics pf = metrics(PORT_FONT);
        FontMetrics tf = metrics(TITLE_FONT);
        String title = circuit.getName();

        int titleH = 20;
        int northH = north.isEmpty() ? 0 : 20;
        int southH = south.isEmpty() ? 0 : 20;
        int rows = Math.max(1, Math.max(west.size(), east.size()));
        int top = titleH + northH; // 첫 옆 포트 위
        int height = top + SPACING * rows + southH;
        int nsSpacing = Math.max(SPACING, up10(Math.max(widest(north, pf), widest(south, pf)) + 2 * PAD));
        int sideW = widest(west, pf) + widest(east, pf) + 2 * PAD + 20;
        int titleW = tf.stringWidth(title) + 2 * PAD + 10;
        int nsW = nsSpacing * Math.max(north.size(), south.size()) + 10;
        int width = up10(Math.max(Math.max(sideW, titleW), Math.max(nsW, 40)));

        int ox = ORIGIN;
        int oy = ORIGIN;
        List<CanvasObject> shapes = new ArrayList<>();
        Rectangle rect = new Rectangle(ox, oy, width, height);
        rect.setValue(DrawAttr.STROKE_WIDTH, Integer.valueOf(2));
        shapes.add(rect);
        shapes.add(text(ox + width / 2, oy + (titleH + northH) - 6, title, TITLE_FONT, DrawAttr.ALIGN_CENTER));

        List<CanvasObject> ports = new ArrayList<>();
        Location first = null;
        Location firstEast = null;
        Location firstNorth = null;
        Location firstWest = null;
        Location firstSouth = null;
        for (int i = 0; i < west.size(); i++) {
            Location at = Location.create(ox, oy + top + SPACING / 2 + SPACING * i);
            firstWest = firstWest == null ? at : firstWest;
            ports.add(new AppearancePort(at, west.get(i)));
            shapes.add(text(at.getX() + PAD, at.getY() + 4, portName(west.get(i)), PORT_FONT,
                    DrawAttr.ALIGN_LEFT));
        }
        for (int i = 0; i < east.size(); i++) {
            Location at = Location.create(ox + width, oy + top + SPACING / 2 + SPACING * i);
            firstEast = firstEast == null ? at : firstEast;
            ports.add(new AppearancePort(at, east.get(i)));
            shapes.add(text(at.getX() - PAD, at.getY() + 4, portName(east.get(i)), PORT_FONT,
                    DrawAttr.ALIGN_RIGHT));
        }
        int nsCount = Math.max(north.size(), south.size());
        int nsLeft = ox + up10((width - nsSpacing * (nsCount - 1)) / 2 - 5);
        for (int i = 0; i < north.size(); i++) {
            Location at = Location.create(nsLeft + nsSpacing * i, oy);
            firstNorth = firstNorth == null ? at : firstNorth;
            ports.add(new AppearancePort(at, north.get(i)));
            shapes.add(text(at.getX(), oy + 12, portName(north.get(i)), PORT_FONT, DrawAttr.ALIGN_CENTER));
        }
        for (int i = 0; i < south.size(); i++) {
            Location at = Location.create(nsLeft + nsSpacing * i, oy + height);
            firstSouth = firstSouth == null ? at : firstSouth;
            ports.add(new AppearancePort(at, south.get(i)));
            shapes.add(text(at.getX(), oy + height - 5, portName(south.get(i)), PORT_FONT,
                    DrawAttr.ALIGN_CENTER));
        }
        // 원조 기본 모양과 같은 기준점 규칙
        first = firstEast != null ? firstEast : firstNorth != null ? firstNorth : firstWest != null ? firstWest
                : firstSouth != null ? firstSouth : Location.create(ox, oy);
        shapes.addAll(ports);
        shapes.add(new AppearanceAnchor(first));
        return shapes;
    }

    private static Text text(int x, int y, String s, Font font, Object align) {
        Text t = new Text(x, y, s);
        t.setValue(DrawAttr.FONT, font);
        t.setValue(DrawAttr.ALIGNMENT, (com.cburch.logisim.data.AttributeOption) align);
        return t;
    }

    /** 도형들의 포트 자리(기준점 기준, facing 방향 인스턴스). CircuitAppearance.getPortOffsets와 같은 계산. */
    static Map<Instance, Location> offsets(Collection<? extends CanvasObject> shapes, Direction facing) {
        Location anchor = null;
        Direction defaultFacing = Direction.EAST;
        for (CanvasObject o : shapes) {
            if (o instanceof AppearanceAnchor) {
                anchor = ((AppearanceAnchor) o).getLocation();
                defaultFacing = ((AppearanceAnchor) o).getFacing();
            }
        }
        Map<Instance, Location> ret = new HashMap<>();
        for (CanvasObject o : shapes) {
            if (o instanceof AppearancePort) {
                Location loc = ((AppearancePort) o).getLocation();
                if (anchor != null) {
                    loc = loc.translate(-anchor.getX(), -anchor.getY());
                }
                if (facing != defaultFacing) {
                    loc = loc.rotate(defaultFacing, facing, 0, 0);
                }
                ret.put(((AppearancePort) o).getPin(), loc);
            }
        }
        return ret;
    }

    /** 포트 자리가 바뀌어 끊어질 연결. */
    public static final class Impact {
        /** 연결이 끊어질 인스턴스 수. */
        public final int instances;
        /** 끊어질 포트 연결 수(인스턴스 포트마다 하나). */
        public final int connections;
        /** 끊어질 인스턴스 경로(예: {@code main › regfile #1}). */
        public final List<String> where;

        Impact(int instances, int connections, List<String> where) {
            this.instances = instances;
            this.connections = connections;
            this.where = where;
        }
    }

    /**
     * 새 도형으로 바꾸면 끊어질 연결을 센다. 이 회로를 쓰는 모든 인스턴스에서, 자리가 바뀌는 포트의 옛 자리에 다른
     * 부품이나 선이 닿아 있으면 하나로 센다.
     */
    public static Impact impact(LogisimFile file, Circuit circuit, List<CanvasObject> shapes) {
        int instances = 0;
        int connections = 0;
        List<String> where = new ArrayList<>();
        for (Circuit parent : file.getCircuits()) {
            for (Component c : parent.getNonWires()) {
                if (!(c.getFactory() instanceof SubcircuitFactory)
                        || ((SubcircuitFactory) c.getFactory()).getSubcircuit() != circuit) {
                    continue;
                }
                Direction facing = c.getAttributeSet().getValue(StdAttr.FACING);
                Map<Instance, Location> after = offsets(shapes, facing);
                int broken = 0;
                for (Map.Entry<Location, Instance> e : circuit.getAppearance().getPortOffsets(facing).entrySet()) {
                    Location off = e.getKey();
                    if (off.equals(after.get(e.getValue()))) {
                        continue;
                    }
                    Location at = c.getLocation().translate(off.getX(), off.getY());
                    for (Component o : parent.getComponents(at)) {
                        if (o != c) {
                            broken++;
                            break;
                        }
                    }
                }
                if (broken > 0) {
                    instances++;
                    connections += broken;
                    where.add(Names.path(parent.getName(), Names.name(parent, c)));
                }
            }
        }
        return new Impact(instances, connections, where);
    }

    /** 되돌릴 수 있는 동작: 원조 "Revert To Default Appearance"와 같은 방식으로 도형을 바꾼다. */
    public static Action action(Circuit circuit, List<CanvasObject> shapes) {
        return new Action() {
            private List<CanvasObject> old;
            private boolean wasDefault;

            @Override
            public String getName() {
                return Messages.get("autoAppearance.action");
            }

            @Override
            public void doIt(Project proj) {
                CircuitAppearance appear = circuit.getAppearance();
                wasDefault = appear.isDefaultAppearance();
                old = new ArrayList<>(appear.getObjectsFromBottom());
                inUsers(() -> {
                    appear.setDefaultAppearance(false); // 먼저 끈다: 켜진 채면 핀이 바뀔 때 기본 모양으로 돌아간다
                    appear.setObjectsForce(shapes);
                });
            }

            @Override
            public void undo(Project proj) {
                CircuitAppearance appear = circuit.getAppearance();
                inUsers(() -> {
                    appear.setObjectsForce(old);
                    appear.setDefaultAppearance(wasDefault);
                });
            }

            /**
             * 모양이 바뀌면 이 회로를 쓰는 회로의 인스턴스 포트가 바뀐다. 원조 모양 편집기(CanvasActionAdapter)처럼 그
             * 회로들을 잠근 거래 안에서 바꾼다. 거래 없이 바꾸면 인스턴스가 있을 때 "ends changed outside transaction".
             */
            private void inUsers(Runnable r) {
                new com.cburch.logisim.circuit.CircuitTransaction() {
                    @Override
                    protected Map<Circuit, Integer> getAccessedCircuits() {
                        Map<Circuit, Integer> m = new java.util.HashMap<>();
                        for (Circuit sup : circuit.getCircuitsUsingThis()) {
                            m.put(sup, READ_WRITE);
                        }
                        return m;
                    }

                    @Override
                    protected void run(com.cburch.logisim.circuit.CircuitMutator mutator) {
                        r.run();
                    }
                }.execute();
            }
        };
    }

    /**
     * 메뉴 "Auto Appearance": 새 모양을 만들고, 끊어질 연결이 있으면 먼저 알린 뒤(11.10 포트 변경 영향) 되돌릴 수 있게
     * 바꾼다.
     */
    public static void run(Project proj, Circuit circuit, java.awt.Component parent) {
        List<CanvasObject> shapes = build(circuit);
        Impact impact = impact(proj.getLogisimFile(), circuit, shapes);
        if (impact.connections > 0) {
            String list = String.join("\n", impact.where.subList(0, Math.min(8, impact.where.size())));
            Object[] options = {Messages.get("autoAppearance.apply"), Messages.get("autoAppearance.cancel")};
            int r = javax.swing.JOptionPane.showOptionDialog(parent,
                    Messages.get("autoAppearance.impact", impact.instances, impact.connections) + "\n\n" + list,
                    Messages.get("autoAppearance.title"), javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            if (r != 0) {
                return;
            }
        }
        proj.doAction(action(circuit, shapes));
    }
}
