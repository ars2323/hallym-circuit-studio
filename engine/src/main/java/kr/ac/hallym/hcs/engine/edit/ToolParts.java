/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.model.Ids;
import kr.ac.hallym.hcs.engine.model.ModelJson;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * model.tool(N-08, D-146): 부품 놓기 도구가 지금 속성으로 loc에 놓을 부품의 모습. 원조 AddTool이 끄는 동안 그리는
 * 유령(회색 윤곽)을 화면이 같은 자리·크기로 그리게 한다. 부품은 회로에 넣지 않고, id는 이 답에만 쓰는 따로 된 번호표로
 * 매긴다(파일의 부품 id와 섞이지 않게 {@code "ghost"}로 바꾼다).
 */
public final class ToolParts {
    private ToolParts() {
    }

    public static JsonObject ghost(Doc d, String lib, String name, Location loc) throws RpcError {
        return ghost(d, lib, name, loc, java.util.Collections.<String, String>emptyMap());
    }

    /**
     * model.toolbar(N-17, D-158, docs/interaction-parity.md I-112): 파일의 {@code <toolbar>}(원조 Project › Options ›
     * Toolbar, 기본 틀: Poke·Edit·Text, 입력 핀, 출력 핀, NOT·AND·OR)를 차례대로, 구분선은 빼고. 원조에서 Ctrl+1…9가 이
     * 차례로 도구를 고른다({@code KeyboardToolSelection}); v1과 v2는 Ctrl+1이 100%라 Ctrl+2…9가 둘째…아홉째다.
     * 놓는 도구는 {@code lib}(파일의 회로면 null)·{@code name}과, 라이브러리 도구와 다른 속성만 {@code attrs}(원조 문자열)로
     * 준다: 화면은 부품 목록의 도구처럼 들고(model.tool, edit.addComponent의 attrs), 같은 부품이 놓인다. 기본 도구(Poke
     * Tool, Edit Tool …)는 {@code tool}로 이름만. 모델은 바꾸지 않는다(읽기만: OpenSaveParityTest.screenOpens).
     */
    public static JsonArray toolbar(Doc d) {
        JsonArray out = new JsonArray();
        for (Tool t : d.file().getOptions().getToolbarData().getContents()) {
            if (t == null) {
                continue;   // a separator
            }
            JsonObject o = new JsonObject();
            o.addProperty("name", t.getName());
            if (t instanceof AddTool) {
                Tool own = null;
                String lib = null;
                for (Tool x : d.file().getTools()) {
                    if (x.sharesSource(t)) {
                        own = x;
                    }
                }
                if (own == null) {
                    for (com.cburch.logisim.tools.Library l : kr.ac.hallym.hcs.app.libs.MipsShadow.libraries(d.file())) {
                        Tool x = l.getTool(t.getName());
                        if (x != null && x.sharesSource(t)) {
                            own = x;
                            lib = l.getName();
                            break;
                        }
                    }
                }
                if (own == null) {
                    continue;   // a tool of a library no longer loaded: nothing to hold
                }
                if (lib == null) {
                    o.add("lib", JsonNull.INSTANCE);
                } else {
                    o.addProperty("lib", lib);
                }
                JsonObject attrs = new JsonObject();
                AttributeSet mine = t.getAttributeSet();
                AttributeSet theirs = own.getAttributeSet();
                if (mine != null) {
                    for (com.cburch.logisim.data.Attribute<?> a : mine.getAttributes()) {
                        @SuppressWarnings("unchecked")
                        com.cburch.logisim.data.Attribute<Object> ao = (com.cburch.logisim.data.Attribute<Object>) a;
                        Object v = mine.getValue(ao);
                        Object w = theirs == null || !theirs.containsAttribute(ao) ? null : theirs.getValue(ao);
                        if (v != null && !v.equals(w)) {
                            attrs.addProperty(a.getName(), ao.toStandardString(v));
                        }
                    }
                }
                if (attrs.size() > 0) {
                    o.add("attrs", attrs);
                }
            } else {
                o.addProperty("tool", t.getName());
            }
            out.add(o);
        }
        return out;
    }

