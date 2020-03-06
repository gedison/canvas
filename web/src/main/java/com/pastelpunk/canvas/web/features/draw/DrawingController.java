package com.pastelpunk.canvas.web.features.draw;

import com.google.common.collect.Iterables;
import com.pastelpunk.canvas.core.model.Line;
import com.pastelpunk.canvas.core.model.Point;
import com.pastelpunk.canvas.core.model.PolynomialFitLine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.core.util.UuidUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;

import javax.servlet.http.HttpSession;
import java.util.*;

@Slf4j
@CrossOrigin
@RestController()
public class DrawingController {

    private final int degree = 2;
    private final PolynomialCurveFitter polynomialFitter3rdDegree = PolynomialCurveFitter.create(degree);

    private List<PolynomialFitLine> polynomialFitLineList = new ArrayList<>();

    public DrawingController() {
    }

    @RequestMapping(value = "api/v1/draw/reset", method = RequestMethod.GET)
    public ResponseEntity<String> reset() {
        polynomialFitLineList = new ArrayList<>();
        return ResponseEntity.ok("Reset");
    }

    @RequestMapping(value = "api/v1/draw", method = RequestMethod.GET)
    public ResponseEntity<Map<String, List<Point>>> getResponse() {


        Map<String, List<Point>> allPoints = new HashMap<>(polynomialFitLineList.size());

        for (PolynomialFitLine polynomialFitLine : polynomialFitLineList) {
            List<Point> points = new ArrayList<>();

            if (polynomialFitLine.getStart().getX() > polynomialFitLine.getEnd().getX()) {
                Point tmp = polynomialFitLine.getStart();
                polynomialFitLine.setStart(polynomialFitLine.getEnd());
                polynomialFitLine.setEnd(tmp);
            }


            for (double x = polynomialFitLine.getStart().getX(); x < polynomialFitLine.getEnd().getX(); x += 10) {
                double[] coefficients = polynomialFitLine.getCoefficients();
                int degree = polynomialFitLine.getDegree();

                double y = coefficients[0];
                for (int i = 1; i <= degree; i++) {
                    y += coefficients[i] * Math.pow(x, i);
                }

                points.add(new Point(x, y));
            }

            points.add(0, polynomialFitLine.getStart());
            points.add(polynomialFitLine.getEnd());
            allPoints.put(polynomialFitLine.getId(), points);
        }

        return ResponseEntity.ok(allPoints);
    }

    @RequestMapping(value = "api/v1/draw", method = RequestMethod.POST)
    public void draw(@RequestBody Line line) {

        var points = line.getPoints();

        List<List<Point>> partitionedPointList = new ArrayList<>();


        var previousPoint = points.get(0);
        var nextPoint = points.get(1);

        List<Point> list = new ArrayList<>();
        list.add(previousPoint);

        boolean xDirection = compareX(previousPoint, nextPoint, true);
        boolean yDirection = compareY(previousPoint, nextPoint, true);
        int directionChange = 0;
        for (int i = 1; i < points.size(); i++) {
            var point = points.get(i);

            if(!compareY(previousPoint, point, yDirection)) {
                directionChange++;
                yDirection = !yDirection;
            }

            if (compareX(previousPoint, point, xDirection) && directionChange < degree) {
                list.add(point);
            } else {
                xDirection = !xDirection;
                directionChange = 0;
                partitionedPointList.add(new ArrayList<>(list));
                list = new ArrayList<>();
                list.add(previousPoint);
                list.add(point);
            }

            previousPoint = point;
        }

        partitionedPointList.add(list);

        for (List<Point> partitionedPoints : partitionedPointList) {
            List<Point> fittedPartitionedPoints = new ArrayList<>();
            ramerDouglasPeucker(partitionedPoints, .05, fittedPartitionedPoints);

            WeightedObservedPoints observations = new WeightedObservedPoints();
            for (Point point : partitionedPoints) {
                observations.add(point.getX(), point.getY());
            }

            double[] mCoefficients = polynomialFitter3rdDegree.fit(observations.toList());
            polynomialFitLineList.add(generatePolynomialFitLine(partitionedPoints, mCoefficients));
        }
    }

    private static boolean compareY(Point a, Point b, boolean isIncreasing) {
        if (isIncreasing) {
            return (a.getY() -1.5) <= b.getY();
        } else {
            return (b.getY()-1.5) < a.getY();
        }
    }

    private static boolean compareX(Point a, Point b, boolean isIncreasing) {
        if (isIncreasing) {
            return (a.getX() - .5) <= b.getX();
        } else {
            return (b.getX() - .5) < a.getX();
        }
    }

    private static PolynomialFitLine generatePolynomialFitLine(List<Point> points, double[] coefficients) {
        var polynomialFitLine = new PolynomialFitLine();
        polynomialFitLine.setId(UuidUtil.getTimeBasedUuid().toString());
        polynomialFitLine.setColor("#000");
        polynomialFitLine.setStart(points.get(0));
        polynomialFitLine.setEnd(Iterables.getLast(points));
        polynomialFitLine.setDegree(2);
        polynomialFitLine.setCoefficients(coefficients);
        return polynomialFitLine;
    }

    public static boolean between(double i, double minValueInclusive, double maxValueInclusive) {
        return (i >= minValueInclusive && i <= maxValueInclusive);
    }


    private double perpendicularDistance(Point pt, Point lineStart, Point lineEnd) {
        double dx = lineEnd.getX() - lineStart.getX();
        double dy = lineEnd.getY() - lineStart.getY();

        // Normalize
        double mag = Math.hypot(dx, dy);
        if (mag > 0.0) {
            dx /= mag;
            dy /= mag;
        }
        double pvx = pt.getX() - lineStart.getX();
        double pvy = pt.getY() - lineStart.getY();

// Get dot product (project pv onto normalized direction)
        double pvdot = dx * pvx + dy * pvy;

// Scale line direction vector and subtract it from pv
        double ax = pvx - pvdot * dx;
        double ay = pvy - pvdot * dy;

        return Math.hypot(ax, ay);
    }

    private void ramerDouglasPeucker(List<Point> pointList, double epsilon, List<Point> out) {
        if (pointList.size() < 2){
            out = pointList;
            return;
        }

        // Find the point with the maximum distance from line between the start and end
        double dmax = 0.0;
        int index = 0;
        int end = pointList.size() - 1;
        for (int i = 1; i < end; ++i) {
            double d = perpendicularDistance(pointList.get(i), pointList.get(0), pointList.get(end));
            if (d > dmax) {
                index = i;
                dmax = d;
            }
        }

        // If max distance is greater than epsilon, recursively simplify
        if (dmax > epsilon) {
            List<Point> recResults1 = new ArrayList<>();
            List<Point> recResults2 = new ArrayList<>();
            List<Point> firstLine = pointList.subList(0, index + 1);
            List<Point> lastLine = pointList.subList(index, pointList.size());
            ramerDouglasPeucker(firstLine, epsilon, recResults1);
            ramerDouglasPeucker(lastLine, epsilon, recResults2);

            // build the result list
            out.addAll(recResults1.subList(0, recResults1.size() - 1));
            out.addAll(recResults2);
            if (out.size() < 2){
                out = pointList;
            }
        } else {
            // Just return start and end points
            out.clear();
            out.add(pointList.get(0));
            out.add(Iterables.getLast(pointList));
        }
    }

}
