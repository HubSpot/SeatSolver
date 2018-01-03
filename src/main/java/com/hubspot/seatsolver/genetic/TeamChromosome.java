package com.hubspot.seatsolver.genetic;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.PointBase;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;

public class TeamChromosome extends AbstractSeatChromosome {
  private static final Logger LOG = LoggerFactory.getLogger(TeamChromosome.class);

  private final SeatGrid seatGrid;
  private final ISeq<Seat> allSeats;
  private final Set<Seat> allSeatsSet;
  private final Team team;

  private AtomicReference<Point> centroid = new AtomicReference<>(null);

  public TeamChromosome(SeatGrid seatGrid,
                        ISeq<Seat> allSeats,
                        Set<Seat> allSeatsSet,
                        List<Seat> selectedSeats,
                        Team team) {
    super(ISeq.of(
        selectedSeats.stream()
            .map(seat -> EnumGene.of(allSeats.indexOf(seat), ISeq.of(allSeats)))
            .collect(Collectors.toList())
    ));

    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
    this.allSeatsSet = allSeatsSet;
    this.team = team;
  }

  public TeamChromosome(ISeq<? extends EnumGene<Seat>> genes, SeatGrid seatGrid, ISeq<Seat> allSeats, Team team) {
    super(genes);
    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
    this.allSeatsSet = ImmutableSet.copyOf(allSeats);
    this.team = team;
  }

  public Team getTeam() {
    return team;
  }

  public double meanWeightedSeatDistance() {
    // mean of pairwise distances
    List<Seat> seats = toSeq().stream()
        .map(EnumGene::getAllele)
        .collect(Collectors.toList());

    double totalDist = 0;
    int pairs = 0;
    for (Seat seat : seats) {
      for (Seat other : seats) {
        if (seat == other) {
          continue;
        }

        double dist = PointUtils.distance(seat, other);
        totalDist += Math.pow(dist, 1.7);
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
      c = centroid(toSeq().stream().map(EnumGene::getAllele).collect(Collectors.toSet()));
      centroid.set(c);
    }

    return c;
  }

  private List<Seat> selectSeatBlock(ISeq<Seat> availableSeats) {
    return selectSeatBlock(seatGrid, availableSeats, length());
  }

  @Override
  public AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<Seat>> genes) {
    return new TeamChromosome(genes, seatGrid, allSeats, team);
  }

  public TeamChromosome newTeamChromosome(ISeq<Seat> available) {
    return new TeamChromosome(seatGrid, allSeats, allSeatsSet, selectSeatBlock(available), team);
  }

  @Override
  public Chromosome<EnumGene<Seat>> newInstance(ISeq<EnumGene<Seat>> genes) {
    return newSeatChromosome(genes);
  }

  @Override
  public Chromosome<EnumGene<Seat>> newInstance() {
    List<Seat> selected = selectSeatBlock(seatGrid, allSeats, allSeatsSet, length());
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

  public static List<Seat> selectSeatBlock(SeatGrid grid, ISeq<Seat> availableSeats, int size) {
    return selectSeatBlock(grid, availableSeats, Sets.newHashSet(availableSeats), size);
  }

  public static List<Seat> selectSeatBlock(SeatGrid grid, ISeq<Seat> availableSeats, Set<Seat> availableSeatSet, int size) {
    List<Seat> selected = selectBlock(grid, availableSeats, availableSeatSet, size);

    Set<Seat> selectedSet = new HashSet<>(selected);
    if (selected.size() < size) {
      LOG.debug("Could not find enough adjacent seats for team of size {}", size);
      // fill with random seats now, this will not be valid
      int fillAttempts = 0;
      while (selected.size() < size && fillAttempts < MAX_FILL_ATTEMPTS) {
        fillAttempts++;

        int originSeatIdx = RandomRegistry.getRandom().nextInt(selected.size());
        Seat randomSeat = availableSeats.get(originSeatIdx);
        if (selectedSet.contains(randomSeat)) {
          continue;
        }

        selected.add(randomSeat);
        selectedSet.add(randomSeat);
      }

      if (selected.size() < size) {
        LOG.debug("Failed to create full seat block for {} seats", size);
      }
    }

    return selected;
  }

  private static List<Seat> selectBlock(SeatGrid grid, ISeq<Seat> availableSeatList, Set<Seat> availableSeatSet, int size) {
    Set<Seat> lastSelected = new HashSet<>();
    for (int y = 0; y < MAX_BLOCK_ATTEMPTS; y++) {
      // pick a random starting point
      int startSeatIdx = RandomRegistry.getRandom().nextInt(availableSeatList.size());
      Seat seat = availableSeatList.get(startSeatIdx);

      Set<Seat> selected = Sets.newHashSet(seat);
      for (int x = 0; x < MAX_SEAT_ATTEMPTS; x++) {
        if (selected.size() == size) {
          break;
        }

        Optional<Seat> nextSeat = selectAdjacent(selected, availableSeatSet, grid);
        if (!nextSeat.isPresent()) {
          break;
        }

        selected.add(nextSeat.get());
      }

      if (selected.size() == size) {
        return Lists.newArrayList(selected);
      }

      lastSelected = selected;
    }

    return Lists.newArrayList(lastSelected);
  }

  public static Optional<Seat> selectAdjacent(Set<Seat> existing, Set<Seat> available, SeatGrid grid) {
    Set<Seat> allAdjacent = existing.stream()
        .flatMap(seat -> grid.getAdjacent(seat).stream())
        .collect(Collectors.toSet());

    Set<Seat> newAdjacent = Sets.difference(allAdjacent, existing);

    Set<Seat> availableAdjacent = Sets.intersection(newAdjacent, available);
    if (availableAdjacent.size() == 0) {
      return Optional.empty();
    }

    Point center = centroid(existing);
    // Get the nearest
    return availableAdjacent.stream()
        .min(Comparator.comparing(seat -> PointUtils.distance(seat, center)));
  }

  @Override
  public String getIdentifier() {
    return team.id();
  }

  private static Point centroid(Collection<? extends PointBase> points) {
    double sumX = 0;
    double sumY = 0;

    for (PointBase point : points) {
      sumX += point.x();
      sumY += point.y();
    }

    double x = sumX / points.size();
    double y = sumY / points.size();

    return Point.builder().x(x).y(y).build();
  }

}
