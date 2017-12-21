package com.hubspot.seatsolver.genetic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Seat;

import io.jenetics.Chromosome;
import io.jenetics.Genotype;

public class SeatGenotypeValidator {
  private static final Logger LOG = LoggerFactory.getLogger(SeatGenotypeValidator.class);

  private final SeatGrid grid;

  public SeatGenotypeValidator(SeatGrid grid) {
    this.grid = grid;
  }

  public boolean validateGenotype(Genotype<SeatGene> genotype) {
    LOG.trace("Validating genotype: {}", genotype);

    Set<String> chosen = new HashSet<>();
    for (Chromosome<SeatGene> chromosome : genotype) {
      for (SeatGene gene : chromosome) {
        if (chosen.contains(gene.getSeat().id())) {
          return false;
        }

        chosen.add(gene.getSeat().id());
      }
    }

    // now do adjacency
    for (Chromosome<SeatGene> chromosome : genotype) {
      List<Seat> seats = chromosome.stream()
          .map(SeatGene::getSeat)
          .collect(Collectors.toList());

      if (seats.size() <= 1) {
        continue;
      }

      boolean allAdjacent = seats.stream()
          .allMatch(seat -> {
            return seats.stream().anyMatch(seat2 -> grid.isAdjacent(seat, seat2));
          });

      if (!allAdjacent) {
        return false;
      }
    }

    LOG.trace("Found valid genotype: {}", genotype);

    return true;
  }
}
