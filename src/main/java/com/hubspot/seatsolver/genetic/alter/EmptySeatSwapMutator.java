package com.hubspot.seatsolver.genetic.alter;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.hubspot.seatsolver.genetic.EmptySeatChromosome;
import com.hubspot.seatsolver.genetic.TeamChromosome;
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

public class EmptySeatSwapMutator extends Mutator<EnumGene<SeatCore>, Double> {
  public EmptySeatSwapMutator(double probability) {
    super(probability);
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

    Genotype<EnumGene<SeatCore>> genotype = phenotype.getGenotype();
    int chIdx = random.nextInt(genotype.length());
    Chromosome<EnumGene<SeatCore>> chromosome = genotype.get(chIdx);
    if (chromosome instanceof EmptySeatChromosome) {
      return MutatorResult.of(phenotype);
    }

    TeamChromosome teamChromosome = ((TeamChromosome) chromosome);

    int emptySeatIdx = -1;
    EmptySeatChromosome empty = null;
    for (int i = 0; i < genotype.length(); i++) {
      Chromosome<EnumGene<SeatCore>> current = genotype.get(i);
      if (current instanceof EmptySeatChromosome) {
        emptySeatIdx = i;
        empty = ((EmptySeatChromosome) current);
        break;
      }
    }

    if (emptySeatIdx == -1) {
      throw new IllegalStateException("All genotypes should have one EmptySeatChromosome");
    }

    TeamChromosome newChromosome = teamChromosome.newTeamChromosome(empty.toSeq().map(EnumGene::getAllele));

    Set<SeatCore> newBlock = newChromosome.stream().map(EnumGene::getAllele).collect(Collectors.toSet());

    MSeq<EnumGene<SeatCore>> emptyGenesMut = empty.toSeq().copy();
    emptyGenesMut = emptyGenesMut.append(teamChromosome.toSeq());

    Chromosome<EnumGene<SeatCore>> newEmpty = empty.newInstance(
        emptyGenesMut.stream()
            .filter(gene -> !newBlock.contains(gene.getAllele()))
            .collect(ISeq.toISeq())
    );

    if (newEmpty.length() != empty.length()) {
      // This failed and will not be valid, simply abort
      return MutatorResult.of(phenotype);
    }

    final MSeq<Chromosome<EnumGene<SeatCore>>> chromosomes = genotype.toSeq().copy();
    chromosomes.set(emptySeatIdx, newEmpty);
    chromosomes.set(chIdx, newChromosome);

    return MutatorResult.of(phenotype.newInstance(Genotype.of(chromosomes)));
  }
}
