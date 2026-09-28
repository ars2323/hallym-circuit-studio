/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.model;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.hex.HexModel;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.FontUtil;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.props.QuickAttrs;
import kr.ac.hallym.hcs.engine.doc.Doc;

/**
 * 속성 표(model.attributes, N-10, D-157): 원조 속성 표가 보이는 것 그대로를 규약 JSON으로.
 * <ul>
 * <li>고른 것(원조 {@code AttrTableSelectionModel}): 선이 아닌 것이 있으면 선은 빼고, 모두가 가진 속성만 첫 부품의
 * 차례로, 값이 다르면 빈칸(원조 {@code SelectionAttributes.computeAttributes}). 고른 것이 없으면 회로 속성
 * ({@code AttrTableCircuitModel}).</li>
 * <li>든 도구({@code AttrTableToolModel}): 도구의 속성(다음에 놓을 부품).</li>
 * </ul>
 * 줄마다 이름(원조 표시 이름), .circ 글자, 원조 표가 보이는 글자, 편집기 종류와 선택지(원조 편집기가 목록 상자면 그
 * 목록: 비트 폭, 방향, 예·아니오, 부품이 정한 선택지)를 싣는다. 값을 바꾸는 것은 edit.setAttr·setToolAttr·
 * setCircuitAttr이고 읽는 규칙은 원조 {@code Attribute.parse} 하나다. 제목은 원조 글("Selection: AND Gate").
 */
public final class AttrTable {
    private static final LocaleManager GUI = new LocaleManager("resources/logisim", "gui");

    private AttrTable() {
    }

    /** 고른 부품·선(없으면 회로 속성). */
    public static JsonObject selection(Doc d, Circuit c, Collection<Component> chosen) {
        List<Component> comps = attributed(chosen);
        if (comps.isEmpty()) {
            return circuit(d, c);
        }
        JsonObject o = new JsonObject();
        o.addProperty("target", "selection");
        o.addProperty("circuitId", d.ids().of(c));
        o.addProperty("title", selectionTitle(chosen));
        o.addProperty("editable", editable(d, c));
        LinkedHashMap<Attribute<Object>, Object> attrs = common(comps);
        JsonArray rows = new JsonArray();
        for (Map.Entry<Attribute<Object>, Object> e : attrs.entrySet()) {
            rows.add(row(e.getKey(), e.getValue(), readOnly(comps, e.getKey()), e.getValue() == null && comps.size() > 1));
        }
        o.add("rows", rows);
        JsonObject quick = quick(d, chosen);
        if (quick != null) {
            o.add("quick", quick);
        }
        return o;
    }

    /** 회로 속성(원조 AttrTableCircuitModel: 이름, 공유 라벨, 방향, 글꼴). */
    public static JsonObject circuit(Doc d, Circuit c) {
        JsonObject o = new JsonObject();
        o.addProperty("target", "circuit");
        o.addProperty("circuitId", d.ids().of(c));
        o.addProperty("title", StringUtil.format(GUI.get("circuitAttrTitle"), c.getName()));
        o.addProperty("editable", editable(d, c));
        o.add("rows", rows(c.getStaticAttributes()));
        return o;
    }

    /** 든 도구(원조 AttrTableToolModel). 속성이 없는 도구면 rows가 빈다. */
    public static JsonObject tool(Doc d, String lib, Tool t) {
        JsonObject o = new JsonObject();
        o.addProperty("target", "tool");
        o.addProperty("lib", lib);
        o.addProperty("name", t.getName());
        o.addProperty("title", StringUtil.format(GUI.get("toolAttrTitle"), t.getDisplayName()));
        o.addProperty("editable", !d.isReadOnly());
        AttributeSet as = t.getAttributeSet();
        o.add("rows", as == null ? new JsonArray() : rows(as));
        return o;
    }

    public static boolean editable(Doc d, Circuit c) {
        return !d.isReadOnly() && d.file().contains(c);
    }

    private static JsonArray rows(AttributeSet as) {
        JsonArray rows = new JsonArray();
        for (Attribute<?> a : as.getAttributes()) {
            @SuppressWarnings("unchecked")
            Attribute<Object> attr = (Attribute<Object>) a;
            rows.add(row(attr, as.getValue(attr), as.isReadOnly(attr), false));
        }
        return rows;
    }

