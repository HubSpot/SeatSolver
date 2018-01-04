package com.hubspot.seatsolver.genetic.alter;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.hubspot.seatsolver.genetic.EmptySeatChromosome;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Seat;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Mutator;
import io.jenetics.MutatorResult;
import io.jenetics.Phenotype;
import io.jenetics.internal.math.probability;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;

public class MultiTeamSwapMutator extends Mutator<EnumGene<Seat>, Double> {
  private final SeatGrid seatGrid;
  private final int maxSizeRetries;

  @Inject
  public MultiTeamSwapMutator(SeatGrid seatGrid, @Assisted double probability, @Assisted int maxSizeRetries) {
    super(probability);
    this.maxSizeRetries = maxSizeRetries;
    this.seatGrid = seatGrid;
  }

  protected MutatorResult<Phenotype<EnumGene<Seat>, Double>> mutate(
      final Phenotype<EnumGene<Seat>, Double> phenotype,
      final long generation,
      final double p,
      final Random random
  ) {
    final int P = probability.toInt(p);
    if (random.nextInt() >= P) {
      return MutatorResult.of(phenotype);
    }

    final Genotype<EnumGene<Seat>> genotype = phenotype.getGenotype();

    //Choosing the Chromosome index for crossover.
    final int chIndex1 = random.nextInt(genotype.length());

    Chromosome<EnumGene<Seat>> ch1 = genotype.get(chIndex1);
    if (ch1 instanceof EmptySeatChromosome) {
      return MutatorResult.of(phenotype);
    }

   TeamChromosome teamChromosome1 = ((TeamChromosome) ch1);

    Set<Seat> adjacent = ch1.stream()
        .flatMap(seatEnumGene -> seatGrid.getAdjacent(seatEnumGene.getAllele()).stream())
        .collect(Collectors.toSet());

    // now find an adjacent team to add into the mix
    int chIndex2 = -1;
    TeamChromosome teamChromosome2 = null;
    for (int i = 0; i < genotype.length(); i++) {
      Chromosome<EnumGene<Seat>> maybeAdjacent = genotype.get(i);
      if (maybeAdjacent instanceof EmptySeatChromosome) {
        continue;
      }

      if (maybeAdjacent.stream().anyMatch(gene -> adjacent.contains(gene.getAllele()))) {
        teamChromosome2 = ((TeamChromosome) maybeAdjacent);
        chIndex2 = i;
      }
    }

    if (chIndex2 < 0) {
      return MutatorResult.of(phenotype);
    }

    int totalSize = teamChromosome1.length() + teamChromosome2.length();

    int chIndex3 = random.nextInt(genotype.length());
    Chromosome<EnumGene<Seat>> ch3 = genotype.get(chIndex3);

    int i = 0;
    while ((ch3.length() != totalSize && i < maxSizeRetries) || ch3 instanceof EmptySeatChromosome) {
      i++;

      chIndex3 = random.nextInt(genotype.length());
      ch3 = genotype.get(chIndex3);
    }

    TeamChromosome teamChromosome3 = ((TeamChromosome) ch3);

    if (teamChromosome3.length() != totalSize) {
      return MutatorResult.of(phenotype);
    }

    ISeq<Seat> available = teamChromosome3.toSeq().map(EnumGene::getAllele);
    Chromosome<EnumGene<Seat>> newTeam1 = teamChromosome1.newTeamChromosome(available);

    Set<Seat> taken = newTeam1.stream().map(EnumGene::getAllele).collect(Collectors.toSet());
    ISeq<EnumGene<Seat>> remaining = teamChromosome3.stream().filter(gene -> !taken.contains(gene.getAllele())).collect(ISeq.toISeq());
    Chromosome<EnumGene<Seat>> newTeam2 = teamChromosome2.newInstance(remaining);

    ISeq<EnumGene<Seat>> genes3 = teamChromosome1.toSeq().append(teamChromosome2.toSeq());
    Chromosome<EnumGene<Seat>> newTeam3 = teamChromosome3.newInstance(genes3);

    final MSeq<Chromosome<EnumGene<Seat>>> chromosomes = genotype.toSeq().copy();
    chromosomes.set(chIndex1, newTeam1);
    chromosomes.set(chIndex2, newTeam2);
    chromosomes.set(chIndex3, newTeam3);

    return MutatorResult.of(phenotype.newInstance(Genotype.of(chromosomes)));
  }

  public interface MultiTeamSwapMutatorFactory {
    MultiTeamSwapMutator create(double probability, int maxSizeRetries);
  }
}