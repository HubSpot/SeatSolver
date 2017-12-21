package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Direction;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.AbstractChromosome;
import io.jenetics.Chromosome;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;

public class TeamChromosome extends AbstractChromosome<SeatGene> {
  private static final Logger LOG = LoggerFactory.getLogger(TeamChromosome.class);

  private final SeatGrid seatGrid;
  private final List<Seat> allSeats;
  private final Team team;

  public TeamChromosome(SeatGrid seatGrid,
                        List<Seat> allSeats,
                        Team team) {
    this(seatGrid, allSeats, selectSeatBlock(seatGrid, allSeats, team.numMembers()), team);
  }

  public TeamChromosome(SeatGrid seatGrid,
                        List<Seat> allSeats,
                        List<Seat> selectedSeats,
                        Team team) {
    super(ISeq.of(
        selectedSeats.stream()
            .map(seat -> new SeatGene(allSeats, seat))
            .collect(Collectors.toList())
    ));

    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
    this.team = team;
  }

  public TeamChromosome(ISeq<? extends SeatGene> genes, SeatGrid seatGrid, List<Seat> allSeats, Team team) {
    super(genes);
    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
    this.team = team;
  }

  public Team getTeam() {
    return team;
  }

  public double meanWeightedSeatDistance() {
    // mean of pairwise distances
    List<Seat> seats = toSeq().stream().map(SeatGene::getSeat).collect(Collectors.toList());

    double totalDist = 0;
    int pairs = 0;
    for (Seat seat : seats) {
      for (Seat other : seats) {
        if (seat == other) {
          continue;
        }

        double dist = PointUtils.distance(seat, other);
        totalDist += Math.pow(dist, 2);
        pairs++;
      }
    }

    if (pairs == 0) {
      return 0;
    }

    return totalDist / ((double) pairs);
  }

  public Point centroid() {
    // mean of pairwise distances
    double sumX = toSeq().stream().mapToDouble(gene -> gene.getSeat().x()).sum();
    double sumY = toSeq().stream().mapToDouble(gene -> gene.getSeat().y()).sum();

    double x = sumX / length();
    double y = sumY / length();

    return Point.builder().x(x).y(y).build();
  }

  @Override
  public Chromosome<SeatGene> newInstance(ISeq<SeatGene> genes) {
    return new TeamChromosome(genes, seatGrid, allSeats, team);
  }

  @Override
  public Chromosome<SeatGene> newInstance() {
    List<Seat> selected = selectSeatBlock(seatGrid, allSeats, length());
    return new TeamChromosome(seatGrid, allSeats, selected, team);
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

  public static List<Seat> selectSeatBlock(SeatGrid grid, List<Seat> availableSeats, int size) {
    List<Seat> selected = selectBlock(grid, availableSeats, size);

    Set<Seat> selectedSet = new HashSet<>(selected);
    if (selected.size() < size) {
      LOG.warn(String.format("Could not find enough adjacent seats for team of size %d", size));
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
      }

      if (selected.size() < size) {
        LOG.warn("Failed to create full seat block for {} seats", size);
      }
    }

    return selected;
  }

  private static List<Seat> selectBlock(SeatGrid grid, List<Seat> availableSeatList, int size) {
    Set<Seat> availableSeatSet = Sets.newHashSet(availableSeatList);

    List<Seat> lastSelected = new ArrayList<>();
    for (int y = 0; y < MAX_BLOCK_ATTEMPTS; y++) {
      // pick a random starting point
      int startSeatIdx = RandomRegistry.getRandom().nextInt(availableSeatList.size());
      Seat seat = availableSeatList.get(startSeatIdx);

      int seatAttempts = 0;
      Set<String> selectedIds = Sets.newHashSet(seat.id());
      List<Seat> selected = Lists.newArrayList(seat);
      while (selected.size() < size && seatAttempts < MAX_SEAT_ATTEMPTS) {
        seatAttempts++;

        // Pick random existing seat to start from
        int originSeatIdx = RandomRegistry.getRandom().nextInt(selected.size());
        Seat origin = selected.get(originSeatIdx);

        for (Direction direction : Direction.values()) {
          Optional<Seat> optionalSeat = grid.findAdjacent(origin, direction);

          if (optionalSeat.isPresent()) {
            Seat selectedSeat = optionalSeat.get();
            if (selectedIds.contains(selectedSeat.id()) || !availableSeatSet.contains(selectedSeat)) {
              continue;
            }

            selected.add(selectedSeat);
            selectedIds.add(selectedSeat.id());
          }
        }
      }

      if (selected.size() == size) {
        return selected;
      }

      lastSelected = selected;
    }

    return lastSelected;
  }

}
