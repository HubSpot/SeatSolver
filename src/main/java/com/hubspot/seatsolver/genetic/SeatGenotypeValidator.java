package com.hubspot.seatsolver.genetic;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.SeatCore;

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

  public boolean validateGenotype(Genotype<EnumGene<SeatCore>> genotype) {
    Stopwatch stopwatch = Stopwatch.createStarted();

    LOG.trace("Validating genotype: {}", genotype);
    for (Chromosome<EnumGene<SeatCore>> chromosome : genotype) {
      if (chromosome instanceof TeamChromosome) {
        if (!((TeamChromosome) chromosome).hasTheRightNumberOfSeats()) {
          return false;
        }
      }
    }

    HashObjSet<String> chosen = HashObjSets.getDefaultFactory().newMutableSet();
    HashObjSet<String> empty = HashObjSets.getDefaultFactory().newMutableSet();
    for (Chromosome<EnumGene<SeatCore>> chromosome : genotype) {
      if (chromosome instanceof EmptySeatChromosome) {
        chromosome.stream()
            .map(gene -> gene.getAllele().id())
            .forEach(empty::add);
      }

      for (EnumGene<SeatCore> gene : chromosome) {
        if (chosen.contains(gene.getAllele().id())) {
          LOG.debug("Duplicate seat used: {}", gene.getAllele());
          return false;
        }

        chosen.add(gene.getAllele().id());
      }

      // now do adjacency
      if (chromosome.length() == 1 || chromosome instanceof EmptySeatChromosome) {
        continue;
      }

      HashObjSet<SeatCore> seats = chromosome.stream()
          .map(EnumGene::getAllele)
          .collect(Collectors.toCollection(HashObjSets.getDefaultFactory()::newMutableSet));

      SeatCore start = seats.iterator().next();
      Set<SeatCore> connected = connectedSeatsForSeatCore(start, seats);

      if (connected.size() < chromosome.length()) {
        LOG.debug("Got unconnected chromosome: {}", chromosome.stream().collect(Collectors.toList()));
        return false;
      }
    }

    if (chosen.size() + empty.size() < grid.size()) {
      LOG.trace("Total selected seats including empty is not equal to grid seats ({} != {})", chosen.size() + empty.size(), grid.size());
      return false;
    }

    LOG.trace("Found valid genotype: {}", genotype);

    LOG.debug("Validated genotype in {}ns", stopwatch.elapsed(TimeUnit.NANOSECONDS));
    return true;
  }

  private Set<SeatCore> connectedSeatsForSeatCore(SeatCore seat, Set<SeatCore> toFind) {
    HashObjSet<SeatCore> toFindMinusSelf = HashObjSets.newMutableSet(toFind);
    toFindMinusSelf.remove(seat);

    Set<SeatCore> adjacentSeats = new HashSet<>(grid.getAdjacent(seat));
    adjacentSeats.removeIf(s -> !toFind.contains(s));

    HashObjSet<SeatCore> toFindMinusAdjacent = HashObjSets.newMutableSet(toFindMinusSelf);
    toFindMinusAdjacent.removeAll(adjacentSeats);

    Set<SeatCore> result = HashObjSets.newMutableSet(adjacentSeats);
    result.add(seat);

    for (SeatCore adjacentSeatCore: adjacentSeats) {
      Set<SeatCore> nextConnected = connectedSeatsForSeatCore(adjacentSeatCore, toFindMinusAdjacent);
      toFindMinusAdjacent.removeAll(nextConnected);
      result.addAll(nextConnected);
    }

    return result;
  }
}
