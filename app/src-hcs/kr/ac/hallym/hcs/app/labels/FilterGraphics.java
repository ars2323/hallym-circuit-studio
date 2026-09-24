/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;
import java.util.Set;

/**
 * 그리기를 그대로 넘기되, 정해진 글자·자리의 {@code drawString}만 빼는 Graphics(#79). 편집 캔버스에서 원조
 * 라벨(원조 TextField가 그리는 글자)을 빼고 그 자리를 라벨 칩이 맡게 한다. 원조 엔진 코드는 그대로이고,
 * 빼는 것은 미리 계산한 라벨 글자와 좌표가 정확히 같은 호출뿐이다.
 */
final class FilterGraphics extends Graphics2D {
    private final Graphics2D g;
    private final Set<String> skip;

    FilterGraphics(Graphics2D g, Set<String> skip) {
        this.g = g;
        this.skip = skip;
    }

    /** 뺄 호출의 열쇠: 글자와 기준선 좌표. */
    static String key(String text, int x, int y) {
        return text + '\u0000' + x + ',' + y;
    }

    @Override
    public void drawString(String str, int x, int y) {
        if (!skip.contains(key(str, x, y))) {
            g.drawString(str, x, y);
        }
    }

    @Override
    public Graphics create() {
        return new FilterGraphics((Graphics2D) g.create(), skip);
    }

    // 나머지는 그대로 넘긴다

    @Override
    public void drawString(String str, float x, float y) {
        g.drawString(str, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator it, int x, int y) {
        g.drawString(it, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator it, float x, float y) {
        g.drawString(it, x, y);
    }

    @Override
    public void draw(Shape s) {
        g.draw(s);
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        return g.drawImage(img, xform, obs);
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        g.drawImage(img, op, x, y);
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        g.drawRenderedImage(img, xform);
    }

    @Override
    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
        g.drawRenderableImage(img, xform);
    }

    @Override
    public void drawGlyphVector(GlyphVector gv, float x, float y) {
        g.drawGlyphVector(gv, x, y);
    }

    @Override
    public void fill(Shape s) {
        g.fill(s);
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        return g.hit(rect, s, onStroke);
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return g.getDeviceConfiguration();
    }

    @Override
    public void setComposite(Composite comp) {
        g.setComposite(comp);
    }

    @Override
    public void setPaint(Paint paint) {
        g.setPaint(paint);
    }

    @Override
    public void setStroke(Stroke s) {
        g.setStroke(s);
    }

    @Override
    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
        g.setRenderingHint(hintKey, hintValue);
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key hintKey) {
        return g.getRenderingHint(hintKey);
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        g.setRenderingHints(hints);
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        g.addRenderingHints(hints);
    }

    @Override
    public RenderingHints getRenderingHints() {
        return g.getRenderingHints();
    }

    @Override
    public void translate(int x, int y) {
        g.translate(x, y);
    }

    @Override
    public void translate(double tx, double ty) {
        g.translate(tx, ty);
    }

    @Override
    public void rotate(double theta) {
        g.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        g.rotate(theta, x, y);
    }

    @Override
    public void scale(double sx, double sy) {
        g.scale(sx, sy);
    }

    @Override
    public void shear(double shx, double shy) {
        g.shear(shx, shy);
    }

    @Override
    public void transform(AffineTransform tx) {
        g.transform(tx);
    }

    @Override
    public void setTransform(AffineTransform tx) {
        g.setTransform(tx);
    }

    @Override
    public AffineTransform getTransform() {
        return g.getTransform();
    }

    @Override
    public Paint getPaint() {
        return g.getPaint();
    }

    @Override
    public Composite getComposite() {
        return g.getComposite();
    }

    @Override
    public void setBackground(Color color) {
        g.setBackground(color);
    }

    @Override
    public Color getBackground() {
        return g.getBackground();
    }

    @Override
    public Stroke getStroke() {
        return g.getStroke();
    }

    @Override
    public void clip(Shape s) {
        g.clip(s);
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return g.getFontRenderContext();
    }

    @Override
    public Color getColor() {
        return g.getColor();
    }

    @Override
    public void setColor(Color c) {
        g.setColor(c);
    }

    @Override
    public void setPaintMode() {
        g.setPaintMode();
    }

    @Override
    public void setXORMode(Color c1) {
        g.setXORMode(c1);
    }

    @Override
    public Font getFont() {
        return g.getFont();
    }

    @Override
    public void setFont(Font font) {
        g.setFont(font);
    }

    @Override
    public FontMetrics getFontMetrics() {
        return g.getFontMetrics();
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        return g.getFontMetrics(f);
    }

    @Override
    public Rectangle getClipBounds() {
        return g.getClipBounds();
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        g.clipRect(x, y, width, height);
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        g.setClip(x, y, width, height);
    }

    @Override
    public Shape getClip() {
        return g.getClip();
    }

    @Override
    public void setClip(Shape clip) {
        g.setClip(clip);
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        g.copyArea(x, y, width, height, dx, dy);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        g.drawLine(x1, y1, x2, y2);
    }

    @Override
    public void drawRect(int x, int y, int width, int height) {
        g.drawRect(x, y, width, height);
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        g.fillRect(x, y, width, height);
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        g.clearRect(x, y, width, height);
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        g.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        g.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        g.drawOval(x, y, width, height);
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        g.fillOval(x, y, width, height);
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        g.drawArc(x, y, width, height, startAngle, arcAngle);
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        g.fillArc(x, y, width, height, startAngle, arcAngle);
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        g.drawPolyline(xPoints, yPoints, nPoints);
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        g.drawPolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        g.fillPolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        return g.drawImage(img, x, y, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        return g.drawImage(img, x, y, width, height, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        return g.drawImage(img, x, y, bgcolor, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor,
            ImageObserver observer) {
        return g.drawImage(img, x, y, width, height, bgcolor, observer);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
            ImageObserver observer) {
        return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
            Color bgcolor, ImageObserver observer) {
        return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
    }

    @Override
    public void dispose() {
        g.dispose();
    }
}