    /** attrs: 놓을 부품에만 줄 값(edit.addComponent의 attrs와 같다, 검색창 "and 3"). 도구 속성은 그대로 둔다. */
    public static JsonObject ghost(Doc d, String lib, String name, Location loc, java.util.Map<String, String> attrs)
            throws RpcError {
        Tool t = SelectionIntents.findTool(d, lib, name);
        if (!(t instanceof AddTool)) {
            throw RpcError.params("tool " + name + " does not place a part");
        }
        AddTool tool = (AddTool) t;
        ComponentFactory f = tool.getFactory();
        if (f == null) {
            throw RpcError.notFound("tool", name);
        }
        AttributeSet as = (AttributeSet) tool.getAttributeSet().clone();
        for (java.util.Map.Entry<String, String> e : attrs.entrySet()) {
            Intents.set(as, e.getKey(), e.getValue());
        }
        Component c = f.createComponent(loc, as);
        JsonObject part = new ModelJson(new Ids(), d.file()).component(c);
        part.addProperty("id", "ghost");
        JsonObject o = new JsonObject();
        o.add("component", part);
        return o;
    }

    /**
     * model.textAt(N-08, D-146): 글자 도구로 loc을 누르면 원조 {@code TextTool.mousePressed}가 여는 글자 칸. 고른 것 먼저,
     * 그다음 회로 전체에서 그 점을 포함하고 {@code TextEditable.getTextCaret}이 칸을 주는 부품(라벨이 비었으면 몸체
     * 어디든, 있으면 라벨 위). 없으면 그 자리의 새 Label(음수 자리면 아무것도 없음). 답: {id, text, box} 또는
     * {id: null, text: "", box}(새 Label) 또는 {id: null, none: true}. 모델은 바꾸지 않는다.
     */
    public static JsonObject textAt(Doc d, com.cburch.logisim.circuit.Circuit c, Location loc) {
        java.awt.Graphics g = SelectionIntents.G;
        com.cburch.logisim.comp.ComponentUserEvent event =
                new com.cburch.logisim.comp.ComponentUserEvent(d.canvas(), loc.getX(), loc.getY());
        java.util.List<Component> order = new java.util.ArrayList<>();
        if (d.project().getCurrentCircuit() == c) {
            order.addAll(d.selection().getComponentsContaining(loc, g));
        }
        order.addAll(c.getAllContaining(loc, g));
        JsonObject o = new JsonObject();
        for (Component comp : order) {
            com.cburch.logisim.tools.TextEditable ed =
                    (com.cburch.logisim.tools.TextEditable) comp.getFeature(com.cburch.logisim.tools.TextEditable.class);
            com.cburch.logisim.tools.Caret caret = ed == null ? null : ed.getTextCaret(event);
            if (caret != null) {
                o.addProperty("id", d.ids().of(comp));
                o.addProperty("text", caret.getText());
                o.add("box", box(caret.getBounds(g)));
                caret.cancelEditing();
                return o;
            }
        }
        if (loc.getX() < 0 || loc.getY() < 0) {
            o.add("id", com.google.gson.JsonNull.INSTANCE);
            o.addProperty("none", true);
            return o;
        }
        // TextTool: 새 Label(글자 도구 속성으로)의 칸
        Component label = com.cburch.logisim.std.base.Text.FACTORY.createComponent(loc,
                (AttributeSet) textToolAttrs(d).clone());
        com.cburch.logisim.tools.TextEditable ed =
                (com.cburch.logisim.tools.TextEditable) label.getFeature(com.cburch.logisim.tools.TextEditable.class);
        com.cburch.logisim.tools.Caret caret = ed == null ? null : ed.getTextCaret(event);
        o.add("id", com.google.gson.JsonNull.INSTANCE);
        o.addProperty("text", "");
        o.add("box", box(caret == null ? label.getBounds() : caret.getBounds(g)));
        if (caret != null) {
            caret.cancelEditing();
        }
        return o;
    }

    private static AttributeSet textToolAttrs(Doc d) {
        Tool t = d.file().getLibrary("Base") == null ? null : d.file().getLibrary("Base").getTool("Text Tool");
        return t != null && t.getAttributeSet() != null ? t.getAttributeSet()
                : com.cburch.logisim.std.base.Text.FACTORY.createAttributeSet();
    }

    private static com.google.gson.JsonArray box(com.cburch.logisim.data.Bounds b) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        a.add(b.getX());
        a.add(b.getY());
        a.add(b.getWidth());
        a.add(b.getHeight());
        return a;
    }
}