    /** 원조 SelectionAttributes.createSet: 선이 아닌 것이 하나라도 있으면 선은 뺀다. */
    static List<Component> attributed(Collection<Component> chosen) {
        boolean wiresOnly = true;
        for (Component x : chosen) {
            if (!(x instanceof Wire)) {
                wiresOnly = false;
                break;
            }
        }
        List<Component> ret = new ArrayList<>();
        for (Component x : chosen) {
            if (wiresOnly || !(x instanceof Wire)) {
                ret.add(x);
            }
        }
        // 원조는 선택(해시 집합)의 차례로 첫 부품을 정해 그 속성 차례를 쓴다: 같은 선택이면 같은 표가 되게 위→아래,
        // 왼쪽→오른쪽 차례로 정한다(정함, D-157)
        ret.sort((a, b) -> a.getLocation().getY() != b.getLocation().getY()
                ? a.getLocation().getY() - b.getLocation().getY()
                : a.getLocation().getX() != b.getLocation().getX() ? a.getLocation().getX() - b.getLocation().getX()
                : a.getFactory().getName().compareTo(b.getFactory().getName()));
        return ret;
    }

    /** 원조 SelectionAttributes.computeAttributes: 모두가 가진 속성만(첫 부품 차례), 값이 다르면 null. */
    static LinkedHashMap<Attribute<Object>, Object> common(List<Component> comps) {
        LinkedHashMap<Attribute<Object>, Object> map = new LinkedHashMap<>();
        Iterator<Component> it = comps.iterator();
        if (!it.hasNext()) {
            return map;
        }
        AttributeSet first = it.next().getAttributeSet();
        for (Attribute<?> a : first.getAttributes()) {
            @SuppressWarnings("unchecked")
            Attribute<Object> attr = (Attribute<Object>) a;
            map.put(attr, first.getValue(attr));
        }
        while (it.hasNext()) {
            AttributeSet next = it.next().getAttributeSet();
            Iterator<Attribute<Object>> ai = map.keySet().iterator();
            while (ai.hasNext()) {
                Attribute<Object> attr = ai.next();
                if (next.containsAttribute(attr)) {
                    Object v = map.get(attr);
                    if (v != null && !v.equals(next.getValue(attr))) {
                        map.put(attr, null);
                    }
                } else {
                    ai.remove();
                }
            }
        }
        return map;
    }

    private static boolean readOnly(List<Component> comps, Attribute<?> a) {
        for (Component x : comps) {
            if (x.getAttributeSet().isReadOnly(a)) {
                return true;
            }
        }
        return false;
    }

    /** 원조 AttrTableSelectionModel.getTitle. */
    static String selectionTitle(Collection<Component> chosen) {
        ComponentFactory wireFactory = null;
        ComponentFactory factory = null;
        int factoryCount = 0;
        int totalCount = 0;
        boolean various = false;
        for (Component comp : chosen) {
            ComponentFactory f = comp.getFactory();
            if (f == factory) {
                factoryCount++;
            } else if (comp instanceof Wire) {
                wireFactory = f;
                if (factory == null) {
                    factoryCount++;
                }
            } else if (factory == null) {
                factory = f;
                factoryCount = 1;
            } else {
                various = true;
            }
            if (!(comp instanceof Wire)) {
                totalCount++;
            }
        }
        if (factory == null) {
            factory = wireFactory;
        }
        if (various) {
            return StringUtil.format(GUI.get("selectionVarious"), "" + totalCount);
        } else if (factoryCount <= 1) {
            return StringUtil.format(GUI.get("selectionOne"), factory.getDisplayName());
        } else {
            return StringUtil.format(GUI.get("selectionMultiple"), factory.getDisplayName(), "" + factoryCount);
        }
    }

