package com.hubspot.seatsolver.genetic;

import java.util.Comparator;
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
import com.hubspot.seatsolver.model.SeatIF;
import com.hubspot.seatsolver.model.TeamIF;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.RandomRegistry;

public class TeamChromosome extends AbstractSeatChromosome {
  private static final Logger LOG = LoggerFactory.getLogger(TeamChromosome.class);

  private final SeatGrid seatGrid;
  private final ISeq<? extends SeatIF> allSeats;
  private final Set<? extends SeatIF> allSeatsSet;
  private final TeamIF team;

  private AtomicReference<Point> centroid = new AtomicReference<>(null);
  private AtomicDouble meanWeightedSeatDist = new AtomicDouble(-1);

  public TeamChromosome(SeatGrid seatGrid,
                        ISeq<? extends SeatIF> allSeats,
                        Set<? extends SeatIF> allSeatsSet,
                        List<? extends SeatIF> selectedSeats,
                        TeamIF team) {
    super(generateSeq(allSeats, selectedSeats));
    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
    this.allSeatsSet = allSeatsSet;
    this.team = team;
  }

  public TeamChromosome(ISeq<? extends EnumGene<SeatIF>> genes, SeatGrid seatGrid, ISeq<? extends SeatIF> allSeats, TeamIF team) {
    super(genes);
    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
    this.allSeatsSet = ImmutableSet.copyOf(allSeats);
    this.team = team;
  }

  private static ISeq<EnumGene<SeatIF>> generateSeq(ISeq<? extends SeatIF> allSeats,
                                                    List<? extends SeatIF> selectedSeats) {
    MSeq<EnumGene<SeatIF>> result = MSeq.ofLength(selectedSeats.size());
    ISeq<SeatIF> allSeatsSeq = ISeq.of(allSeats);
    for (int i = 0; i < selectedSeats.size(); ++i) {
      SeatIF seat = selectedSeats.get(i);
      result.set(i, EnumGene.of(allSeats.indexOf(seat), allSeatsSeq));
    }
    return result.toISeq();
  }

  public TeamIF getTeam() {
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
    List<? extends SeatIF> seats = toSeq().stream()
        .map(EnumGene::getAllele)
        .collect(Collectors.toList());

    double totalDist = 0;
    int pairs = 0;
    for (SeatIF seat : seats) {
      for (SeatIF other : seats) {
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

  private List<? extends SeatIF> selectSeatBlock(ISeq<? extends SeatIF> availableSeats) {
    return selectSeatBlock(seatGrid, availableSeats, length());
  }

  @Override
  public AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<SeatIF>> genes) {
    return new TeamChromosome(genes, seatGrid, allSeats, team);
  }

  public TeamChromosome newTeamChromosome(ISeq<? extends SeatIF> available) {
    return new TeamChromosome(seatGrid, allSeats, allSeatsSet, selectSeatBlock(available), team);
  }

  @Override
  public Chromosome<EnumGene<SeatIF>> newInstance(ISeq<EnumGene<SeatIF>> genes) {
    return newSeatChromosome(genes);
  }

  @Override
  public Chromosome<EnumGene<SeatIF>> newInstance() {
    List<? extends SeatIF> selected = selectSeatBlock(seatGrid, allSeats, allSeatsSet, length());
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

  public static List<? extends SeatIF> selectSeatBlock(SeatGrid grid, ISeq<? extends SeatIF> availableSeats, int size) {
    return selectSeatBlock(grid, availableSeats, Sets.newHashSet(availableSeats), size);
  }

  public static List<? extends SeatIF> selectSeatBlock(SeatGrid grid, ISeq<? extends SeatIF> availableSeats, Set<? extends SeatIF> availableSeatSet, int size) {
    List<SeatIF> selected = selectBlock(grid, availableSeats, availableSeatSet, size);

    Set<SeatIF> selectedSet = new HashSet<>(selected);
    if (selected.size() < size) {
      LOG.debug("Could not find enough adjacent seats for team of size {}", size);
      // fill with random seats now, this will not be valid
      int fillAttempts = 0;
      while (selected.size() < size && fillAttempts < MAX_FILL_ATTEMPTS) {
        fillAttempts++;

        int originSeatIdx = RandomRegistry.getRandom().nextInt(selected.size());
        SeatIF randomSeatIF = availableSeats.get(originSeatIdx);
        if (selectedSet.contains(randomSeatIF)) {
          continue;
        }

        selected.add(randomSeatIF);
        selectedSet.add(randomSeatIF);
      }

      if (selected.size() < size) {
        LOG.debug("Failed to create full seat block for {} seats", size);
      }
    }

    return selected;
  }

  private static List<SeatIF> selectBlock(SeatGrid grid, ISeq<? extends SeatIF> availableSeatList, Set<? extends SeatIF> availableSeatSet, int size) {
    Set<SeatIF> lastSelected = new HashSet<>();
    for (int y = 0; y < MAX_BLOCK_ATTEMPTS; y++) {
      // pick a random starting point
      int startSeatIdx = RandomRegistry.getRandom().nextInt(availableSeatList.size());
      SeatIF seat = availableSeatList.get(startSeatIdx);

      Set<SeatIF> selected = Sets.newHashSet(seat);
      for (int x = 0; x < MAX_SEAT_ATTEMPTS; x++) {
        if (selected.size() == size) {
          break;
        }

        Optional<SeatIF> nextSeatIF = selectAdjacent(selected, availableSeatSet, grid);
        if (!nextSeatIF.isPresent()) {
          break;
        }

        selected.add(nextSeatIF.get());
      }

      if (selected.size() == size) {
        return Lists.newArrayList(selected);
      }

      lastSelected = selected;
    }

    return Lists.newArrayList(lastSelected);
  }

  public static Optional<SeatIF> selectAdjacent(Set<SeatIF> existing, Set<? extends SeatIF> available, SeatGrid grid) {
    Set<SeatIF> allAdjacent = existing.stream()
        .flatMap(seat -> grid.getAdjacent(seat).stream())
        .collect(Collectors.toSet());

    Set<SeatIF> newAdjacent = Sets.difference(allAdjacent, existing);

    Set<SeatIF> availableAdjacent = Sets.intersection(newAdjacent, available);
    if (availableAdjacent.size() == 0) {
      return Optional.empty();
    }

    Point center = centroid(existing.iterator());
    // Get the nearest
    return availableAdjacent.stream()
        .min(Comparator.comparing(seat -> PointUtils.distance(seat, center)));
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
