package com.hubspot.seatsolver.genetic.alter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import com.hubspot.seatsolver.utils.Pair;

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
  private final ISeq<Seat> allSeats;

  @AssistedInject
  public SeatSwapMutator(@Assisted double probability, SeatGrid seatGrid, List<Seat> allSeats) {
    super(probability);
    this.seatGrid = seatGrid;
    this.allSeats = ISeq.of(allSeats);
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

    Map<String, Integer> chromosomeToIndex = new HashMap<>();
    for (int i = 0; i < genotype.length(); i++) {
      chromosomeToIndex.put(((AbstractSeatChromosome) genotype.get(i)).getIdentifier(), i);
    }

    int modified = 0;
    List<AbstractSeatChromosome> newChromosomes = Arrays.asList(new AbstractSeatChromosome[genotype.length()]);
    for (int i = 0; i < genotype.length(); i++) {
      if (!(random.nextInt() < P)) {
        continue;
      }

      // If there is already a new chromosome, don't reprocess
      if (newChromosomes.get(i) != null) {
        continue;
      }

      AbstractSeatChromosome chromosome = ((AbstractSeatChromosome) genotype.get(i));

      Set<Seat> current = chromosome.stream()
          .map(EnumGene::getAllele)
          .collect(Collectors.toSet());

      Set<Seat> allAdjacent = chromosome.stream()
          .map(EnumGene::getAllele)
          .flatMap(s -> seatGrid.getAdjacent(s).stream())
          .collect(Collectors.toSet());

      Optional<AbstractSeatChromosome> maybeToSwap = Sets.difference(allAdjacent, current).stream()
          .map(seatsToChromosome::get)
          .filter(Objects::nonNull)
          .findFirst();

      if (!maybeToSwap.isPresent()) {
        continue;
      }

      // Don't swap with ourselves
      AbstractSeatChromosome toSwap = maybeToSwap.get();
      if (toSwap.getIdentifier().equalsIgnoreCase(chromosome.getIdentifier())) {
        continue;
      }

      int toSwapIdx = chromosomeToIndex.get(toSwap.getIdentifier());

      LOG.trace("Swapping {} with {}", chromosome.getIdentifier(), toSwap.getIdentifier());

      Optional<Pair<AbstractSeatChromosome, AbstractSeatChromosome>> maybeSwapped = swap(chromosome, toSwap);
      if (maybeSwapped.isPresent()) {
        modified += 2;
        Pair<AbstractSeatChromosome, AbstractSeatChromosome> pair = maybeSwapped.get();
        newChromosomes.set(i, pair.first());
        newChromosomes.set(toSwapIdx, pair.second());
      }
    }

    // Always return chromosomes in the same order!
    for (int i = 0; i < genotype.length(); i++) {
      if (newChromosomes.get(i) != null) {
        continue;
      }

      newChromosomes.set(i, ((AbstractSeatChromosome) genotype.get(i)));
    }

    return MutatorResult.of(Genotype.of(newChromosomes), modified);
  }

  private Optional<Pair<AbstractSeatChromosome, AbstractSeatChromosome>> swap(AbstractSeatChromosome that,
                                                                              AbstractSeatChromosome other) {
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
      return Optional.empty();
    }

    List<Seat> thatSwappableList = Lists.newArrayList(thatSwappable);
    int idx = RandomRegistry.getRandom().nextInt(thatSwappableList.size());
    Seat thatSwap = thatSwappableList.get(idx);

    List<Seat> otherSwappableList = Lists.newArrayList(otherSwappable);
    idx = RandomRegistry.getRandom().nextInt(otherSwappableList.size());
    Seat otherSwap = otherSwappableList.get(idx);

    // Don't swap if these are the same
    if (thatSwap == otherSwap) {
      return Optional.empty();
    }

    // Find the index in the original mseq
    List<Seat> thatList = that.stream().map(EnumGene::getAllele).collect(Collectors.toList());
    List<Seat> otherList = other.stream().map(EnumGene::getAllele).collect(Collectors.toList());

    int thatIdx = thatList.indexOf(otherSwap);
    int otherIdx = otherList.indexOf(thatSwap);

    thatList.set(thatIdx, thatSwap);
    otherList.set(otherIdx, otherSwap);

    return Optional.of(
        Pair.of(
            that.newSeatChromosome(
                thatList.stream()
                    .map(seat -> EnumGene.of(allSeats.indexOf(seat), allSeats))
                    .collect(ISeq.toISeq())
            ),
            other.newSeatChromosome(
                otherList.stream()
                    .map(seat -> EnumGene.of(allSeats.indexOf(seat), allSeats))
                    .collect(ISeq.toISeq()
                    )
            )
        )
    );
  }

  @Override
  protected MutatorResult<Chromosome<EnumGene<Seat>>> mutate(Chromosome<EnumGene<Seat>> chromosome,
                                                             double p,
                                                             Random random) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected EnumGene<Seat> mutate(EnumGene<Seat> gene, Random random) {
    throw new UnsupportedOperationException();
  }

  public interface SeatSwapMutatorFactory {
    SeatSwapMutator<Double> create(double probability);
  }
}
