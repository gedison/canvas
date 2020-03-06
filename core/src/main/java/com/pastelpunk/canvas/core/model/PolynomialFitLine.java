package com.pastelpunk.canvas.core.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
public class PolynomialFitLine extends Entity {
    private String color;
    private Point start;
    private Point end;
    private int degree;
    private double[] coefficients;
    private List<Point> searchCoordinates;


}
