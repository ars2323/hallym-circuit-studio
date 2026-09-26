/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.export;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 그림 내보내기(E-07)의 벡터 기록기: 모든 그리기를 장치 좌표의 채운 도형으로 바꿔 모은다. 선은 그 굵기의 외곽선,
 * 글자는 글꼴 외곽선(한글 라벨도 글꼴을 싣지 않고 그대로), 잘라내기는 도형 교집합으로. SVG·PDF 작성기가 이 목록을
 * 쓴다. 그림(이미지)은 위치·크기와 함께 따로 모은다.
 */
final class VectorGraphics extends Graphics2D {
    /** 채운 도형 하나. */
    static final class Fill {
        final Shape shape;
        final Color color;

        Fill(Shape shape, Color color) {
            this.shape = shape;
            this.color = color;
        }
    }

    /** 그림 하나(장치 좌표 변환 포함). */
    static final class Picture {
        final BufferedImage image;
        final AffineTransform at;

        Picture(BufferedImage image, AffineTransform at) {
            this.image = image;
            this.at = at;
        }
    }

    /** 모은 것(create()한 사본들이 함께 쓴다). */
    static final class Sink {
        final List<Object> items = new ArrayList<>();
    }

    private static final Graphics2D METRICS = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

    private final Sink sink;
    private AffineTransform transform = new AffineTransform();
    private Color color = Color.BLACK;
    private Paint paint = Color.BLACK;
    private Color background = Color.WHITE;
    private Stroke stroke = new BasicStroke(1f);
    private Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private Composite composite = AlphaComposite.SrcOver;
    private Shape clip; // 장치 좌표, null이면 없음
    private final RenderingHints hints = new RenderingHints(null);

    VectorGraphics(Sink sink) {
        this.sink = sink;
    }

    List<Object> items() {
        return sink.items;
    }

