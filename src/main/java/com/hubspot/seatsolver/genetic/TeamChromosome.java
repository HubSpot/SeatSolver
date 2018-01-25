package com.hubspot.seatsolver.genetic;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicDouble;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.PointBase;
import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.model.TeamCore;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.RandomRegistry;

public class TeamChromosome extends AbstractSeatChromosome {
  private static final Logger LOG = LoggerFactory.getLogger(TeamChromosome.class);

  private final SeatGrid seatGrid;
  private final ISeq<SeatCore> allSeats;
  private final Set<SeatCore> allSeatsSet;
  private final TeamCore team;

  private AtomicReference<Point> centroid = new AtomicReference<>(null);
  private AtomicDouble meanWeightedSeatDist = new AtomicDouble(-1);

  public TeamChromosome(SeatGrid seatGrid,
                        ISeq<SeatCore> allSeats,
                        Set<SeatCore> allSeatsSet,
                        List<SeatCore> selectedSeats,
                        TeamCore team) {
    super(generateSeq(allSeats, selectedSeats));
    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
    this.allSeatsSet = allSeatsSet;
    this.team = team;
  }

  public TeamChromosome(ISeq<? extends EnumGene<SeatCore>> genes, SeatGrid seatGrid, ISeq<SeatCore> allSeats, TeamCore team) {
    super(genes);
    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
    this.allSeatsSet = ImmutableSet.copyOf(allSeats);
    this.team = team;
  }

  private static ISeq<EnumGene<SeatCore>> generateSeq(ISeq<SeatCore> allSeats,
                                                    List<SeatCore> selectedSeats) {
    MSeq<EnumGene<SeatCore>> result = MSeq.ofLength(selectedSeats.size());
    ISeq<SeatCore> allSeatsSeq = ISeq.of(allSeats);
    for (int i = 0; i < selectedSeats.size(); ++i) {
      SeatCore seat = selectedSeats.get(i);
      result.set(i, EnumGene.of(allSeats.indexOf(seat), allSeatsSeq));
    }
    return result.toISeq();
  }

  public TeamCore getTeam() {
    return team;
  }

  public SeatGrid getSeatGrid() {
    return seatGrid;
  }

  public double meanWeightedSeatDistance() {
    Double dist = meanWeightedSeatDist.get();
    if (dist < 0) {
      dist = calculateMeanWeightedSeatDistance();
      meanWeightedSeatDist.set(dist);
    }

    return dist;
  }

  private double calculateMeanWeightedSeatDistance() {
    // mean of pairwise distances
    List<SeatCore> seats = toSeq().stream()
        .map(EnumGene::getAllele)
        .collect(Collectors.toList());

    double totalDist = 0;
    int pairs = 0;
    for (SeatCore seat : seats) {
      for (SeatCore other : seats) {
        if (seat == other) {
          continue;
        }

        double dist = PointUtils.distance(seat, other);
        totalDist += dist;
        pairs++;
      }
    }

    if (pairs == 0) {
      return 0;
    }

    return totalDist / ((double) pairs);
  }

  public Point centroid() {
    Point c = centroid.get();
    if (c == null) {
      c = centroid(toSeq().stream().map(EnumGene::getAllele).iterator());
      centroid.set(c);
    }
    return c;
  }

  private List<SeatCore> selectSeatBlock(ISeq<SeatCore> availableSeats) {
    return selectSeatBlock(seatGrid, availableSeats, length());
  }

