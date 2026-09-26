/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.export;

import java.awt.Color;
import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;

import javax.imageio.ImageIO;

/**
 * 벡터 기록({@link VectorGraphics})을 SVG와 PDF로 쓴다(E-07). 외부 라이브러리 없이 직접 쓴다. 도형은 모두 채운 경로라
 * 두 형식이 같은 모양이다. PDF는 흰 바탕 위 반투명 색을 흰색과 섞은 불투명 색으로 적는다(흰 바탕으로 내보내므로 같은
 * 모양). SVG의 그림(이미지)은 PNG로 넣고, PDF는 그림을 빼고 도형만 적는다(회로 그림에는 그림이 없다).
 */
final class VectorWriters {
    private VectorWriters() {
    }

    static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-4) {
            return Long.toString(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.3f", v);
    }

    /** 경로 글: SVG는 M/L/C/Q/Z, PDF는 m/l/c/h(PDF는 y를 뒤집는다). */
    static String path(java.awt.Shape s, boolean pdf, double height) {
        StringBuilder sb = new StringBuilder();
        double[] c = new double[6];
        double lastX = 0;
        double lastY = 0;
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            if (pdf) {
                for (int i = 1; i < 6; i += 2) {
                    c[i] = height - c[i];
                }
            }
            switch (t) {
            case PathIterator.SEG_MOVETO:
                sb.append(pdf ? num(c[0]) + " " + num(c[1]) + " m " : "M" + num(c[0]) + " " + num(c[1]));
                lastX = c[0];
                lastY = c[1];
                break;
            case PathIterator.SEG_LINETO:
                sb.append(pdf ? num(c[0]) + " " + num(c[1]) + " l " : "L" + num(c[0]) + " " + num(c[1]));
                lastX = c[0];
                lastY = c[1];
                break;
            case PathIterator.SEG_QUADTO: {
                // PDF에는 2차 곡선이 없다: 3차로 올린다
                double c1x = lastX + 2.0 / 3 * (c[0] - lastX);
                double c1y = lastY + 2.0 / 3 * (c[1] - lastY);
                double c2x = c[2] + 2.0 / 3 * (c[0] - c[2]);
                double c2y = c[3] + 2.0 / 3 * (c[1] - c[3]);
                sb.append(pdf ? num(c1x) + " " + num(c1y) + " " + num(c2x) + " " + num(c2y) + " " + num(c[2]) + " "
                        + num(c[3]) + " c " : "Q" + num(c[0]) + " " + num(c[1]) + " " + num(c[2]) + " " + num(c[3]));
                lastX = c[2];
                lastY = c[3];
                break;
            }
            case PathIterator.SEG_CUBICTO:
                sb.append(pdf ? num(c[0]) + " " + num(c[1]) + " " + num(c[2]) + " " + num(c[3]) + " " + num(c[4])
                        + " " + num(c[5]) + " c " : "C" + num(c[0]) + " " + num(c[1]) + " " + num(c[2]) + " "
                                + num(c[3]) + " " + num(c[4]) + " " + num(c[5]));
                lastX = c[4];
                lastY = c[5];
                break;
            default:
                sb.append(pdf ? "h " : "Z");
                break;
            }
        }
        return sb.toString();
    }

    static boolean evenOdd(java.awt.Shape s) {
        return s.getPathIterator(null).getWindingRule() == PathIterator.WIND_EVEN_ODD;
    }

    static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** SVG 한 문서. width·height는 장치 좌표(px). */
    static void svg(List<Object> items, int width, int height, OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"")
                .append(width).append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width)
                .append(' ').append(height).append("\">\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        for (Object o : items) {
            if (o instanceof VectorGraphics.Fill) {
                VectorGraphics.Fill f = (VectorGraphics.Fill) o;
                sb.append("<path d=\"").append(path(f.shape, false, 0)).append("\" fill=\"").append(hex(f.color))
                        .append('"');
                if (f.color.getAlpha() < 255) {
                    sb.append(" fill-opacity=\"").append(num(f.color.getAlpha() / 255.0)).append('"');
                }
                if (evenOdd(f.shape)) {
                    sb.append(" fill-rule=\"evenodd\"");
                }
                sb.append("/>\n");
            } else {
                VectorGraphics.Picture p = (VectorGraphics.Picture) o;
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(p.image, "PNG", png);
                double[] m = new double[6];
                p.at.getMatrix(m);
                sb.append("<image width=\"").append(p.image.getWidth()).append("\" height=\"")
                        .append(p.image.getHeight()).append("\" transform=\"matrix(").append(num(m[0])).append(' ')
                        .append(num(m[1])).append(' ').append(num(m[2])).append(' ').append(num(m[3])).append(' ')
                        .append(num(m[4])).append(' ').append(num(m[5])).append(")\" xlink:href=\"data:image/png;base64,")
                        .append(Base64.getEncoder().encodeToString(png.toByteArray())).append("\"/>\n");
            }
        }
        sb.append("</svg>\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 흰 바탕과 섞은 불투명 색(PDF). */
    static double[] rgbOnWhite(Color c) {
        double a = c.getAlpha() / 255.0;
        return new double[] {(c.getRed() * a + 255 * (1 - a)) / 255, (c.getGreen() * a + 255 * (1 - a)) / 255,
            (c.getBlue() * a + 255 * (1 - a)) / 255};
    }

    /** PDF 한 쪽(1.4). 크기는 장치 좌표 1px = 1pt. 내용은 압축한다. */
    static void pdf(List<Object> items, int width, int height, OutputStream out) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("1 1 1 rg 0 0 ").append(width).append(' ').append(height).append(" re f\n");
        for (Object o : items) {
            if (!(o instanceof VectorGraphics.Fill)) {
                continue;
            }
            VectorGraphics.Fill f = (VectorGraphics.Fill) o;
            double[] rgb = rgbOnWhite(f.color);
            content.append(num(rgb[0])).append(' ').append(num(rgb[1])).append(' ').append(num(rgb[2]))
                    .append(" rg ").append(path(f.shape, true, height)).append(evenOdd(f.shape) ? "f*" : "f")
                    .append('\n');
        }
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(zipped)) {
            z.write(content.toString().getBytes(StandardCharsets.US_ASCII));
        }
        byte[] stream = zipped.toByteArray();

        ByteArrayOutputStream doc = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(doc, "%PDF-1.4\n%âãÏÓ\n");
        offsets.add(doc.size());
        write(doc, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets.add(doc.size());
        write(doc, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        offsets.add(doc.size());
        write(doc, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + width + " " + height
                + "] /Resources << >> /Contents 4 0 R >>\nendobj\n");
        offsets.add(doc.size());
        write(doc, "4 0 obj\n<< /Length " + stream.length + " /Filter /FlateDecode >>\nstream\n");
        doc.write(stream);
        write(doc, "\nendstream\nendobj\n");
        int xref = doc.size();
        StringBuilder x = new StringBuilder("xref\n0 5\n0000000000 65535 f \n");
        for (int off : offsets) {
            x.append(String.format(Locale.ROOT, "%010d 00000 n \n", off));
        }
        x.append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(doc, x.toString());
        out.write(doc.toByteArray());
    }

    private static void write(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
