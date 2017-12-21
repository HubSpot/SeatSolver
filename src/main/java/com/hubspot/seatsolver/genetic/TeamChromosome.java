package com.hubspot.seatsolver.genetic;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Direction;
import com.hubspot.seatsolver.model.Seat;

import io.jenetics.AbstractChromosome;
import io.jenetics.Chromosome;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;

public class TeamChromosome extends AbstractChromosome<SeatGene> {

  private final SeatGrid seatGrid;
  private final List<Seat> allSeats;

  public TeamChromosome(SeatGrid seatGrid,
                        List<Seat> allSeats,
                        int size) {
    this(seatGrid, allSeats, selectSeatBlock(seatGrid, allSeats, size));
  }

  public TeamChromosome(SeatGrid seatGrid,
                        List<Seat> allSeats,
                        List<Seat> selectedSeats) {
    super(ISeq.of(
        selectedSeats.stream()
            .map(seat -> new SeatGene(allSeats, seat))
            .collect(Collectors.toList())
    ));

    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
  }

  public TeamChromosome(ISeq<? extends SeatGene> genes, SeatGrid seatGrid, List<Seat> allSeats) {
    super(genes);
    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
  }

  public float meanSeatDistance() {
    return 0;
  }

  @Override
  public Chromosome<SeatGene> newInstance(ISeq<SeatGene> genes) {
    return new TeamChromosome(genes, seatGrid, allSeats);
  }

  @Override
  public Chromosome<SeatGene> newInstance() {
    List<Seat> selected = selectSeatBlock(seatGrid, allSeats, length());
    return new TeamChromosome(seatGrid, allSeats, selected);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("_genes", _genes)
        .add("_valid", _valid)
        .toString();
  }

  private static final int MAX_ATTEMPTS = 100;

  public static List<Seat> selectSeatBlock(SeatGrid grid, List<Seat> availableSeats, int size) {
    // pick a random starting point
    int startSeatIdx = RandomRegistry.getRandom().nextInt(availableSeats.size());
    Seat seat = availableSeats.get(startSeatIdx);

    int attempts = 0;
    List<Seat> selected = Lists.newArrayList(seat);
    while (selected.size() < size && attempts < MAX_ATTEMPTS) {
      attempts++;

      // Pick random existing seat to start from
      int originSeatIdx = RandomRegistry.getRandom().nextInt(selected.size());
      Seat origin = selected.get(originSeatIdx);

      Optional<Seat> optionalSeat = grid.findAdjacent(origin, Direction.randomDirection());
      optionalSeat.ifPresent(selected::add);

    }

    if (selected.size() < size) {
      throw new IllegalStateException(String.format("Could not find enough adjacent seats for team of size %d starting at %s!", size, seat));
    }

    return selected;
  }
}
