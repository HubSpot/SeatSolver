package com.hubspot.seatsolver.genetic.alter;

import java.util.Optional;
import java.util.Random;

import com.google.common.collect.Streams;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.utils.Pair;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Mutator;
import io.jenetics.MutatorResult;
import io.jenetics.Phenotype;
import io.jenetics.internal.math.probability;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;

public class NearSeatMutator extends Mutator<EnumGene<SeatCore>, Double> {
  private final int maxSizeRetries;

  public NearSeatMutator(double probability, int maxSizeRetries) {
    super(probability);
    this.maxSizeRetries = maxSizeRetries;
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

    for (int i = 0; i < maxSizeRetries; ++i) {
      final int chromosomeIdx = random.nextInt(genotype.length());
      Chromosome<EnumGene<SeatCore>> chromosome = genotype.get(chromosomeIdx);
      if (chromosome instanceof TeamChromosome) {
        Optional<Pair<SeatCore, Integer>> otherTeamSeat = ((TeamChromosome) chromosome).findAdjacentSeat();
        if (!otherTeamSeat.isPresent()) {
          continue;
        }
        Pair<SeatCore, Integer> thisTeamSeat = ((TeamChromosome) chromosome).getFurthestSeat();

        Chromosome<EnumGene<SeatCore>> otherChromosome = null;
        int otherChromosomeIdx = -1;
        for (int otherTeamIndex = 0; otherTeamIndex < genotype.length(); ++otherTeamIndex) {
          if (otherTeamIndex == chromosomeIdx) {
            continue;
          }
          Chromosome<EnumGene<SeatCore>> otherTeam = genotype.get(otherTeamIndex);
          if (otherTeam instanceof TeamChromosome &&
              ((TeamChromosome) otherTeam).hasSeatIndex(otherTeamSeat.get().second())) {
            otherChromosomeIdx = otherTeamIndex;
            otherChromosome = otherTeam;
            break;
          }
        }
        if (otherChromosome == null) {
          continue;
        }

        ISeq<EnumGene<SeatCore>> team1Gene = Streams.concat(
            chromosome.stream()
                .filter(gene -> !gene.getAllele().equals(thisTeamSeat.first())),
            otherChromosome.stream()
                .filter(otherGene -> otherGene.getAllele().equals(otherTeamSeat.get().first()))
        ).collect(ISeq.toISeq());

        ISeq<EnumGene<SeatCore>> team2Gene = Streams.concat(
            otherChromosome.stream()
                .filter(gene -> !gene.getAllele().equals(otherTeamSeat.get().first())),
            chromosome.stream()
                .filter(thisGene -> thisGene.getAllele().equals(thisTeamSeat.first()))
        ).collect(ISeq.toISeq());

        MSeq<Chromosome<EnumGene<SeatCore>>> newGenotype = genotype.toSeq().copy();
        newGenotype.set(chromosomeIdx, chromosome.newInstance(team1Gene));
        newGenotype.set(otherChromosomeIdx, otherChromosome.newInstance(team2Gene));
        return MutatorResult.of(phenotype.newInstance(Genotype.of(newGenotype)));
      }
    }

    return MutatorResult.of(phenotype);
  }
}
