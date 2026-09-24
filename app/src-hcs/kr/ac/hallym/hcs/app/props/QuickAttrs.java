/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.props;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.key.KeyConfigurationEvent;
import com.cburch.logisim.tools.key.KeyConfigurationResult;
import com.cburch.logisim.tools.key.KeyConfigurator;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringGetter;

import kr.ac.hallym.hcs.app.model.Kinds;

/**
 * 빠른 속성 창의 모델(#74, PLAN.md 11.4). 어떤 속성을 보일지는 부품 종류 등록표({@link Kinds.Kind#quickAttrs()})
 * 하나에서 읽고, 선택지는 원조 속성 편집기에서 읽는다. 바꾸기는 원조 속성 표가 만드는 것과 같은
 * {@link SetAttributeAction}이다. 원조 2.7.1의 숨은 단축키(숫자 키, Alt+숫자)는 부품의 원조 {@link KeyConfigurator}에
 * 키를 넣어 보고 알아낸다. GUI 없이 테스트한다.
 */
public final class QuickAttrs {
    /** 빠른 속성 창에 보일 최대 개수. */
    public static final int MAX = 5;

    /** 속성 한 줄. options가 비어 있으면 글자로 넣는 속성이다. */
    public static final class Entry {
        public final Attribute<Object> attr;
        public final String value;
        /** [원조 저장 값, 원조 표시 이름]. */
        public final List<String[]> options;

        Entry(Attribute<Object> attr, String value, List<String[]> options) {
            this.attr = attr;
            this.value = value;
            this.options = Collections.unmodifiableList(options);
        }

        public String name() {
            return attr.getName();
        }
    }

    /** 숨은 단축키 하나: 키 설명(예: "0–9", "Alt+0–9")과 그 키가 바꾸는 속성. */
    public static final class Hint {
        public final String keys;
        public final Attribute<?> attr;

        Hint(String keys, Attribute<?> attr) {
            this.keys = keys;
            this.attr = attr;
        }
    }

    /** 원조 속성 표가 쓰는 되돌리기 이름("선택 항목 속성 바꾸기"). */
    private static final LocaleManager GUI = new LocaleManager("resources/logisim", "gui");
    private static final StringGetter ACTION_NAME = GUI.getter("selectionAttributeAction");

    private QuickAttrs() {
    }

