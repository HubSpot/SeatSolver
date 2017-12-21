package com.hubspot.seatsolver.grid;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.hubspot.seatsolver.model.Direction;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.PointBase;
import com.hubspot.seatsolver.model.Seat;

public class SeatGrid {

  private static final int GRID_SIZE_X = 6;
  private static final int GRID_SIZE_Y = 6;

  private final HashMap<Double, HashMap<Double, Seat>> seatGrid;

  public SeatGrid(HashMap<Double, HashMap<Double, Seat>> seatGrid) {
    this.seatGrid = seatGrid;
  }

  public SeatGrid(List<Seat> seats) {
    this.seatGrid = new HashMap<>();

    for (Seat seat : seats) {
      HashMap<Double, Seat> col = seatGrid.getOrDefault(seat.x(), new HashMap<>());
      col.put(seat.y(), seat);

      seatGrid.put(seat.x(), col);
    }
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

    if (x < 0 || y < 0 || x > GRID_SIZE_X || y > GRID_SIZE_Y) {
      return Optional.empty();
    }

    Seat curSeat = seatGrid.getOrDefault(x, new HashMap<>()).get(y);
    if (curSeat == null) {
      return findAdjacent(Point.builder().x(x).y(y).build(), direction);
    }

    return Optional.of(curSeat);
  }

  public Optional<Seat> get(int x, int y) {
    return Optional.ofNullable(seatGrid.getOrDefault(x, new HashMap<>()).get(y));
  }
}
