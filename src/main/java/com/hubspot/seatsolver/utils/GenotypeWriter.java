package com.hubspot.seatsolver.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hubspot.seatsolver.genetic.EmptySeatChromosome;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.TeamAssignment;

import io.jenetics.Chromosome;
import io.jenetics.Genotype;

@Singleton
public class GenotypeWriter {
  private final ObjectMapper objectMapper;

  @Inject
  public GenotypeWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(Genotype<SeatGene> genotype, String filename) throws IOException {
    try (FileWriter writer = new FileWriter(filename)) {
      List<TeamAssignment> assignments = genotype.stream()
          .map(c -> {
            if (c instanceof EmptySeatChromosome) {
              EmptySeatChromosome emptySeatChromosome = ((EmptySeatChromosome) c);
              return TeamAssignment.builder()
                  .seats(seatsFromChromosome(emptySeatChromosome))
                  .build();
            }

            TeamChromosome chromosome = ((TeamChromosome) c);
            return TeamAssignment.builder()
                .team(chromosome.getTeam())
                .seats(seatsFromChromosome(chromosome))
                .build();
          })
          .collect(Collectors.toList());

      writer.write(objectMapper.writeValueAsString(assignments));
    }
  }

  private static List<Seat> seatsFromChromosome(Chromosome<SeatGene> chromosome) {
    return chromosome.stream().map(SeatGene::getSeat).collect(Collectors.toList());
  }
}
