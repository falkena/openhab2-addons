/**
 * ConcaveHull.java - 14/10/16
 *
 * @author Udo Schlegel - Udo.3.Schlegel(at)uni-konstanz.de
 * @version 1.0
 *
 * This is an implementation of the algorithm described by Adriano Moreira and Maribel Yasmina Santos:
 * CONCAVE HULL: A K-NEAREST NEIGHBOURS APPROACH FOR THE COMPUTATION OF THE REGION OCCUPIED BY A SET OF POINTS.
 * GRAPP 2007 - International Conference on Computer Graphics Theory and Applications; pp 61-68.
 *
 * https://repositorium.sdum.uminho.pt/bitstream/1822/6429/1/ConcaveHull_ACM_MYS.pdf
 *
 * With help from https://github.com/detlevn/QGIS-ConcaveHull-Plugin/blob/master/concavehull.py
 */
package org.openhab.binding.irobot.internal.utils;

import java.awt.geom.Point2D;
import java.util.*;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class ConcaveHull {

    private static final double EPSILON = Double.longBitsToDouble((1023l - 53l) << 52);

    private List<Point2D> kNearestNeighbors(ArrayList<Point2D> list, Point2D point, Integer k) {
        Map<Double, Point2D> nearest = new TreeMap<>();
        list.forEach(neighbour -> {
            nearest.put(point.distance(neighbour), neighbour);
        });

        int count = 0;
        List<Point2D> result = new ArrayList<>();
        for (Map.Entry<Double, Point2D> entry : nearest.entrySet()) {
            if (count >= Math.min(k, nearest.size())) {
                break;
            }
            result.add(entry.getValue());
            count++;
        }

        return result;
    }

    private double calculateAngle(Point2D p1, Point2D p2) {
        return Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX());
    }

    private double angleDifference(double a1, double a2) {
        // calculate angle difference in clockwise directions as radians
        if ((a1 > 0 && a2 >= 0) && a1 > a2) {
            return Math.abs(a1 - a2);
        } else if ((a1 >= 0 && a2 > 0) && a1 < a2) {
            return 2 * Math.PI + a1 - a2;
        } else if ((a1 < 0 && a2 <= 0) && a1 < a2) {
            return 2 * Math.PI + a1 + Math.abs(a2);
        } else if ((a1 <= 0 && a2 < 0) && a1 > a2) {
            return Math.abs(a1 - a2);
        } else if (a1 <= 0 && 0 < a2) {
            return 2 * Math.PI + a1 - a2;
        } else if (a1 >= 0 && 0 >= a2) {
            return a1 + Math.abs(a2);
        } else {
            return 0.0;
        }
    }

    private List<Point2D> sortByAngle(List<Point2D> list, Point2D q, double a) {
        // Sort by angle descending
        Collections.sort(list, new Comparator<>() {
            @Override
            public int compare(final Point2D p1, final Point2D p2) {
                Double a1 = angleDifference(a, calculateAngle(q, p1));
                Double a2 = angleDifference(a, calculateAngle(q, p2));
                return a2.compareTo(a1);
            }
        });
        return list;
    }

    private Boolean intersect(Point2D l1p1, Point2D l1p2, Point2D l2p1, Point2D l2p2) {
        // calculate part equations for line-line intersection
        double a1 = l1p2.getY() - l1p1.getY();
        double b1 = l1p1.getX() - l1p2.getX();
        double c1 = a1 * l1p1.getX() + b1 * l1p1.getY();
        double a2 = l2p2.getY() - l2p1.getY();
        double b2 = l2p1.getX() - l2p2.getX();
        double c2 = a2 * l2p1.getX() + b2 * l2p1.getY();

        // calculate the divisor
        double tmp = (a1 * b2 - a2 * b1);

        // calculate intersection point x coordinate
        double pX = (c1 * b2 - c2 * b1) / tmp;

        // check if intersection x coordinate lies in line line segment
        if ((pX > l1p1.getX() && pX > l1p2.getX()) || (pX > l2p1.getX() && pX > l2p2.getX())
                || (pX < l1p1.getX() && pX < l1p2.getX()) || (pX < l2p1.getX() && pX < l2p2.getX())) {
            return false;
        }

        // calculate intersection point y coordinate
        double pY = (a1 * c2 - a2 * c1) / tmp;

        // check if intersection y coordinate lies in line line segment
        if ((pY > l1p1.getY() && pY > l1p2.getY()) || (pY > l2p1.getY() && pY > l2p2.getY())
                || (pY < l1p1.getY() && pY < l1p2.getY()) || (pY < l2p1.getY() && pY < l2p2.getY())) {
            return false;
        }

        return true;
    }

    private boolean pointInPolygon(Point2D p, ArrayList<Point2D> pp) {
        boolean result = false;
        for (int i = 0, j = pp.size() - 1; i < pp.size(); j = i++) {
            final Point2D p0 = pp.get(i);
            final Point2D p1 = pp.get(j);
            double k = (p1.getX() - p0.getX()) / (p1.getY() - p0.getY());
            if ((p0.getY() > p.getY()) != (p1.getY() > p.getY())
                    && (p.getX() - p0.getX() < k * (p.getY() - p0.getY()))) {
                result = !result;
            }
        }
        return result;
    }

    public ConcaveHull() {
    }

    public List<Point2D> calculate(List<Point2D> pointArrayList, int k) {

        // the resulting concave hull
        ArrayList<Point2D> concaveHull = new ArrayList<>();

        // optional remove duplicates
        Set<Point2D> set = new TreeSet<>(new Comparator<>() {
            @Override
            public int compare(Point2D p1, Point2D p2) {
                return (p1.distance(p2) < 1000.0 * EPSILON) ? 0 : 1;
            }
        });
        set.addAll(pointArrayList);
        ArrayList<Point2D> pointArraySet = new ArrayList<>(set);

        // return Points if already Concave Hull
        if (pointArraySet.size() < 3) {
            return pointArraySet;
        }

        // k has to be greater than 3 to execute the algorithm
        Integer kk = Math.max(k, 3);

        // make sure that k neighbors can be found
        kk = Math.min(kk, pointArraySet.size() - 1);

        // find first point and remove from point list
        Point2D firstPoint = Collections.min(pointArraySet, Comparator.comparing(p -> p.getY()));
        concaveHull.add(firstPoint);
        Point2D currentPoint = firstPoint;
        pointArraySet.remove(firstPoint);

        Integer step = 2;
        double previousAngle = 0.0;

        while ((currentPoint != firstPoint || step == 2) && !pointArraySet.isEmpty()) {

            // after 3 steps add first point to dataset, otherwise hull cannot be closed
            if (step == 5) {
                pointArraySet.add(firstPoint);
            }

            // get k nearest neighbors of current point
            List<Point2D> kNearestPoints = kNearestNeighbors(pointArraySet, currentPoint, kk);

            // sort points by angle clockwise
            List<Point2D> clockwisePoints = sortByAngle(kNearestPoints, currentPoint, previousAngle);

            // check if clockwise angle nearest neighbors are candidates for concave hull
            int i = -1;
            boolean its = true;
            while (its && i < clockwisePoints.size() - 1) {
                i++;

                int lastPoint = 0;
                if (clockwisePoints.get(i) == firstPoint) {
                    lastPoint = 1;
                }

                // check if possible new concave hull point intersects with others
                int j = 2;
                its = false;
                while (!its && j < concaveHull.size() - lastPoint) {
                    its = intersect(concaveHull.get(step - 2), clockwisePoints.get(i), concaveHull.get(step - 2 - j),
                            concaveHull.get(step - 1 - j));
                    j++;
                }
            }

            // if there is no candidate increase k - try again
            if (its) {
                return calculate(pointArrayList, k + 1);
            }

            // add candidate to concave hull and remove from dataset
            currentPoint = clockwisePoints.get(i);
            concaveHull.add(currentPoint);
            pointArraySet.remove(currentPoint);

            // calculate last angle of the concave hull line
            previousAngle = calculateAngle(concaveHull.get(step - 1), concaveHull.get(step - 2));

            step++;

        }

        // Check if all points are contained in the concave hull
        boolean insideCheck = true;
        int i = pointArraySet.size() - 1;
        while (insideCheck && i > 0) {
            insideCheck = pointInPolygon(pointArraySet.get(i), concaveHull);
            i--;
        }

        // if not all points inside - try again
        if (!insideCheck) {
            return calculate(pointArrayList, k + 1);
        } else {
            return concaveHull;
        }
    }
}
