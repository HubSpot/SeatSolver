package com.hubspot.seatsolver.genetic.alter;

import java.util.Random;

import com.hubspot.seatsolver.model.Seat;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.Recombinator;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.RandomRegistry;

public class TeamSwapCrossover extends Recombinator<EnumGene<Seat>, Double> {
  private final int maxSizeRetries;

  public TeamSwapCrossover(double probability, int maxSizeRetries) {
    super(probability, 2);
    this.maxSizeRetries = maxSizeRetries;
  }

  private void crossover(MSeq<EnumGene<Seat>> that, MSeq<EnumGene<Seat>> other) {
    // At this point we are guaranteed these are of the same length
    // Because they are the same length we can literally just swap the seats
    that.swap(0, that.length(), other, 0);
  }

  @Override
  protected int recombine(MSeq<Phenotype<EnumGene<Seat>, Double>> population, int[] individuals, long generation) {
    // This implementation is basically stolen from Crossover
    assert individuals.length == 2 : "Required order of 2";
    final Random random = RandomRegistry.getRandom();

    final Phenotype<EnumGene<Seat>, Double> pt1 = population.get(individuals[0]);
    final Phenotype<EnumGene<Seat>, Double> pt2 = population.get(individuals[1]);
    final Genotype<EnumGene<Seat>> gt1 = pt1.getGenotype();
    final Genotype<EnumGene<Seat>> gt2 = pt2.getGenotype();

    //Choosing the Chromosome index for crossover.
    final int chIndex1 = random.nextInt(gt1.length());
    int chIndex2 = random.nextInt(gt2.length());

    final MSeq<Chromosome<EnumGene<Seat>>> c1 = gt1.toSeq().copy();
    final MSeq<Chromosome<EnumGene<Seat>>> c2 = gt2.toSeq().copy();
    final MSeq<EnumGene<Seat>> genes1 = c1.get(chIndex1).toSeq().copy();
    ISeq<EnumGene<Seat>> genes2 = c2.get(chIndex2).toSeq();

    int i = 0;
    while (genes2.length() != genes1.length() && i < maxSizeRetries) {
      i++;

      chIndex2 = random.nextInt(gt2.length());
      genes2 = c2.get(chIndex2).toSeq();
    }

    if (genes2.length() != genes1.length()) {
      return 0;
    }

    MSeq<EnumGene<Seat>> genes2Mut = genes2.asMSeq();
    crossover(genes1, genes2Mut);

    c1.set(chIndex1, c1.get(chIndex1).newInstance(genes1.toISeq()));
    c2.set(chIndex2, c2.get(chIndex2).newInstance(genes2Mut.toISeq()));

    //Creating two new Phenotypes and exchanging it with the old.
    population.set(
        individuals[0],
        pt1.newInstance(Genotype.of(c1.toISeq()), generation)
    );
    population.set(
        individuals[1],
        pt2.newInstance(Genotype.of(c2.toISeq()), generation)
    );

    return getOrder();
  }
}
