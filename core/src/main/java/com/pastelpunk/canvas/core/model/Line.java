package com.pastelpunk.canvas.core.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Line {
    private String color;
    private List<Point> points;
}