    /**
     * 한 줄: {attr, display, value(.circ 글자, 다르면 null), text(원조 표의 글), type, options?, radix?, min?, max?,
     * readOnly, mixed}. type은 편집기: option(목록), number, text, font, color, contents(ROM 내용: 16진 편집기).
     */
    static JsonObject row(Attribute<Object> a, Object v, boolean readOnly, boolean mixed) {
        JsonObject o = new JsonObject();
        o.addProperty("attr", a.getName());
        o.addProperty("display", a.getDisplayName());
        String value = null;
        String text = "";
        if (v != null) {
            try {
                value = a.toStandardString(v);
                text = a.toDisplayString(v);
            } catch (RuntimeException e) {
                text = "???"; // 원조 표도 이렇게 보인다(AttributeSetTableModel.AttrRow.getValue)
            }
        }
        o.addProperty("value", value);
        o.addProperty("text", text);
        o.addProperty("readOnly", readOnly);
        o.addProperty("mixed", mixed);
        if (v instanceof HexModel) {
            o.addProperty("type", "contents");
            o.remove("value"); // 내용 전체는 표에 싣지 않는다(16진 편집기가 mem.read로 읽는다)
            o.add("value", com.google.gson.JsonNull.INSTANCE);
            return o;
        }
        if (v instanceof Font) {
            o.addProperty("type", "font");
            JsonArray styles = new JsonArray();
            for (int s : new int[] {Font.PLAIN, Font.ITALIC, Font.BOLD, Font.BOLD | Font.ITALIC}) {
                JsonObject so = new JsonObject();
                so.addProperty("value", FontUtil.toStyleStandardString(s));
                so.addProperty("display", FontUtil.toStyleDisplayString(s));
                styles.add(so);
            }
            o.add("styles", styles);
            JsonArray families = new JsonArray();
            for (String f : FAMILIES) {
                families.add(f);
            }
            o.add("families", families);
            return o;
        }
        if (v instanceof Color) {
            o.addProperty("type", "color");
            return o;
        }
        JsonArray opts = optionsJson(a, v);
        if (opts != null) {
            o.addProperty("type", "option");
            o.add("options", opts);
            return o;
        }
        if (v instanceof Integer || numeric(a)) {
            o.addProperty("type", "number");
            o.addProperty("radix", a.getClass().getSimpleName().startsWith("Hex") ? 16 : 10);
            Integer min = intField(a, "start");
            Integer max = intField(a, "end");
            if (min != null && max != null) {
                o.addProperty("min", min);
                o.addProperty("max", max);
            }
            return o;
        }
        o.addProperty("type", "text");
        return o;
    }

    /**
     * 원조 목록 상자의 선택지를 .circ 글자와 보이는 글자로. 목록의 항목이 값 자체가 아닌 속성(Splitter의 bitN: 항목은
     * 보기 객체, 값은 그 차례)은 원조 표처럼 차례를 값으로 쓴다. 어느 쪽으로도 읽을 수 없으면 null(글 칸).
     */
    public static JsonArray optionsJson(Attribute<Object> a, Object v) {
        List<Object> options = comboOptions(a, v);
        if (options == null) {
            return null;
        }
        JsonArray opts = new JsonArray();
        for (int i = 0; i < options.size(); i++) {
            Object x = options.get(i);
            JsonObject oo = new JsonObject();
            try {
                oo.addProperty("value", a.toStandardString(x));
                oo.addProperty("display", a.toDisplayString(x));
            } catch (RuntimeException notAValue) {
                try {
                    Object byIndex = Integer.valueOf(i);
                    oo.addProperty("value", a.toStandardString(byIndex));
                    oo.addProperty("display", x.toString());
                } catch (RuntimeException e) {
                    return null;
                }
            }
            opts.add(oo);
        }
        return opts;
    }

    /** 글꼴 편집기가 고르게 하는 글꼴(원조 JFontChooser의 논리 글꼴과 .circ에 흔한 이름). */
    static final String[] FAMILIES = {"SansSerif", "Serif", "Monospaced", "Dialog", "DialogInput"};

    private static boolean numeric(Attribute<?> a) {
        String n = a.getClass().getSimpleName();
        return n.equals("IntegerAttribute") || n.equals("HexIntegerAttribute") || n.equals("IntegerRangeAttribute")
                || n.equals("DoubleAttribute");
    }

