package com.chronos.dto;

import java.util.List;

public record ForecastResponse(List<Point> history, List<Point> forecast) {
    public record Point(String period, Double value, Double lowerBound, Double upperBound) {}
}