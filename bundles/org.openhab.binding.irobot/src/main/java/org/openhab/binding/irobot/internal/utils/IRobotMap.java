/**
 * Copyright (c) 2021- Alexander Falkenstern
 *
 * License: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.openhab.binding.irobot.internal.utils;

import static org.opencv.core.CvType.*;
import static org.opencv.imgproc.Imgproc.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import uk.osgb.algorithm.concavehull.ConcaveHull;
import uk.osgb.algorithm.concavehull.TriCheckerChi;

@NonNullByDefault
public class IRobotMap extends BufferedImage {
    private final ArrayList<Point2D> points = new ArrayList<>();

    private final GeometryFactory factory = new GeometryFactory();
    private final Rectangle2D bounds = new Rectangle2D.Double();
    private final AffineTransform scale = AffineTransform.getScaleInstance(0.9, 0.9);

    private List<Coordinate> getOuterContour() {
        AffineTransform offset = new AffineTransform();
        offset.translate(getWidth() / 2.0 - bounds.getCenterX(), getHeight() / 2.0 - bounds.getCenterY());

        BufferedImage buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = buffer.createGraphics();

        graphics.setBackground(Color.BLACK);
        graphics.clearRect(0, 0, buffer.getWidth(), buffer.getHeight());

        graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        QuadCurve2D parabola = new QuadCurve2D.Double();
        for (int i = 1; i < points.size() - 1; i++) {
            parabola.setCurve(points.get(i - 1), points.get(i), points.get(i + 1));
            graphics.draw(offset.createTransformedShape(parabola));
        }
        graphics.dispose();

        Mat image = new Mat(buffer.getHeight(), buffer.getWidth(), CV_8UC1);
        DataBuffer data = buffer.getRaster().getDataBuffer();
        image.put(0, 0, ((DataBufferByte) data).getData());

        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(image, contours, hierarchy, RETR_TREE, CHAIN_APPROX_NONE);

        List<Coordinate> coordinates = new ArrayList<>();
        for (int index = 0; index < hierarchy.cols(); index++) {
            int[] topology = new int[4];
            hierarchy.get(0, index, topology);

            // topology[{ 0,1,2,3 }] =
            // { next contour (same level), previous contour (same level), child contour, parent contour }

            // There are no parents for this contour -> outer contour -> Add coordinates
            if ((topology[3] == -1) && (index < contours.size())) {
                for (final Point point : contours.get(index).toArray()) {
                    coordinates.add(new Coordinate(point.x, point.y));
                }
            }
        }

        return coordinates;
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
        AffineTransform offset = new AffineTransform();
        offset.translate(getWidth() / 2.0 - bounds.getCenterX(), getHeight() / 2.0 - bounds.getCenterY());

        final Coordinate[] coordinates = getOuterContour().toArray(new Coordinate[0]);
        final ConcaveHull hullBuilder = new ConcaveHull(factory.createMultiPointFromCoords(coordinates));

        final TriCheckerChi triChecker = new TriCheckerChi(20.0); // Pixel distance to reject
        final Collection<Geometry> hulls = hullBuilder.getConcaveHullBFS(triChecker, false, false);

        Graphics2D graphics = createGraphics();
        graphics.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        graphics.setColor(Color.GREEN);
        ShapeWriter writer = new ShapeWriter();
        for (final Geometry hull : hulls) {
            graphics.fill(writer.toShape(hull));
        }

        graphics.setColor(Color.BLUE);
        QuadCurve2D parabola = new QuadCurve2D.Double();
        for (int i = 1; i < points.size() - 1; i++) {
            parabola.setCurve(points.get(i - 1), points.get(i), points.get(i + 1));
            graphics.draw(offset.createTransformedShape(parabola));
        }

        graphics.setColor(Color.RED);
        Ellipse2D point = new Ellipse2D.Double();
        for (int i = 0; i < points.size(); i++) {
            final Point2D p = points.get(i);
            point.setFrame(p.getX() - 1, p.getY() - 1, 3, 3);
            graphics.fill(offset.createTransformedShape(point));
        }
        graphics.dispose();

        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight() / 2; y++) {
                int temporary = getRGB(x, y);
                setRGB(x, y, getRGB(x, getHeight() - y - 1));
                setRGB(x, getHeight() - y - 1, temporary);
            }
        }
    }
}
