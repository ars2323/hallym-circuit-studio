/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.doc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.ac.hallym.hcs.app.BundledLibraries;

/**
 * 열기 전에 .circ가 가리키는 JAR·.circ 라이브러리 파일이 있는지 본다. 원조 로더는 없는 라이브러리를 파일 선택
 * 창으로 묻는데(headless에서는 열 수 없다), 엔진은 그 대신 없는 파일 목록으로 오류를 돌려준다. 경로 규칙은
 * 원조 {@code LibraryManager.loadLibrary}와 같다: 상대 경로는 .circ의 폴더 기준, 번들 MIPS 라이브러리는
 * 경로의 jar가 없으면 번들 jar로 잇는다(D-007).
 */
final class LibraryCheck {
    private LibraryCheck() {
    }

    /** circ가 가리키는데 읽을 수 없는 라이브러리 파일들(descriptor의 경로 글자 그대로). */
    static List<String> missing(File circ) {
        List<String> out = new ArrayList<>();
        Element root;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            root = b.parse(circ).getDocumentElement();
        } catch (Exception e) {
            return out; // 모양이 틀린 파일은 원조 로더가 알린다
        }
        File dir = circ.getAbsoluteFile().getParentFile();
        NodeList kids = root.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (!(n instanceof Element) || !"lib".equals(((Element) n).getTagName())) {
                continue;
            }
            String desc = ((Element) n).getAttribute("desc");
            int sep = desc.indexOf('#');
            if (sep < 0) {
                continue;
            }
            String type = desc.substring(0, sep);
            String name = desc.substring(sep + 1);
            if (type.equals("jar")) {
                int last = name.lastIndexOf('#');
                if (last < 0) {
                    continue;
                }
                String path = name.substring(0, last);
                String cls = name.substring(last + 1);
                File named = resolve(dir, path);
                if (!named.canRead() && BundledLibraries.substitute(named, cls) == null) {
                    out.add(path);
                }
            } else if (type.equals("file")) {
                if (!resolve(dir, name).canRead()) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    private static File resolve(File dir, String path) {
        File f = new File(path);
        return f.isAbsolute() || dir == null ? f : new File(dir, path);
    }
}
