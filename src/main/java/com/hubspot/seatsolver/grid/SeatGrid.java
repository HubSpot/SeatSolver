package com.hubspot.seatsolver.grid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.varunpant.quadtree.QuadTree;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.PointBase;
import com.hubspot.seatsolver.model.SeatCore;

@Singleton
public class SeatGrid {
  private static final int MAX_ADJ_OFFSET = 60;
  private static final int SEAT_WIDTH = 12;
  private static final int SEAT_HEIGHT = 14;

  private final QuadTree<SeatCore> seatQuadTree;
  private final SetMultimap<SeatCore, SeatCore> adjacencyMap;
  private final double gridSizeX;
  private final double gridSizeY;
  private final int size;

  @Inject
  public SeatGrid(List<? extends SeatCore> seats) {
    this.size = seats.size();
    double maxX = 0;
    double maxY = 0;
    for (SeatCore seat : seats) {
      if (seat.x() > maxX) {
        maxX = seat.x();
      }

      if (seat.y() > maxY) {
        maxY = seat.y();
      }
    }

    this.gridSizeX = maxX;
    this.gridSizeY = maxY;

    this.seatQuadTree = new QuadTree<>(0, 0, maxX, maxY);
    seats.forEach(seat -> seatQuadTree.set(seat.x(), seat.y(), seat));

    HashMultimap<SeatCore, SeatCore> adjMap = HashMultimap.create();
    seats.forEach(seat -> {
      Set<SeatCore> adj = findAllAdjacent(seat);
      adjMap.putAll(seat, adj);
    });

    this.adjacencyMap = ImmutableSetMultimap.copyOf(adjMap);
  }

  public int size() {
    return size;
  }

  public Set<SeatCore> getAdjacent(SeatCore seat) {
    return adjacencyMap.get(seat);
  }

  private Set<SeatCore> findAllAdjacent(SeatCore seat) {
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

    com.github.varunpant.quadtree.Point<SeatCore>[] points = seatQuadTree.searchWithin(xMin, yMin, xMax, yMax);

    Set<SeatCore> neighbors = new HashSet<>();
    for (com.github.varunpant.quadtree.Point<SeatCore> point : points) {
      if (point.getValue() == seat) {
        continue;
      }

      neighbors.add(point.getValue());
    }

    Set<SeatCore> result = neighbors.stream()
        .filter(neighbor -> !isOccluded(seat, neighbor, neighbors))
        .collect(Collectors.toSet());

    return result;
  }

  private boolean isOccluded(SeatCore from, SeatCore to, Collection<SeatCore> neighbors) {
    List<Point> line = bresenhamLine(from, to);

    return neighbors.stream()
        .filter(seat -> seat != to)
        .anyMatch(seat -> {
          // Calculate the bounding points of the rect
          // Any points with (x,y) > (x0, y0) && (x,y) < (x1, y1) are in the rect
          // And thus these points occlude the target

          double x0 = seat.x() - (SEAT_WIDTH / 2);
          double y0 = seat.y() - (SEAT_HEIGHT / 2);
          double x1 = seat.x() + (SEAT_WIDTH / 2);
          double y1 = seat.y() + (SEAT_HEIGHT / 2);

          return line.stream().anyMatch(point -> {
            return point.x() > x0 && point.y() > y0 && point.x() < x1 && point.y() < y1;
          });
        });
  }

  private List<Point> bresenhamLine(PointBase from, PointBase to) {
    double x0 = from.x();
    double y0 = from.y();
    double x1 = to.x();
    double y1 = to.y();

    List<Point> line = new ArrayList<>();

    double dx = Math.abs(x1 - x0);
    double dy = Math.abs(y1 - y0);

    int sx = x0 < x1 ? 1 : -1;
    int sy = y0 < y1 ? 1 : -1;

    int err = ((int) (dx - dy));
    int e2;

    while (true) {
      line.add(Point.builder().x(x0).y(y0).build());

      if (x0 == x1 && y0 == y1) {
        break;
      }

      e2 = 2 * err;
      if (e2 > -dy) {
        err = ((int) (err - dy));
        x0 = x0 + sx;
      }

      if (e2 < dx) {
        err = ((int) (err + dx));
        y0 = y0 + sy;
      }
    }

    return line;
  }
}