    /** 빠른 속성 창에 보일 속성(등록표 순서, 이 부품에 있는 것만, 최대 {@link #MAX}개). */
    public static List<Entry> entries(Component c) {
        List<Entry> ret = new ArrayList<>();
        AttributeSet as = c.getAttributeSet();
        for (String name : names(c)) {
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) as.getAttribute(name);
            if (a == null || as.isReadOnly(a)) {
                continue;
            }
            Object v = as.getValue(a);
            ret.add(new Entry(a, v == null ? "" : a.toDisplayString(v), options(a, v)));
            if (ret.size() == MAX) {
                break;
            }
        }
        return ret;
    }

    /** 등록표의 빠른 속성. 등록표에 없는 종류(서브회로, 다른 라이브러리)는 흔한 속성으로 대신한다. */
    static List<String> names(Component c) {
        List<String> q = Kinds.of(c).quickAttrs();
        if (!q.isEmpty()) {
            return q;
        }
        List<String> ret = new ArrayList<>();
        for (String n : new String[] {"label", "width", "facing"}) {
            ret.add(n);
        }
        return ret;
    }

    /** 속성의 선택지. 원조 속성 편집기가 목록 상자일 때만 있다. */
    static List<String[]> options(Attribute<Object> a, Object v) {
        List<String[]> ret = new ArrayList<>();
        java.awt.Component editor;
        try {
            editor = a.getCellEditor(null, v);
        } catch (RuntimeException e) {
            return ret;
        }
        if (editor instanceof javax.swing.JComboBox) {
            javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) editor;
            for (int i = 0; i < combo.getItemCount(); i++) {
                Object o = combo.getItemAt(i);
                ret.add(new String[] {a.toStandardString(o), a.toDisplayString(o)});
            }
        }
        return ret;
    }

    /** 선택한 부품들이 모두 같은 종류면 그 부품들(선 제외), 아니면 빈 목록. 빠른 속성 창의 대상이다. */
    public static List<Component> targets(java.util.Collection<Component> selection) {
        List<Component> ret = new ArrayList<>();
        for (Component c : selection) {
            if (c instanceof Wire) {
                continue;
            }
            if (!ret.isEmpty() && ret.get(0).getFactory() != c.getFactory()) {
                return Collections.emptyList();
            }
            ret.add(c);
        }
        return ret;
    }

    /**
     * 속성 바꾸기. 원조 속성 표(AttrTableSelectionModel)와 같은 Action이다: {@link SetAttributeAction}에 부품마다
     * 같은 속성과 값을 넣고 이름도 같다. 되돌리기 한 번.
     */
    public static SetAttributeAction action(Circuit circuit, List<Component> comps, Attribute<?> attr,
            Object value) {
        SetAttributeAction act = new SetAttributeAction(circuit, ACTION_NAME);
        for (Component c : comps) {
            if (!(c instanceof Wire) && c.getAttributeSet().containsAttribute(attr)) {
                act.set(c, attr, value);
            }
        }
        return act;
    }

    /** 글자 값을 원조 속성의 해석으로 바꾼 Action. 해석할 수 없으면 IllegalArgumentException. */
    public static SetAttributeAction parse(Circuit circuit, List<Component> comps, Attribute<Object> attr,
            String text) {
        Object v;
        try {
            v = attr.parse(text.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(text, e);
        }
        if (v == null) {
            throw new IllegalArgumentException(text);
        }
        return action(circuit, comps, attr, v);
    }

    /** 모든 부품의 속성이 이미 text의 값이면 true(되돌리기 목록에 빈 변경을 넣지 않는다). */
    public static boolean unchanged(List<Component> comps, Attribute<Object> attr, String text) {
        Object v;
        try {
            v = attr.parse(text.trim());
        } catch (RuntimeException e) {
            return false;
        }
        for (Component c : comps) {
            if (!java.util.Objects.equals(c.getAttributeSet().getValue(attr), v)) {
                return false;
            }
        }
        return true;
    }

    /** 제자리에서 고칠 라벨 속성. 없으면 null. */
    @SuppressWarnings("unchecked")
    public static Attribute<Object> labelAttr(Component c) {
        if (c instanceof Wire) {
            return null;
        }
        Attribute<?> a = c.getAttributeSet().getAttribute("label");
        return a == null || c.getAttributeSet().isReadOnly(a) ? null : (Attribute<Object>) a;
    }

    /** 라벨을 text로 바꾸는 Action(제자리 편집). 속성 표에 라벨을 적은 것과 같다. */
    public static SetAttributeAction labelAction(Circuit circuit, Component c, String text) {
        Attribute<Object> a = labelAttr(c);
        if (a == null) {
            throw new IllegalArgumentException("no label");
        }
        return action(circuit, Collections.singletonList(c), a, a.parse(text));
    }

    /**
     * 원조 2.7.1의 숨은 단축키. 부품의 원조 KeyConfigurator에 숫자 키(수정 키 없음, Alt)를 넣어 보고 어떤 속성이
     * 바뀌는지 본다. 선택 도구가 같은 KeyConfigurator로 키를 처리하므로 표시와 실제 동작이 어긋나지 않는다.
     */
    public static List<Hint> hints(Component c) {
        Map<String, Hint> ret = new LinkedHashMap<>();
        AttributeSet as = c.getAttributeSet();
        Object feature = c.getFactory().getFeature(KeyConfigurator.class, as);
        if (!(feature instanceof KeyConfigurator)) {
            return new ArrayList<>();
        }
        KeyConfigurator base = (KeyConfigurator) feature;
        int[] mods = {0, InputEvent.ALT_DOWN_MASK};
        String[] labels = {"0–9", "Alt+0–9"};
        JPanel source = new JPanel();
        for (int m = 0; m < mods.length; m++) {
            for (char d = '1'; d <= '9'; d++) {
                KeyConfigurator k = base.clone();
                KeyEvent key = new KeyEvent(source, KeyEvent.KEY_TYPED, 0L, mods[m], KeyEvent.VK_UNDEFINED, d);
                KeyConfigurationResult r = k.keyEventReceived(new KeyConfigurationEvent(
                        KeyConfigurationEvent.KEY_TYPED, as, key, c));
                if (r != null && !r.getAttributeValues().isEmpty()) {
                    Attribute<?> a = r.getAttributeValues().keySet().iterator().next();
                    ret.putIfAbsent(labels[m], new Hint(labels[m], a));
                    break;
                }
            }
        }
        return new ArrayList<>(ret.values());
    }
}