    /**
     * 원조 편집기가 목록 상자면 그 선택지(원조 표가 펼치는 목록 그대로). 창이 필요한 편집기(ROM 내용, 글꼴, 색)는
     * 앞에서 걸렀다. 목록이 아니면 null.
     */
    public static List<Object> comboOptions(Attribute<Object> a, Object v) {
        java.awt.Component editor;
        try {
            editor = a.getCellEditor(null, v);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(editor instanceof JComboBox)) {
            return null;
        }
        JComboBox<?> combo = (JComboBox<?>) editor;
        List<Object> ret = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object x = combo.getItemAt(i);
            if (x != null) {
                ret.add(x);
            }
        }
        return ret;
    }

    /** 원조 IntegerRangeAttribute의 범위(읽기만: 원조 parse가 쓰는 같은 값, 틀린 값의 문장에 보인다). */
    private static Integer intField(Attribute<?> a, String name) {
        for (Class<?> k = a.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                java.lang.reflect.Field f = k.getDeclaredField(name);
                if (f.getType() != int.class) {
                    return null;
                }
                f.setAccessible(true);
                return f.getInt(a);
            } catch (NoSuchFieldException e) {
                continue;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 원조 숨은 키(v1 QuickAttrs.hints): 부품의 원조 KeyConfigurator에 숫자 키(수정 키 없음, Alt)를 넣어 보고 바뀌는
     * 속성을 알아낸다. 한 키가 둘 이상을 바꾸면(Splitter의 Alt+숫자: 들어오는 폭과 팔 수) 모두를 속성 집합의 차례로
     * 적는다(v1은 해시 차례의 하나만 적어 실행마다 달랐다). display는 그 이름들을 ", "로 이은 것.
     */
    static JsonArray hints(Component c) {
        JsonArray out = new JsonArray();
        AttributeSet as = c.getAttributeSet();
        Object feature = c.getFactory().getFeature(com.cburch.logisim.tools.key.KeyConfigurator.class, as);
        if (!(feature instanceof com.cburch.logisim.tools.key.KeyConfigurator)) {
            return out;
        }
        com.cburch.logisim.tools.key.KeyConfigurator base = (com.cburch.logisim.tools.key.KeyConfigurator) feature;
        int[] mods = {0, java.awt.event.InputEvent.ALT_DOWN_MASK};
        String[] labels = {"0–9", "Alt+0–9"};
        javax.swing.JPanel source = new javax.swing.JPanel();
        for (int m = 0; m < mods.length; m++) {
            for (char digit = '1'; digit <= '9'; digit++) {
                com.cburch.logisim.tools.key.KeyConfigurator k = base.clone();
                java.awt.event.KeyEvent key = new java.awt.event.KeyEvent(source, java.awt.event.KeyEvent.KEY_TYPED, 0L,
                        mods[m], java.awt.event.KeyEvent.VK_UNDEFINED, digit);
                com.cburch.logisim.tools.key.KeyConfigurationResult r = k.keyEventReceived(
                        new com.cburch.logisim.tools.key.KeyConfigurationEvent(
                                com.cburch.logisim.tools.key.KeyConfigurationEvent.KEY_TYPED, as, key, c));
                if (r == null || r.getAttributeValues().isEmpty()) {
                    continue;
                }
                List<String> names = new ArrayList<>();
                List<String> displays = new ArrayList<>();
                for (Attribute<?> a : as.getAttributes()) {
                    if (r.getAttributeValues().containsKey(a)) {
                        names.add(a.getName());
                        displays.add(a.getDisplayName());
                    }
                }
                if (names.isEmpty()) {
                    continue;
                }
                JsonObject ho = new JsonObject();
                ho.addProperty("keys", labels[m]);
                ho.addProperty("attr", names.get(0));
                if (names.size() > 1) {
                    JsonArray all = new JsonArray();
                    names.forEach(all::add);
                    ho.add("attrs", all);
                }
                ho.addProperty("display", String.join(", ", displays));
                out.add(ho);
                break;
            }
        }
        return out;
    }

    /**
     * 빠른 속성 창(v1 QuickBar, I-103·I-104): 고른 선 아닌 부품이 모두 같은 종류일 때 그 종류의 자주 바꾸는 속성
     * (부품 종류 등록표, 최대 5), 원조 숨은 키, R·F2, 기본 모양 서브회로의 Auto Appearance. 아니면 null.
     */
    static JsonObject quick(Doc d, Collection<Component> chosen) {
        List<Component> targets = QuickAttrs.targets(chosen);
        if (targets.isEmpty()) {
            return null;
        }
        Component first = targets.get(0);
        JsonObject q = new JsonObject();
        JsonArray attrs = new JsonArray();
        for (QuickAttrs.Entry e : QuickAttrs.entries(first)) {
            attrs.add(e.name());
        }
        q.add("attrs", attrs);
        JsonArray hints = new JsonArray();
        try {
            hints = hints(first);
        } catch (RuntimeException e) {
            // 키 설정기가 창을 원하면 숨은 키 줄만 없다
        }
        q.add("hints", hints);
        q.addProperty("rotate", first.getAttributeSet().getAttribute("facing") != null);
        q.addProperty("label", QuickAttrs.labelAttr(first) != null);
        q.addProperty("count", targets.size());
        if (first.getFactory() instanceof SubcircuitFactory) {
            Circuit sub = ((SubcircuitFactory) first.getFactory()).getSubcircuit();
            if (sub.getAppearance().isDefaultAppearance() && d.file().getCircuits().contains(sub)) {
                q.addProperty("autoAppearance", d.ids().of(sub));
            }
        }
        return q;
    }
}