  @Override
  public AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<SeatCore>> genes) {
    return new TeamChromosome(genes, seatGrid, allSeats, team);
  }

  public TeamChromosome newTeamChromosome(ISeq<SeatCore> available) {
    return new TeamChromosome(seatGrid, allSeats, allSeatsSet, selectSeatBlock(available), team);
  }

  @Override
  public Chromosome<EnumGene<SeatCore>> newInstance(ISeq<EnumGene<SeatCore>> genes) {
    return newSeatChromosome(genes);
  }

  @Override
  public Chromosome<EnumGene<SeatCore>> newInstance() {
    List<SeatCore> selected = selectSeatBlock(seatGrid, allSeats, allSeatsSet, length());
    return new TeamChromosome(seatGrid, allSeats, allSeatsSet, selected, team);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("team", team)
        .add("_genes", _genes)
        .add("_valid", _valid)
        .toString();
  }


  private static final int MAX_SEAT_ATTEMPTS = 100;
  private static final int MAX_BLOCK_ATTEMPTS = 100;
  private static final int MAX_FILL_ATTEMPTS = 100;

  public static List<SeatCore> selectSeatBlock(SeatGrid grid, ISeq<SeatCore> availableSeats, int size) {
    return selectSeatBlock(grid, availableSeats, Sets.newHashSet(availableSeats), size);
  }

  public static List<SeatCore> selectSeatBlock(SeatGrid grid, ISeq<SeatCore> availableSeats, Set<SeatCore> availableSeatSet, int size) {
    List<SeatCore> selected = selectBlock(grid, availableSeats, availableSeatSet, size);

    Set<SeatCore> selectedSet = new HashSet<>(selected);
    if (selected.size() < size) {
      LOG.debug("Could not find enough adjacent seats for team of size {}", size);
      // fill with random seats now, this will not be valid
      int fillAttempts = 0;
      while (selected.size() < size && fillAttempts < MAX_FILL_ATTEMPTS) {
        fillAttempts++;

        int originSeatIdx = RandomRegistry.getRandom().nextInt(selected.size());
        SeatCore randomSeatCore = availableSeats.get(originSeatIdx);
        if (selectedSet.contains(randomSeatCore)) {
          continue;
        }

        selected.add(randomSeatCore);
        selectedSet.add(randomSeatCore);
      }

      if (selected.size() < size) {
        LOG.debug("Failed to create full seat block for {} seats", size);
      }
    }

    return selected;
  }

  private static List<SeatCore> selectBlock(SeatGrid grid, ISeq<SeatCore> availableSeatList, Set<SeatCore> availableSeatSet, int size) {
    Set<SeatCore> lastSelected = new HashSet<>();
    for (int y = 0; y < MAX_BLOCK_ATTEMPTS; y++) {
      // pick a random starting point
      int startSeatIdx = RandomRegistry.getRandom().nextInt(availableSeatList.size());
      SeatCore seat = availableSeatList.get(startSeatIdx);

      Set<SeatCore> selected = Sets.newHashSet(seat);
      for (int x = 0; x < MAX_SEAT_ATTEMPTS; x++) {
        if (selected.size() == size) {
          break;
        }

        Optional<SeatCore> nextSeatCore = selectAdjacent(selected, availableSeatSet, grid);
        if (!nextSeatCore.isPresent()) {
          break;
        }

        selected.add(nextSeatCore.get());
      }

      if (selected.size() == size) {
        return Lists.newArrayList(selected);
      }

      lastSelected = selected;
    }

    return Lists.newArrayList(lastSelected);
  }

  public static Optional<SeatCore> selectAdjacent(Set<SeatCore> existing, Set<SeatCore> available, SeatGrid grid) {
    Set<SeatCore> allAdjacent = existing.stream()
        .flatMap(seat -> grid.getAdjacent(seat).stream())
        .collect(Collectors.toSet());

    Set<SeatCore> newAdjacent = Sets.difference(allAdjacent, existing);

    Set<SeatCore> availableAdjacent = Sets.intersection(newAdjacent, available);
    if (availableAdjacent.isEmpty()) {
      return Optional.empty();
    }

    Point center = centroid(existing.iterator());
    double minDistance = Double.MAX_VALUE;
    SeatCore nearestSeat = null;
    for (SeatCore seat : availableAdjacent) {
      double myDistance = PointUtils.distance(seat, center);
      if (myDistance < minDistance) {
        nearestSeat = seat;
      }
    }
    return Optional.ofNullable(nearestSeat);
  }

  @Override
  public String getIdentifier() {
    return team.id();
  }

  private static Point centroid(Iterator<? extends PointBase> points) {
    double sumX = 0;
    double sumY = 0;

    int count = 0;

    while (points.hasNext()) {
      PointBase point = points.next();
        sumX += point.x();
        sumY += point.y();
        ++count;
    }

    double x = sumX / count;
    double y = sumY / count;

    return Point.builder().x(x).y(y).build();
  }

}
