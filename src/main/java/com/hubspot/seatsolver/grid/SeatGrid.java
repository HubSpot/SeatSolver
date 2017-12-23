package com.hubspot.seatsolver.grid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.varunpant.quadtree.QuadTree;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.hubspot.seatsolver.model.Seat;

public class SeatGrid {
  private static final int MAX_ADJ_OFFSET = 35;

  private final HashMap<Double, HashMap<Double, Seat>> seatGrid;
  private final QuadTree<Seat> seatQuadTree;
  private final SetMultimap<Seat, Seat> adjacencyMap;
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

    this.gridSizeX = maxX;
    this.gridSizeY = maxY;

    this.seatQuadTree = new QuadTree<>(0, 0 , maxX, maxY);
    seats.forEach(seat -> seatQuadTree.set(seat.x(), seat.y(), seat));

    this.adjacencyMap = HashMultimap.create();
    seats.stream().forEach(seat -> {
      Set<Seat> adj = findAllAdjacent(seat);
      adjacencyMap.putAll(seat, adj);
    });
  }

  public boolean isAdjacent(Seat first, Seat second) {
    Set<Seat> adjacent = getAdjacent(first);

    return adjacent.contains(second);
  }

  public Set<Seat> getAdjacent(Seat seat) {
    return adjacencyMap.get(seat);
  }

  public Set<Seat> findAllAdjacent(Seat seat) {
    double xMin = seat.x() - MAX_ADJ_OFFSET;
    double yMin = seat.y() - MAX_ADJ_OFFSET;
    double xMax = seat.x() + MAX_ADJ_OFFSET;
    double yMax = seat.y() + MAX_ADJ_OFFSET;

    if (xMin < 0) {
      xMin = 0;
    }

    if (yMin < 0) {
      yMin = 0;
    }

    if (xMax > gridSizeX) {
      xMax = gridSizeX;
    }

    if (yMax > gridSizeY) {
      yMax = gridSizeY;
    }

    com.github.varunpant.quadtree.Point<Seat>[] points = seatQuadTree.searchWithin(xMin, yMin, xMax, yMax);

    Set<Seat> result = new HashSet<>();
    for (com.github.varunpant.quadtree.Point<Seat> point : points) {
      result.add(point.getValue());
    }

    return result;
  }
}
