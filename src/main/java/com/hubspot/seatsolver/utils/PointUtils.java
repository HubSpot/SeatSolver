package com.hubspot.seatsolver.utils;

import com.hubspot.seatsolver.model.PointBase;

public final class PointUtils {

  private PointUtils() { }

  public static double distance(PointBase a, PointBase b) {
    // Good ol Pythagorean Theorem
    double xDist = Math.pow(Math.abs(a.x() - b.x()), 2);
    double yDist = Math.pow(Math.abs(a.y() - b.y()), 2);

    double dist = Math.sqrt(xDist + yDist);
    return dist;
  }
}
