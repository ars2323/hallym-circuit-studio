/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.model;

import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.model.Handle;
import com.cburch.draw.shapes.Curve;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.Line;
import com.cburch.draw.shapes.Oval;
import com.cburch.draw.shapes.Poly;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.draw.shapes.RoundRectangle;
import com.cburch.draw.shapes.Text;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.appear.AppearanceAnchor;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 모양 편집 화면의 자료(model.appearance, N-11, D-153): 서브회로 모양의 도형을 아래부터 번호와 함께. 인스턴스용
 * {@link AppearanceJson}과 달리 포트·기준점도 도형 차례 그대로 들고, 도형마다 원조 속성 표의 속성(글자)·손잡이·
 * 경계·지울 수 있는지를 싣는다. 읽기만 한다.
 *
 * <pre>
 * {circuitId, name, default, editable,
 *  shapes:[{i, kind, svg:{tag, attrs, text?}, attrs:{이름: 글자}, handles:[[x,y]], moves:[bool], bounds:[x,y,w,h],
 *           removable, points?, closed?, text?, port?:{input, pin:[x,y], name, width}, facing?}]}
 * </pre>
 *
 * kind: rect, roundrect, oval, polyline, polygon, line, curve, text, port, anchor. 맞춤(align)은 left·center·right.
 */
public final class AppearanceEditJson {
    private AppearanceEditJson() {
    }

    public static JsonArray point(Location l) {
        JsonArray a = new JsonArray();
        a.add(l.getX());
        a.add(l.getY());
        return a;
    }

    public static JsonObject of(Ids ids, Circuit c, boolean editable) {
        JsonObject o = new JsonObject();
        o.addProperty("circuitId", ids.of(c));
        o.addProperty("name", c.getName());
        o.addProperty("default", c.getAppearance().isDefaultAppearance());
        o.addProperty("editable", editable);
        Document doc = newDocument();
        JsonArray shapes = new JsonArray();
        List<CanvasObject> all = c.getAppearance().getObjectsFromBottom();
        for (int i = 0; i < all.size(); i++) {
            shapes.add(shape(doc, i, all.get(i)));
        }
        o.add("shapes", shapes);
        return o;
    }

    static JsonObject shape(Document doc, int i, CanvasObject s) {
        JsonObject o = new JsonObject();
        o.addProperty("i", i);
        o.addProperty("kind", kind(s));
        if (s instanceof AbstractCanvasObject) {
            o.add("svg", AppearanceJson.element(((AbstractCanvasObject) s).toSvgElement(doc)));
        }
        JsonObject attrs = new JsonObject();
        for (Attribute<?> a : s.getAttributeSet().getAttributes()) {
            @SuppressWarnings("unchecked")
            Attribute<Object> aa = (Attribute<Object>) a;
            Object v = s.getValue(aa);
            if (v == null) {
                continue;
            }
            String text;
            if ((Object) a == DrawAttr.ALIGNMENT) {
                text = v == DrawAttr.ALIGN_LEFT ? "left" : v == DrawAttr.ALIGN_RIGHT ? "right" : "center";
            } else {
                text = aa.toStandardString(v);
            }
            attrs.addProperty(a.getName(), text);
        }
        o.add("attrs", attrs);
        JsonArray handles = new JsonArray();
        JsonArray moves = new JsonArray();
        for (Handle h : s.getHandles(null)) {
            handles.add(point(h.getLocation()));
            moves.add(s.canMoveHandle(h));
        }
        o.add("handles", handles);
        o.add("moves", moves);
        Bounds b = s.getBounds();
        JsonArray bounds = new JsonArray();
        bounds.add(b.getX());
        bounds.add(b.getY());
        bounds.add(b.getWidth());
        bounds.add(b.getHeight());
        o.add("bounds", bounds);
        o.addProperty("removable", s.canRemove());
        if (s instanceof Poly) {
            JsonArray pts = new JsonArray();
            for (Handle h : s.getHandles(null)) {
                pts.add(point(h.getLocation())); // 원조 Poly의 손잡이 = 꼭짓점
            }
            o.add("points", pts);
            o.addProperty("closed", ((Poly) s).isClosed());
        } else if (s instanceof Curve) {
            JsonArray pts = new JsonArray();
            pts.add(point(((Curve) s).getEnd0()));
            pts.add(point(((Curve) s).getEnd1()));
            pts.add(point(((Curve) s).getControl()));
            o.add("points", pts);
        } else if (s instanceof Text) {
            o.addProperty("text", ((Text) s).getText());
            JsonArray at = new JsonArray();
            at.add(((Text) s).getLocation().getX());
            at.add(((Text) s).getLocation().getY());
            o.add("at", at);
        } else if (s instanceof AppearancePort) {
            AppearancePort p = (AppearancePort) s;
            JsonObject po = new JsonObject();
            Instance pin = p.getPin();
            po.addProperty("input", pin == null || com.cburch.logisim.std.wiring.Pin.FACTORY.isInputPin(pin));
            if (pin != null) {
                po.add("pin", point(pin.getLocation()));
                String label = pin.getAttributeValue(StdAttr.LABEL);
                po.addProperty("name", label == null ? "" : label.trim());
                po.addProperty("width", pin.getAttributeValue(StdAttr.WIDTH).getWidth());
            }
            po.add("at", point(p.getLocation()));
            o.add("port", po);
        } else if (s instanceof AppearanceAnchor) {
            o.addProperty("facing", ((AppearanceAnchor) s).getFacing().toString());
            o.add("at", point(((AppearanceAnchor) s).getLocation()));
        }
        return o;
    }

    static String kind(CanvasObject s) {
        if (s instanceof AppearancePort) {
            return "port";
        }
        if (s instanceof AppearanceAnchor) {
            return "anchor";
        }
        if (s instanceof RoundRectangle) {
            return "roundrect";
        }
        if (s instanceof Rectangle) {
            return "rect";
        }
        if (s instanceof Oval) {
            return "oval";
        }
        if (s instanceof Poly) {
            return ((Poly) s).isClosed() ? "polygon" : s.getHandles(null).size() == 2 ? "line" : "polyline";
        }
        if (s instanceof Line) {
            return "line";
        }
        if (s instanceof Curve) {
            return "curve";
        }
        if (s instanceof Text) {
            return "text";
        }
        return "shape";
    }

    private static Document newDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }
}
