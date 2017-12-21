package com.hubspot.seatsolver.model;

import io.jenetics.util.RandomRegistry;

public enum Direction {
  NORTH,
  EAST,
  SOUTH,
  WEST,
  ;

  private static final int NUM_DIRECTIONS = 4;

  public static Direction randomDirection() {
    int dir = RandomRegistry.getRandom().nextInt(NUM_DIRECTIONS);
    return Direction.values()[dir];
  }
}