    private Color effective() {
        float a = composite instanceof AlphaComposite ? ((AlphaComposite) composite).getAlpha() : 1f;
        Color c = paint instanceof Color ? (Color) paint : color;
        if (a >= 1f) {
            return c;
        }
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * a));
    }

    private void emit(Shape userShape) {
        Shape dev = transform.createTransformedShape(userShape);
        if (clip != null) {
            Area a = new Area(dev);
            a.intersect(new Area(clip));
            if (a.isEmpty()) {
                return;
            }
            dev = a;
        }
        Color c = effective();
        if (c.getAlpha() == 0) {
            return;
        }
        sink.items.add(new Fill(dev, c));
    }

    // ---- 도형 ----

    @Override
    public void draw(Shape s) {
        emit(stroke.createStrokedShape(s));
    }

    @Override
    public void fill(Shape s) {
        emit(s);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        draw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void fillRect(int x, int y, int w, int h) {
        fill(new Rectangle(x, y, w, h));
    }

    @Override
    public void drawRect(int x, int y, int w, int h) {
        draw(new Rectangle(x, y, w, h));
    }

    @Override
    public void clearRect(int x, int y, int w, int h) {
        Paint p = paint;
        Color c = color;
        setColor(background);
        fillRect(x, y, w, h);
        paint = p;
        color = c;
    }

    @Override
    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        draw(new RoundRectangle2D.Double(x, y, w, h, aw, ah));
    }

    @Override
    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        fill(new RoundRectangle2D.Double(x, y, w, h, aw, ah));
    }

    @Override
    public void drawOval(int x, int y, int w, int h) {
        draw(new Ellipse2D.Double(x, y, w, h));
    }

    @Override
    public void fillOval(int x, int y, int w, int h) {
        fill(new Ellipse2D.Double(x, y, w, h));
    }

    @Override
    public void drawArc(int x, int y, int w, int h, int start, int extent) {
        draw(new Arc2D.Double(x, y, w, h, start, extent, Arc2D.OPEN));
    }

    @Override
    public void fillArc(int x, int y, int w, int h, int start, int extent) {
        fill(new Arc2D.Double(x, y, w, h, start, extent, Arc2D.PIE));
    }

    @Override
    public void drawPolyline(int[] xs, int[] ys, int n) {
        if (n < 2) {
            return;
        }
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            p.lineTo(xs[i], ys[i]);
        }
        draw(p);
    }

    @Override
    public void drawPolygon(int[] xs, int[] ys, int n) {
        draw(new Polygon(xs, ys, n));
    }

    @Override
    public void fillPolygon(int[] xs, int[] ys, int n) {
        fill(new Polygon(xs, ys, n));
    }

    // ---- 글자 ----

    @Override
    public void drawString(String str, int x, int y) {
        drawString(str, (float) x, (float) y);
    }

    @Override
    public void drawString(String str, float x, float y) {
        if (str == null || str.isEmpty()) {
            return;
        }
        GlyphVector gv = font.createGlyphVector(getFontRenderContext(), str);
        fill(gv.getOutline(x, y));
    }

    @Override
    public void drawString(AttributedCharacterIterator it, int x, int y) {
        drawString(it, (float) x, (float) y);
    }

    @Override
    public void drawString(AttributedCharacterIterator it, float x, float y) {
        TextLayout tl = new TextLayout(it, getFontRenderContext());
        fill(tl.getOutline(AffineTransform.getTranslateInstance(x, y)));
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
        fill(g.getOutline(x, y));
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return new FontRenderContext(new AffineTransform(), true, true);
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        return METRICS.getFontMetrics(f);
    }

    // ---- 그림 ----

    private boolean picture(Image img, AffineTransform where) {
        if (img == null) {
            return true;
        }
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        if (w <= 0 || h <= 0) {
            return false;
        }
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        AffineTransform at = new AffineTransform(transform);
        at.concatenate(where);
        sink.items.add(new Picture(b, at));
        return true;
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        return picture(img, xform == null ? new AffineTransform() : xform);
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        picture(op == null ? img : op.filter(img, null), AffineTransform.getTranslateInstance(x, y));
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        if (img instanceof BufferedImage) {
            picture((BufferedImage) img, xform);
        }
    }

    @Override
    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
        drawRenderedImage(img.createDefaultRendering(), xform);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver obs) {
        return picture(img, AffineTransform.getTranslateInstance(x, y));
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver obs) {
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            return false;
        }
        AffineTransform at = AffineTransform.getTranslateInstance(x, y);
        at.scale(w / (double) iw, h / (double) ih);
        return picture(img, at);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bg, ImageObserver obs) {
        return drawImage(img, x, y, obs);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int w, int h, Color bg, ImageObserver obs) {
        return drawImage(img, x, y, w, h, obs);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
            ImageObserver obs) {
        int w = sx2 - sx1;
        int h = sy2 - sy1;
        if (w <= 0 || h <= 0) {
            return false;
        }
        BufferedImage part = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = part.createGraphics();
        g.drawImage(img, 0, 0, w, h, sx1, sy1, sx2, sy2, null);
        g.dispose();
        return drawImage(part, dx1, dy1, dx2 - dx1, dy2 - dy1, obs);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
            Color bg, ImageObserver obs) {
        return drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, obs);
    }

    // ---- 상태 ----

    @Override
    public Graphics create() {
        VectorGraphics g = new VectorGraphics(sink);
        g.transform = new AffineTransform(transform);
        g.color = color;
        g.paint = paint;
        g.background = background;
        g.stroke = stroke;
        g.font = font;
        g.composite = composite;
        g.clip = clip;
        g.hints.putAll(hints);
        return g;
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        Shape dev = transform.createTransformedShape(onStroke ? stroke.createStrokedShape(s) : s);
        return dev.intersects(rect);
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return METRICS.getDeviceConfiguration();
    }

    @Override
    public void setComposite(Composite comp) {
        composite = comp;
    }

    @Override
    public void setPaint(Paint p) {
        if (p != null) {
            paint = p;
            if (p instanceof Color) {
                color = (Color) p;
            }
        }
    }

    @Override
    public void setStroke(Stroke s) {
        stroke = s;
    }

    @Override
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        hints.put(key, value);
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key key) {
        return hints.get(key);
    }

    @Override
    public void setRenderingHints(Map<?, ?> h) {
        hints.clear();
        hints.putAll(h);
    }

    @Override
    public void addRenderingHints(Map<?, ?> h) {
        hints.putAll(h);
    }

    @Override
    public RenderingHints getRenderingHints() {
        return (RenderingHints) hints.clone();
    }

    @Override
    public void translate(int x, int y) {
        transform.translate(x, y);
    }

    @Override
    public void translate(double tx, double ty) {
        transform.translate(tx, ty);
    }

    @Override
    public void rotate(double theta) {
        transform.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        transform.rotate(theta, x, y);
    }

    @Override
    public void scale(double sx, double sy) {
        transform.scale(sx, sy);
    }

    @Override
    public void shear(double shx, double shy) {
        transform.shear(shx, shy);
    }

    @Override
    public void transform(AffineTransform tx) {
        transform.concatenate(tx);
    }

    @Override
    public void setTransform(AffineTransform tx) {
        transform = new AffineTransform(tx);
    }

    @Override
    public AffineTransform getTransform() {
        return new AffineTransform(transform);
    }

    @Override
    public Paint getPaint() {
        return paint;
    }

    @Override
    public Composite getComposite() {
        return composite;
    }

    @Override
    public void setBackground(Color c) {
        background = c;
    }

    @Override
    public Color getBackground() {
        return background;
    }

    @Override
    public Stroke getStroke() {
        return stroke;
    }

    @Override
    public void clip(Shape s) {
        Shape dev = transform.createTransformedShape(s);
        if (clip == null) {
            clip = dev;
        } else {
            Area a = new Area(clip);
            a.intersect(new Area(dev));
            clip = a;
        }
    }

    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public void setColor(Color c) {
        if (c != null) {
            color = c;
            paint = c;
        }
    }

    @Override
    public void setPaintMode() {
        composite = AlphaComposite.SrcOver;
    }

    @Override
    public void setXORMode(Color c) {
    }

    @Override
    public Font getFont() {
        return font;
    }

    @Override
    public void setFont(Font f) {
        if (f != null) {
            font = f;
        }
    }

    @Override
    public Rectangle getClipBounds() {
        if (clip == null) {
            return null;
        }
        try {
            return transform.createInverse().createTransformedShape(clip).getBounds();
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return null;
        }
    }

    @Override
    public void clipRect(int x, int y, int w, int h) {
        clip(new Rectangle(x, y, w, h));
    }

    @Override
    public void setClip(int x, int y, int w, int h) {
        setClip(new Rectangle(x, y, w, h));
    }

    @Override
    public Shape getClip() {
        if (clip == null) {
            return null;
        }
        try {
            return transform.createInverse().createTransformedShape(clip);
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return null;
        }
    }

    @Override
    public void setClip(Shape s) {
        clip = s == null ? null : transform.createTransformedShape(s);
    }

    @Override
    public void copyArea(int x, int y, int w, int h, int dx, int dy) {
    }

    /** 모은 도형들이 차지하는 장치 좌표 범위(테스트). */
    Rectangle2D extent() {
        Rectangle2D r = null;
        for (Object o : sink.items) {
            Rectangle2D b = o instanceof Fill ? ((Fill) o).shape.getBounds2D()
                    : ((Picture) o).at.createTransformedShape(new Rectangle(((Picture) o).image.getWidth(),
                            ((Picture) o).image.getHeight())).getBounds2D();
            r = r == null ? b : r.createUnion(b);
        }
        return r;
    }
}
