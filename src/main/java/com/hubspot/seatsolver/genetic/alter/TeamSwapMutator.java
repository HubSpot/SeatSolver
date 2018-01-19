package com.hubspot.seatsolver.genetic.alter;

import java.util.Random;

import com.hubspot.seatsolver.model.SeatCore;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Mutator;
import io.jenetics.MutatorResult;
import io.jenetics.Phenotype;
import io.jenetics.internal.math.probability;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;

public class TeamSwapMutator extends Mutator<EnumGene<SeatCore>, Double> {
  private final int maxSizeRetries;

  public TeamSwapMutator(double probability, int maxSizeRetries) {
    super(probability);
    this.maxSizeRetries = maxSizeRetries;
  }

  private void swap(MSeq<EnumGene<SeatCore>> that, MSeq<EnumGene<SeatCore>> other) {
    // At this point we are guaranteed these are of the same length
    // Because they are the same length we can literally just swap the seats
    that.swap(0, that.length(), other, 0);
  }

  protected MutatorResult<Phenotype<EnumGene<SeatCore>, Double>> mutate(
      final Phenotype<EnumGene<SeatCore>, Double> phenotype,
      final long generation,
      final double p,
      final Random random
  ) {
    final int P = probability.toInt(p);
    if (random.nextInt() >= P) {
      return MutatorResult.of(phenotype);
    }

    final Genotype<EnumGene<SeatCore>> genotype = phenotype.getGenotype();

    //Choosing the Chromosome index for crossover.
    final int chIndex1 = random.nextInt(genotype.length());
    int chIndex2 = random.nextInt(genotype.length());

    final MSeq<EnumGene<SeatCore>> genes1 = genotype.get(chIndex1).toSeq().copy();
    ISeq<EnumGene<SeatCore>> genes2 = genotype.get(chIndex2).toSeq();

    int i = 0;
    while (genes2.length() != genes1.length() && i < maxSizeRetries) {
      i++;

      chIndex2 = random.nextInt(genotype.length());
      genes2 = genotype.get(chIndex2).toSeq();
    }

    if (genes2.length() != genes1.length()) {
      return MutatorResult.of(phenotype);
    }

    MSeq<EnumGene<SeatCore>> genes2Mut = genes2.asMSeq();
    swap(genes1, genes2Mut);

    final MSeq<Chromosome<EnumGene<SeatCore>>> chromosomes = genotype.toSeq().copy();
    chromosomes.set(chIndex1, genotype.get(chIndex1).newInstance(genes1.toISeq()));
    chromosomes.set(chIndex2, genotype.get(chIndex2).newInstance(genes2Mut.toISeq()));

    return MutatorResult.of(phenotype.newInstance(Genotype.of(chromosomes)));
  }
}
