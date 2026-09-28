/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.model;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.draw.model.CanvasObject;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.appear.AppearanceAnchor;
import com.cburch.logisim.circuit.appear.AppearancePort;
import com.cburch.logisim.circuit.appear.CircuitAppearance;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 서브회로의 모양(N-05, D-137): 화면이 서브회로 인스턴스를 원조와 같은 자리·크기로 그리도록, 회로의 모양 도형을 원조
 * .circ의 {@code <appear>}와 같은 SVG 요소 글자로 싣는다. 기본 모양(원조가 핀으로 만드는 상자와 홈)도 같은 도형
 * 목록이다(원조 {@code DefaultAppearance}).
 *
 * <pre>
 * {default, anchor:[x,y], facing, shapes:[{tag, attrs:{…}, text?}], ports:[{at:[x,y], pin:[x,y], input}],
 *  label?:{text, facing, font}}
 * </pre>
 *
 * 좌표는 모양 편집기의 좌표다. 인스턴스(위치 loc, 방향 f)에서 점 p는 {@code loc + R(θ)(p − anchor)},
 * θ = facing.toRadians() − f.toRadians()이고 R은 원조 {@code Graphics2D.rotate}(화면 좌표, y 아래)다. 포트
 * {@code at}을 그렇게 옮기면 엔진이 준 인스턴스 포트 위치가 된다(기하 동등성 N-06이 확인한다).
 * {@code label}은 회로 속성의 부품 안 글자(원조 Circuit Label, {@code clabel}, {@code clabelup}, {@code clabelfont}).
 * 읽기만 하고 원조 모양 코드를 바꾸지 않는다.
 */
public final class AppearanceJson {
    private AppearanceJson() {
    }

    public static JsonObject of(Circuit circuit) {
        CircuitAppearance app = circuit.getAppearance();
        JsonObject o = new JsonObject();
        o.addProperty("default", app.isDefaultAppearance());
        Document doc = newDocument();
        JsonArray shapes = new JsonArray();
        JsonArray ports = new JsonArray();
        Location anchor = null;
        Direction facing = Direction.EAST;
        for (CanvasObject shape : app.getObjectsFromBottom()) {
            if (shape instanceof AppearanceAnchor) {
                AppearanceAnchor a = (AppearanceAnchor) shape;
                anchor = a.getLocation();
                facing = a.getFacing();
            } else if (shape instanceof AppearancePort) {
                AppearancePort p = (AppearancePort) shape;
                JsonObject po = new JsonObject();
                po.add("at", ModelJson.point(p.getLocation()));
                if (p.getPin() != null) {
                    po.add("pin", ModelJson.point(p.getPin().getLocation()));
                }
                Element e = p.toSvgElement(doc);
                // 원조가 입력 포트는 반지름 4, 출력은 5로 적는다(AppearancePort)
                po.addProperty("input", "8".equals(e.getAttribute("width")));
                ports.add(po);
            } else if (shape instanceof AbstractCanvasObject) {
                shapes.add(element(((AbstractCanvasObject) shape).toSvgElement(doc)));
            }
        }
        // 원조가 닻이 없을 때 쓰는 자리(CircuitAppearance.findAnchorLocation)
        o.add("anchor", ModelJson.point(anchor == null ? Location.create(100, 100) : anchor));
        o.addProperty("facing", facing.toString());
        o.add("shapes", shapes);
        o.add("ports", ports);
        AttributeSet s = circuit.getStaticAttributes();
        String label = s.getValue(CircuitAttributes.CIRCUIT_LABEL_ATTR);
        if (label != null && !label.isEmpty()) {
            JsonObject l = new JsonObject();
            l.addProperty("text", label);
            l.addProperty("facing", ModelJson.text(s, CircuitAttributes.CIRCUIT_LABEL_FACING_ATTR));
            l.addProperty("font", ModelJson.text(s, CircuitAttributes.CIRCUIT_LABEL_FONT_ATTR));
            o.add("label", l);
        }
        return o;
    }

    /** SVG 요소 하나: 태그, 속성 글자들, 글자 요소면 그 글. */
    static JsonObject element(Element e) {
        JsonObject o = new JsonObject();
        o.addProperty("tag", e.getTagName());
        JsonObject attrs = new JsonObject();
        NamedNodeMap m = e.getAttributes();
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        for (int i = 0; i < m.getLength(); i++) {
            Node a = m.item(i);
            sorted.put(a.getNodeName(), a.getNodeValue());
        }
        for (java.util.Map.Entry<String, String> a : sorted.entrySet()) {
            attrs.addProperty(a.getKey(), a.getValue());
        }
        o.add("attrs", attrs);
        if ("text".equals(e.getTagName())) {
            o.addProperty("text", e.getTextContent());
        }
        return o;
    }

    private static Document newDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }
}
