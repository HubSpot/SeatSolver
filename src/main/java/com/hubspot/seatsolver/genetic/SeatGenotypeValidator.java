package com.hubspot.seatsolver.genetic;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.SeatIF;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import net.openhft.koloboke.collect.set.hash.HashObjSet;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

public class SeatGenotypeValidator {
  private static final Logger LOG = LoggerFactory.getLogger(SeatGenotypeValidator.class);

  private final SeatGrid grid;

  @Inject
  public SeatGenotypeValidator(SeatGrid grid) {
    this.grid = grid;
  }

  public boolean validateGenotype(Genotype<EnumGene<SeatIF>> genotype) {
    LOG.trace("Validating genotype: {}", genotype);

    HashObjSet<String> chosen = HashObjSets.getDefaultFactory().newMutableSet();
    HashObjSet<String> empty = HashObjSets.getDefaultFactory().newMutableSet();
    for (Chromosome<EnumGene<SeatIF>> chromosome : genotype) {
      if (chromosome instanceof EmptySeatChromosome) {
        chromosome.stream()
            .map(gene -> gene.getAllele().id())
            .forEach(empty::add);
      }

      for (EnumGene<SeatIF> gene : chromosome) {
        if (chosen.contains(gene.getAllele().id())) {
          return false;
        }

        chosen.add(gene.getAllele().id());
      }

      // now do adjacency
      if (chromosome.length() == 1 || chromosome instanceof EmptySeatChromosome) {
        continue;
      }

      HashObjSet<SeatIF> seats = chromosome.stream()
          .map(EnumGene::getAllele)
          .collect(Collectors.toCollection(HashObjSets.getDefaultFactory()::newMutableSet));

      SeatIF start = seats.iterator().next();

      Set<SeatIF> connected = connectedSeatsForSeatIF(start, seats);
      connectedSeatsForSeatIF(start, seats);

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

  private Set<SeatIF> connectedSeatsForSeatIF(SeatIF seat, Set<SeatIF> toFind) {
    if (toFind.isEmpty()) {
      return Collections.emptySet();
    }

    HashObjSet<SeatIF> toFindMinusSelf = HashObjSets.newMutableSet(toFind);
    toFindMinusSelf.remove(seat);

    Set<SeatIF> adjacentSeats = new HashSet<>(grid.getAdjacent(seat));
    adjacentSeats.removeIf(s -> !toFind.contains(s));

    HashObjSet<SeatIF> toFindMinusAdjacent = HashObjSets.newMutableSet(toFindMinusSelf);
    toFindMinusAdjacent.removeAll(adjacentSeats);

    Set<SeatIF> result = HashObjSets.newMutableSet(adjacentSeats);
    result.add(seat);

    for (SeatIF adjacentSeatIF: adjacentSeats) {
      Set<SeatIF> nextConnected = connectedSeatsForSeatIF(adjacentSeatIF, toFindMinusAdjacent);
      toFindMinusAdjacent.removeAll(nextConnected);
      result.addAll(nextConnected);
    }

    return result;
  }
}
