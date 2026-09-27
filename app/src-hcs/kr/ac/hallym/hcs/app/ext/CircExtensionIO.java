/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.ext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * {@link CircExtension}을 .circ에 읽고 쓴다(D-024). 저장 형식:
 *
 * <pre>
 *   &lt;hcs:ext xmlns:hcs="urn:hallym-circuit-studio:ext" version="1"&gt;
 *     &lt;hcs:circuit name="main"&gt;
 *       &lt;hcs:tunnel label="PC" color="#1f77b4"/&gt;
 *     &lt;/hcs:circuit&gt;
 *   &lt;/hcs:ext&gt;
 * </pre>
 *
 * {@code <project>}의 마지막 자식으로 둔다. 원조 2.7.1의 XmlReader는 모르는 최상위 요소를 건너뛰므로 그대로
 * 열린다(원조로 저장하면 이 요소는 빠진다). 엔진의 XmlReader·XmlWriter는 바꾸지 않고, 원조 방식으로 저장한 파일
 * 뒤에 이 요소를 끼워 넣는다. 확장 정보가 없으면 파일을 건드리지 않는다(규칙 2.3, D-006).
 */
public final class CircExtensionIO {
    public static final String NS = "urn:hallym-circuit-studio:ext";
    public static final String PREFIX = "hcs";
    public static final int VERSION = 1;

    private static final Pattern EXISTING = Pattern.compile(
            "[ \\t]*<" + PREFIX + ":ext\\b.*?</" + PREFIX + ":ext>[ \\t]*\\r?\\n?", Pattern.DOTALL);
    private static final String END = "</project>";

    private CircExtensionIO() {
    }

    /** .circ의 확장 정보. 없거나 읽을 수 없으면 빈 것. 파일 자체의 오류는 원조 로더가 따로 알린다. */
    public static CircExtension read(File file) throws IOException {
        return parse(Files.readAllBytes(file.toPath()));
    }

    /**
     * 속성은 파일에 적힌 차례 그대로 읽는다(SAX). DOM은 속성을 이름 차례로 돌려주어, 열고 저장만 해도
     * {@code <hcs:splitter x=".." y=".." arm0=".."/>}가 {@code arm0 .. x y}로 바뀌었다(D-149).
     */
    static CircExtension parse(byte[] xml) throws IOException {
        CircExtension ext = new CircExtension();
        try {
            parser().parse(new ByteArrayInputStream(xml), new DefaultHandler() {
                private int depth; // 뿌리 요소 = 1
                private boolean inExt;
                private String circuit;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes atts) {
                    depth++;
                    if (depth == 2 && isHcs(uri, localName, "ext")) {
                        inExt = true;
                    } else if (depth == 3 && inExt && isHcs(uri, localName, "circuit")) {
                        String name = atts.getValue("", "name");
                        circuit = name == null ? "" : name;
                    } else if (depth == 4 && circuit != null && NS.equals(uri)) {
                        ext.add(circuit, new CircExtension.Item(localName, attrs(atts)));
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    if (depth == 3) {
                        circuit = null;
                    } else if (depth == 2) {
                        inExt = false;
                    }
                    depth--;
                }
            });
        } catch (SAXException e) {
            return new CircExtension(); // 읽을 수 없는 파일: 확장 정보 없음(원조 로더가 따로 알린다)
        }
        return ext;
    }

    private static Map<String, String> attrs(Attributes atts) {
        Map<String, String> ret = new LinkedHashMap<>();
        for (int k = 0; k < atts.getLength(); k++) {
            if (atts.getURI(k).isEmpty()) {
                ret.put(atts.getQName(k), atts.getValue(k));
            }
        }
        return ret;
    }

    private static boolean isHcs(String uri, String localName, String want) {
        return NS.equals(uri) && want.equals(localName);
    }

    private static SAXParser parser() throws IOException {
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException(e);
        }
    }

    /**
     * 원조 방식으로 저장된 file 끝에 확장 정보를 넣는다. 확장 정보가 비어 있고 파일에 옛 확장 요소도 없으면
     * 파일을 전혀 건드리지 않는다. 임시 파일에 쓴 뒤 바꿔 끼운다.
     */
    public static void writeInto(File file, CircExtension ext) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String updated = insert(text, ext);
        if (updated.equals(text)) {
            return;
        }
        File dir = file.getAbsoluteFile().getParentFile();
        File tmp = File.createTempFile(".hcs-save", ".tmp", dir);
        try {
            Files.write(tmp.toPath(), updated.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    /** 원조 writer가 쓴 .circ 바이트에 확장 정보를 넣은 것(자동 저장용). */
    public static byte[] withExtension(byte[] xml, CircExtension ext) throws IOException {
        String text = new String(xml, StandardCharsets.UTF_8);
        return insert(text, ext).getBytes(StandardCharsets.UTF_8);
    }

    static String insert(String text, CircExtension ext) throws IOException {
        Matcher m = EXISTING.matcher(text);
        String base = m.find() ? m.replaceAll("") : text;
        if (ext.isEmpty()) {
            return base;
        }
        int end = base.lastIndexOf(END);
        if (end < 0) {
            throw new IOException("not a Logisim project file: missing " + END);
        }
        String nl = base.contains("\r\n") ? "\r\n" : "\n";
        return base.substring(0, end) + toXml(ext, nl) + base.substring(end);
    }

    static String toXml(CircExtension ext, String nl) {
        StringBuilder sb = new StringBuilder();
        sb.append("  <").append(PREFIX).append(":ext xmlns:").append(PREFIX).append("=\"").append(NS)
                .append("\" version=\"").append(VERSION).append("\">").append(nl);
        for (String circuit : ext.circuits()) {
            sb.append("    <").append(PREFIX).append(":circuit name=\"").append(escape(circuit)).append("\">")
                    .append(nl);
            for (CircExtension.Item item : ext.items(circuit)) {
                sb.append("      <").append(PREFIX).append(':').append(item.kind());
                for (Map.Entry<String, String> a : item.attrs().entrySet()) {
                    sb.append(' ').append(a.getKey()).append("=\"").append(escape(a.getValue())).append('"');
                }
                sb.append("/>").append(nl);
            }
            sb.append("    </").append(PREFIX).append(":circuit>").append(nl);
        }
        sb.append("  </").append(PREFIX).append(":ext>").append(nl);
        return sb.toString();
    }

    /** 속성 값 이스케이프. 줄바꿈·탭도 문자 참조로 바꿔 읽을 때 그대로 돌아오게 한다. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '&': sb.append("&amp;"); break;
            case '<': sb.append("&lt;"); break;
            case '>': sb.append("&gt;"); break;
            case '"': sb.append("&quot;"); break;
            case '\n': sb.append("&#10;"); break;
            case '\r': sb.append("&#13;"); break;
            case '\t': sb.append("&#9;"); break;
            default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
