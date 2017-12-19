package com.hubspot.seatsolver;

import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import com.google.common.collect.Lists;

import io.jenetics.AnyChromosome;
import io.jenetics.AnyGene;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;

public class SeatSolver {
  private static final int GRID_SIZE_X = 6;
  private static final int GRID_SIZE_Y = 6;

  private final List<Seat> seatList;
  private final HashMap<Integer, HashMap<Integer, Seat>> seatGrid;
  private final Engine<AnyGene<Seat>, Integer> engine;

  public SeatSolver(List<Seat> seatList) {
    this.seatList = seatList;
    this.seatGrid = new HashMap<>();

    for (Seat seat : seatList) {
      HashMap<Integer, Seat> col = seatGrid.getOrDefault(seat.x(), new HashMap<>());
      col.put(seat.y(), seat);
    }

    List<Team> teams = Lists.newArrayList(
        Team.builder().id("A").numMembers(3).build(),
        Team.builder().id("B").numMembers(5).build(),
        Team.builder().id("C").numMembers(2).build()
    );

    Genotype<AnyGene<Seat>> seatGenotype = Genotype.of(teams.stream().map(this::chromosomeForTeam).collect(Collectors.toList()));
    this.engine = Engine.builder(this::fitness, seatGenotype)
        .maximizing()
        .genotypeValidator(this::validateGenotype)
        .build();
  }

  public void run() {
    List<Genotype<AnyGene<Seat>>> results = engine.stream().limit(10).map(EvolutionResult::getBestPhenotype).map(Phenotype::getGenotype).collect(Collectors.toList());

    results.forEach(System.out::println);
  }

  private int fitness(Genotype<AnyGene<Seat>> genotype) {
    return RandomRegistry.getRandom().nextInt(10);
  }

  private AnyChromosome<Seat> chromosomeForTeam(Team team) {
    return AnyChromosome.of(this::createRandomSeatGene, this::validateSeatGene, this::validateSeatGeneSeq, team.numMembers());
  }

  private Seat createRandomSeatGene() {
    int selected = RandomRegistry.getRandom().nextInt(seatList.size());
    return seatList.get(selected);
  }

  private boolean validateSeatGene(Seat seat) {
    return true;
  }

  private boolean validateSeatGeneSeq(ISeq<Seat> seats) {
    return seats.stream()
        .allMatch(seat -> {
          return seats.stream().anyMatch(seat2 -> isAdjacent(seat, seat2));
        });
  }

  // This is like a really simple ray tracer
  private boolean isAdjacent(Seat first, Seat second) {
    int startX = first.x();
    int startY = first.y();

    for (int xOffset = 1; xOffset - startX >= 0; xOffset++) {
      Seat curSeat = seatGrid.getOrDefault(startX - xOffset, new HashMap<>()).get(startY);
      if (curSeat == null) {
        continue;
      }

      if (curSeat == second) {
        return true;
      }
    }

    for (int xOffset = 1; xOffset + startX < GRID_SIZE_X; xOffset++) {
      Seat curSeat = seatGrid.getOrDefault(startX + xOffset, new HashMap<>()).get(startY);
      if (curSeat == null) {
        continue;
      }

      if (curSeat == second) {
        return true;
      }
    }

    for (int yOffset = 1; yOffset - startY >= 0; yOffset++) {
      Seat curSeat = seatGrid.getOrDefault(startX, new HashMap<>()).get(startY - yOffset);
      if (curSeat == null) {
        continue;
      }

      if (curSeat == second) {
        return true;
      }
    }

    for (int yOffset = 1; yOffset + startY < GRID_SIZE_Y; yOffset++) {
      Seat curSeat = seatGrid.getOrDefault(startX, new HashMap<>()).get(startY + yOffset);
      if (curSeat == null) {
        continue;
      }

      if (curSeat == second) {
        return true;
      }
    }

    return false;
  }

  private boolean validateGenotype(Genotype<AnyGene<Seat>> genotype) {
    Set<Seat> chosen = new HashSet<>();
    for (Chromosome<AnyGene<Seat>> chromosome : genotype) {
      for (AnyGene<Seat> gene : chromosome) {
        if (chosen.contains(gene.getAllele())) {
          return false;
        }

        chosen.add(gene.getAllele());
      }
    }

    return true;
  }

  public static void main(String[] args) throws Exception {
    HashMap<Integer, HashMap<Integer, Seat>> grid = new HashMap<>();

    List<Seat> seats = new ArrayList<>();
    Reader seatMapReader = new FileReader("data/seatmap.csv");
    for (CSVRecord record : CSVFormat.DEFAULT.withHeader().parse(seatMapReader)) {
      seats.add(SeatIF.fromCsvRecord(record));
    }

    new SeatSolver(seats).run();

    //Engine.builder(AssignmentEvaluator::evaluate, ).maximizing()
  }

}
