package com.hubspot.seatsolver;

import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.SeatGenotypeValidator;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.SeatIF;
import com.hubspot.seatsolver.model.Team;

import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.util.RandomRegistry;

public class SeatSolver {
  private static final Logger LOG = LoggerFactory.getLogger(SeatSolver.class);

  private final List<Seat> seatList;
  private final SeatGrid seatGrid;
  private final List<Team> teams;

  public SeatSolver(List<Seat> seatList) {
    this.seatList = seatList;
    this.seatGrid = new SeatGrid(seatList);

    this.teams = Lists.newArrayList(
        Team.builder().id("A").numMembers(3).build(),
        Team.builder().id("B").numMembers(5).build(),
        Team.builder().id("C").numMembers(2).build()
    );
  }

  public void run() {
    LOG.info("Building engine");

    SeatGenotypeFactory factory = new SeatGenotypeFactory(seatList, seatGrid, teams);
    SeatGenotypeValidator validator = new SeatGenotypeValidator(seatGrid);

    Engine<SeatGene, Double> engine = Engine.builder(this::fitness, factory)
        .maximizing()
        .individualCreationRetries(10000)
        .genotypeValidator(validator::validateGenotype)
        .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.info("Starting evolution");
    EvolutionStatistics statistics = EvolutionStatistics.ofNumber();

    Phenotype<SeatGene, Double> result = engine.stream()
        .limit(10)
        .peek(anyGeneDoubleEvolutionResult -> {
          statistics.accept(anyGeneDoubleEvolutionResult);

          LOG.info("Got intermediate result genotype: {}", anyGeneDoubleEvolutionResult.getBestPhenotype().getGenotype());
        })
        .collect(EvolutionResult.toBestPhenotype());

    LOG.info("Finished evolving in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    System.out.println(statistics);

    LOG.info("\n\n************\nBest genotype:\n{}\n*********\n", result.getGenotype());
  }

  private double fitness(Genotype<SeatGene> genotype) {
    return RandomRegistry.getRandom().nextInt(10);
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
