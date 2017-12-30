package com.hubspot.seatsolver.genetic;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Seat;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;

public class SeatGenotypeValidator {
  private static final Logger LOG = LoggerFactory.getLogger(SeatGenotypeValidator.class);

  private final SeatGrid grid;

  @Inject
  public SeatGenotypeValidator(SeatGrid grid) {
    this.grid = grid;
  }

  public boolean validateGenotype(Genotype<EnumGene<Seat>> genotype) {
    LOG.trace("Validating genotype: {}", genotype);

    Set<String> chosen = new HashSet<>();
    Set<String> empty = new HashSet<>();
    for (Chromosome<EnumGene<Seat>> chromosome : genotype) {
      if (chromosome instanceof EmptySeatChromosome) {
        empty.addAll(chromosome.stream()
            .map(gene -> gene.getAllele().id())
            .collect(Collectors.toSet())
        );
      }

      for (EnumGene<Seat> gene : chromosome) {
        if (chosen.contains(gene.getAllele().id())) {
          return false;
        }

        chosen.add(gene.getAllele().id());
      }

      // now do adjacency
      if (chromosome.length() == 1 || chromosome instanceof EmptySeatChromosome) {
        continue;
      }

      Set<Seat> seats = chromosome.stream()
          .map(EnumGene::getAllele)
          .collect(Collectors.toSet());

      Seat start = seats.iterator().next();

      Set<Seat> connected = connectedSeatsForSeat(start, seats);
      connectedSeatsForSeat(start, seats);

      if (connected.size() < chromosome.length()) {
        LOG.debug("Got unconnected chromosome: {}", chromosome.stream().collect(Collectors.toList()));
        return false;
      }
    }

    if (chosen.size() + empty.size() < grid.size()) {
      return false;
    }

    LOG.trace("Found valid genotype: {}", genotype);

    return true;
  }

  private Set<Seat> connectedSeatsForSeat(Seat seat, Set<Seat> toFind) {
    if (toFind.isEmpty()) {
      return Collections.emptySet();
    }

    Set<Seat> toFindMinusSelf = new HashSet<>(toFind);
    toFindMinusSelf.remove(seat);

    Set<Seat> adjacentSeats = Sets.newHashSet(grid.getAdjacent(seat));
    adjacentSeats.removeIf(s -> !toFind.contains(s));

    Set<Seat> toFindMinusAdjacent = new HashSet<>(toFindMinusSelf);
    toFindMinusAdjacent.removeAll(adjacentSeats);

    Set<Seat> result = new HashSet<>(adjacentSeats);
    result.add(seat);

    for (Seat adjacentSeat: adjacentSeats) {
      Set<Seat> nextConnected = connectedSeatsForSeat(adjacentSeat, toFindMinusAdjacent);
      toFindMinusAdjacent.removeAll(nextConnected);
      result.addAll(nextConnected);
    }

    return result;
  }
}
