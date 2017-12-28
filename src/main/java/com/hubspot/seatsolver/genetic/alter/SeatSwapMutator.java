package com.hubspot.seatsolver.genetic.alter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.hubspot.seatsolver.genetic.AbstractSeatChromosome;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Seat;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Mutator;
import io.jenetics.MutatorResult;
import io.jenetics.internal.math.probability;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;

public class SeatSwapMutator<C extends Comparable<? super C>> extends Mutator<EnumGene<Seat>, C> {
  private static final Logger LOG = LoggerFactory.getLogger(SeatSwapMutator.class);

  private final SeatGrid seatGrid;
  private final List<Seat> allSeats;

  @AssistedInject
  public SeatSwapMutator(@Assisted double probability, SeatGrid seatGrid, List<Seat> allSeats) {
    super(probability);
    this.seatGrid = seatGrid;
    this.allSeats = allSeats;
  }

  @Override
  protected MutatorResult<Genotype<EnumGene<Seat>>> mutate(Genotype<EnumGene<Seat>> genotype, double p, Random random) {
    final int P = probability.toInt(p);

    Map<Seat, AbstractSeatChromosome> seatsToChromosome = new HashMap<>();
    genotype.stream().forEach(c -> {
      AbstractSeatChromosome chromosome = ((AbstractSeatChromosome) c);

      c.forEach(gene -> {
        seatsToChromosome.put(gene.getAllele(), chromosome);
      });
    });

    Set<String> modified = new HashSet<>();
    List<AbstractSeatChromosome> newChromosomes = new ArrayList<>();
    genotype.stream().forEach(c -> {
      if (!(random.nextInt() < P)) {
        return;
      }

      AbstractSeatChromosome chromosome = ((AbstractSeatChromosome) c);
      if (modified.contains(chromosome.getIdentifier())) {
        return;
      }

      Set<Seat> current = chromosome.stream()
          .map(EnumGene::getAllele)
          .collect(Collectors.toSet());

      Set<Seat> allAdjacent = chromosome.stream()
          .map(EnumGene::getAllele)
          .flatMap(s -> seatGrid.getAdjacent(s).stream())
          .collect(Collectors.toSet());

      List<AbstractSeatChromosome> adjacent = Sets.difference(allAdjacent, current).stream()
          .map(seatsToChromosome::get)
          .distinct()
          .collect(Collectors.toList());

      if (adjacent.size() == 0) {
        return;
      }

      int toSwapIdx = RandomRegistry.getRandom().nextInt(adjacent.size());
      AbstractSeatChromosome toSwap = adjacent.get(toSwapIdx);
      if (modified.contains(toSwap.getIdentifier())
          || toSwap.getIdentifier().equalsIgnoreCase(chromosome.getIdentifier())) {
        return;
      }

      LOG.trace("Swapping {} with {}", chromosome.getIdentifier(), toSwap.getIdentifier());

      Set<AbstractSeatChromosome> swapped = swap(chromosome, toSwap);
      for (AbstractSeatChromosome swappedChromosome : swapped) {
        modified.add(swappedChromosome.getIdentifier());
        newChromosomes.add(swappedChromosome);
      }
    });

    for (Chromosome<EnumGene<Seat>> c : genotype) {
      AbstractSeatChromosome chromosome = ((AbstractSeatChromosome) c);
      if (!modified.contains(chromosome.getIdentifier())) {
        newChromosomes.add(chromosome);
      }
    }

    return MutatorResult.of(Genotype.of(newChromosomes), modified.size());
  }

  private Set<AbstractSeatChromosome> swap(AbstractSeatChromosome that, AbstractSeatChromosome other) {
    Set<Seat> thatSet = that.stream().map(EnumGene::getAllele).collect(Collectors.toSet());
    Set<Seat> otherSet = other.stream().map(EnumGene::getAllele).collect(Collectors.toSet());

    Set<Seat> thatAdjacent = Sets.difference(
        that.stream()
            .flatMap(s -> seatGrid.getAdjacent(s.getAllele()).stream())
            .collect(Collectors.toSet()),
        thatSet
    );

    Set<Seat> otherAdjacent = Sets.difference(
        other.stream()
            .flatMap(s -> seatGrid.getAdjacent(s.getAllele()).stream())
            .collect(Collectors.toSet()),
        otherSet
    );

    // find seats that could be given to "that"
    Set<Seat> thatSwappable = Sets.intersection(thatAdjacent, otherSet);
    // find seats that could be given to "that"
    Set<Seat> otherSwappable = Sets.intersection(otherAdjacent, thatSet);

    if (thatSwappable.size() == 0 || otherSwappable.size() == 0) {
      return Collections.emptySet();
    }

    List<Seat> thatSwappableList = Lists.newArrayList(thatSwappable);
    int idx = RandomRegistry.getRandom().nextInt(thatSwappableList.size());
    Seat thatSwap = thatSwappableList.get(idx);

    List<Seat> otherSwappableList = Lists.newArrayList(otherSwappable);
    idx = RandomRegistry.getRandom().nextInt(otherSwappableList.size());
    Seat otherSwap = otherSwappableList.get(idx);

    // Don't swap if these are the same
    if (thatSwap == otherSwap) {
      return Collections.emptySet();
    }

    // Find the index in the original mseq
    List<Seat> thatList = that.stream().map(EnumGene::getAllele).collect(Collectors.toList());
    List<Seat> otherList = other.stream().map(EnumGene::getAllele).collect(Collectors.toList());

    int thatIdx = thatList.indexOf(otherSwap);
    int otherIdx = otherList.indexOf(thatSwap);

    thatList.set(thatIdx, thatSwap);
    otherList.set(otherIdx, otherSwap);

    return Sets.newHashSet(
        that.newSeatChromosome(
            ISeq.of(
                thatList.stream()
                    .map(seat -> EnumGene.of(allSeats.indexOf(seat), ISeq.of(allSeats)))
                    .collect(Collectors.toList())
            )
        ),
        other.newSeatChromosome(
            ISeq.of(
                otherList.stream()
                    .map(seat -> EnumGene.of(allSeats.indexOf(seat), ISeq.of(allSeats)))
                    .collect(Collectors.toList())
            )
        )
    );
  }

  @Override
  protected MutatorResult<Chromosome<EnumGene<Seat>>> mutate(Chromosome<EnumGene<Seat>> chromosome, double p, Random random) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected EnumGene<Seat> mutate(EnumGene<Seat> gene, Random random) {
    throw new UnsupportedOperationException();
  }

  public interface SeatSwapCrossoverFactory {
    SeatSwapMutator<Double> create(double probability);
  }
}
