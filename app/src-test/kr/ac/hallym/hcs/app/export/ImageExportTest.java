/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/**
 * E-07: demo-datapath를 PNG(배율), SVG(벡터 경로, XML로 읽힘), PDF(벡터, 구조와 pdfinfo)로. 고른 부분만 내보내기. 벡터
 * 기록기는 글자를 외곽선으로, 잘라내기를 교집합으로.
 */
class ImageExportTest {
    @TempDir
    Path tmp;

    Object[] demo() throws Exception {
        LogisimFile f = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
        Project proj = new Project(f);
        Circuit c = f.getMainCircuit();
        CircuitState s = proj.getCircuitState();
        s.getPropagator().propagate();
        return new Object[] {c, s};
    }

    @Test
    void pngSvgAndPdf() throws Exception {
        Object[] d = demo();
        Circuit c = (Circuit) d[0];
        CircuitState s = (CircuitState) d[1];
        File png = tmp.resolve("a.png").toFile();
        assertTrue(ImageExport.write(png, ImageExport.Format.PNG, null, c, s, null, false, 3), "png");
        BufferedImage img = ImageIO.read(png);
        Bounds b = ImageExport.area(c, null, new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics());
        assertEquals(b.getWidth() * 3, img.getWidth());
        assertEquals(b.getHeight() * 3, img.getHeight());

        File svg = tmp.resolve("a.svg").toFile();
        assertTrue(ImageExport.write(svg, ImageExport.Format.SVG, null, c, s, null, false, 1), "svg");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg);
        assertEquals(Integer.toString(b.getWidth()), doc.getDocumentElement().getAttribute("width"));
        assertTrue(doc.getElementsByTagName("path").getLength() > 100, "vector paths");

        File pdf = tmp.resolve("a.pdf").toFile();
        assertTrue(ImageExport.write(pdf, ImageExport.Format.PDF, null, c, s, null, false, 1), "pdf");
        String head = new String(Files.readAllBytes(pdf.toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("%PDF-1.4"), "header");
        assertTrue(head.contains("/MediaBox [0 0 " + b.getWidth() + " " + b.getHeight() + "]"), "mediabox " + b + " " + head.substring(head.indexOf("/MediaBox"), head.indexOf("/MediaBox") + 30));
        assertTrue(head.trim().endsWith("%%EOF"), "eof");
        File pdfinfo = new File("/usr/bin/pdfinfo");
        if (pdfinfo.canExecute()) {
            Process p = new ProcessBuilder(pdfinfo.getPath(), pdf.getPath()).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), out);
            assertTrue(out.contains("Pages:") && out.contains("1"), out);
        }
    }

    @Test
    void onlyTheSelection() throws Exception {
        Object[] d = demo();
        Circuit c = (Circuit) d[0];
        CircuitState s = (CircuitState) d[1];
        List<Component> one = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Instruction Memory")) {
                one.add(x);
            }
        }
        File png = tmp.resolve("sel.png").toFile();
        assertTrue(ImageExport.write(png, ImageExport.Format.PNG, null, c, s, one, false, 1));
        BufferedImage img = ImageIO.read(png);
        assertEquals(one.get(0).getBounds().getWidth() + 2 * ImageExport.BORDER, img.getWidth());
        assertTrue(!ImageExport.write(tmp.resolve("none.png").toFile(), ImageExport.Format.PNG, null, c, s,
                new ArrayList<>(), false, 1), "nothing selected");
    }

    @Test
    void textBecomesOutlinesAndClipCuts() {
        VectorGraphics.Sink sink = new VectorGraphics.Sink();
        VectorGraphics g = new VectorGraphics(sink);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.setColor(Color.RED);
        g.drawString("한림 R0", 10, 30);
        assertEquals(1, sink.items.size());
        assertTrue(g.extent().getWidth() > 40, "glyph outlines");
        sink.items.clear();
        g.clipRect(0, 0, 10, 10);
        g.fillRect(5, 5, 100, 100);
        assertEquals(5.0, g.extent().getWidth(), 1e-6, "clipped to the rectangle");
        g.setClip(null);
        g.drawLine(0, 50, 100, 50);
        assertTrue(g.extent().getMaxX() >= 100, "strokes become outlines");
    }
}
