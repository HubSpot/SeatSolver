package com.hubspot.seatsolver.grid;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.hubspot.seatsolver.model.Direction;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.PointBase;
import com.hubspot.seatsolver.model.Seat;

public class SeatGrid {
  private static final int MAX_ADJ_OFFSET = 1000;

  private final HashMap<Double, HashMap<Double, Seat>> seatGrid;
  private final double gridSizeX;
  private final double gridSizeY;

  public SeatGrid(List<Seat> seats) {
    this.seatGrid = new HashMap<>();

    double maxX = 0;
    double maxY = 0;
    for (Seat seat : seats) {
      HashMap<Double, Seat> col = seatGrid.getOrDefault(seat.x(), new HashMap<>());
      col.put(seat.y(), seat);

      seatGrid.put(seat.x(), col);

      if (seat.x() > maxX) {
        maxX = seat.x();
      }

      if (seat.y() > maxY) {
        maxY = seat.y();
      }
    }

    gridSizeX = maxX;
    gridSizeY = maxY;
  }

  public boolean isAdjacent(Seat first, Seat second) {
    for (Direction direction : Direction.values()) {
      Optional<Seat> optionalSeat = findAdjacent(first, direction);
      if (optionalSeat.isPresent() && optionalSeat.get() == second) {
        return true;
      }
    }

    return false;
  }

  public Optional<Seat> findAdjacent(PointBase start, Direction direction) {
    return findAdjacent(start, direction, 0);
  }

  public Optional<Seat> findAdjacent(PointBase start, Direction direction, int offset) {
    if (offset > MAX_ADJ_OFFSET) {
      return Optional.empty();
    }

    double x = start.x();
    double y = start.y();

    switch (direction) {
      case NORTH:
        y++;
        break;
      case EAST:
        x++;
        break;
      case SOUTH:
        y--;
        break;
      case WEST:
        x--;
        break;
    }

    if (x < 0 || y < 0 || x > gridSizeX || y > gridSizeY) {
      return Optional.empty();
    }

    Seat curSeat = seatGrid.getOrDefault(x, new HashMap<>()).get(y);
    if (curSeat == null) {
      return findAdjacent(Point.builder().x(x).y(y).build(), direction, offset + 1);
    }

    return Optional.of(curSeat);
  }

  public Optional<Seat> get(int x, int y) {
    return Optional.ofNullable(seatGrid.getOrDefault(x, new HashMap<>()).get(y));
  }
}
