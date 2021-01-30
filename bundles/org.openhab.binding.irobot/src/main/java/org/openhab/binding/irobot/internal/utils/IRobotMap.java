/**
 * Copyright (c) 2021- Alexander Falkenstern
 *
 * License: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.openhab.binding.irobot.internal.utils;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class IRobotMap extends BufferedImage {
    private final ArrayList<Point2D> points = new ArrayList<>();

    private final ConcaveHull hull = new ConcaveHull();
    private final Rectangle2D bounds = new Rectangle2D.Double();

    private final AffineTransform scale = AffineTransform.getScaleInstance(0.9, 0.9);

    private Path2D toPath(final List<Point2D> points) {
        Path2D result = new Path2D.Double(Path2D.WIND_NON_ZERO, points.size());
        result.moveTo(points.get(0).getX(), points.get(0).getY());
        for (int i = 1; i < points.size(); i++) {
            final Point2D p0 = points.get(i - 1);
            final Point2D p1 = points.get(i - 0);
            result.quadTo(p0.getX(), p0.getY(), p1.getX(), p1.getY());
        }
        result.closePath();
        return result;
    }

    public IRobotMap() {
        this(800, 800);
    }

    public IRobotMap(int width, int height) {
        super(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    public void add(double x, double y) {
        final Point2D point = scale.transform(new Point2D.Double(x, y), null);
        if (points.add(point)) {
            bounds.add(point);
        }
    }

    public void add(final Point2D.Double point) {
        this.add(point.getX(), point.getY());
    }

    public void clear() {
        points.clear();
        bounds.setFrame(0.0, 0.0, 0.0, 0.0);
    }

    public ArrayList<Point2D> getPoints() {
        return points;
    }

    public void generate() {
        Graphics2D graphics = createGraphics();
        graphics.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        AffineTransform offset = new AffineTransform();
        offset.translate(getWidth() / 2.0 - bounds.getCenterX(), getHeight() / 2.0 - bounds.getCenterY());

        graphics.setColor(Color.GREEN);
        Path2D cHull = toPath(hull.calculate(points, 9));
        graphics.fill(offset.createTransformedShape(cHull));

        graphics.setColor(Color.BLUE);
        graphics.draw(offset.createTransformedShape(toPath(points)));

        graphics.setColor(Color.RED);
        Ellipse2D point = new Ellipse2D.Double();
        for (int i = 0; i < points.size(); i++) {
            final Point2D p0 = points.get(i);
            point.setFrame(p0.getX() - 3, p0.getY() - 3, 3, 3);
            graphics.fill(offset.createTransformedShape(point));
        }

        graphics.dispose();
    }
}
