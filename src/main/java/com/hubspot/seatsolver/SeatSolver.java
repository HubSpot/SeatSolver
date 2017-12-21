package com.hubspot.seatsolver;

import java.io.FileReader;
import java.io.Reader;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.SeatGenotypeValidator;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.SeatIF;
import com.hubspot.seatsolver.model.Team;
import com.hubspot.seatsolver.utils.DoubleStatistics;

import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;

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
        Team.builder().id("B").numMembers(2).build(),
        Team.builder().id("C").numMembers(5).build(),
        Team.builder().id("D").numMembers(2).build(),
        Team.builder().id("E").numMembers(1).build(),
        Team.builder().id("F").numMembers(1).build()
    );
  }

  public void run() throws Exception {
    LOG.info("Building engine");

    SeatGenotypeFactory factory = new SeatGenotypeFactory(seatList, seatGrid, teams);
    SeatGenotypeValidator validator = new SeatGenotypeValidator(seatGrid);

    ExecutorService executorService = Executors.newFixedThreadPool(20, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("seat-solver-evolve-%d").build());

    Engine<SeatGene, Double> engine = Engine.builder(this::fitness, factory)
        .executor(executorService)
        .individualCreationRetries(100000)
        .minimizing()
        .genotypeValidator(validator::validateGenotype)
        .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.info("Starting evolution");
    EvolutionStatistics statistics = EvolutionStatistics.ofNumber();

    Phenotype<SeatGene, Double> result = engine.stream()
        .limit(Limits.byFitnessConvergence(10, 50,  .000000000001))
        .limit(Limits.byExecutionTime(Duration.of(3, ChronoUnit.MINUTES)))
        .limit(1000)
        .peek(anyGeneDoubleEvolutionResult -> {
          statistics.accept(anyGeneDoubleEvolutionResult);

          LOG.info("Got intermediate result genotype: {}", anyGeneDoubleEvolutionResult.getBestPhenotype());
        })
        .collect(EvolutionResult.toBestPhenotype());

    LOG.info("Finished evolving in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    System.out.println(statistics);

    LOG.info("\n\n************\nFitness: {}\nGenotype:\n{}\n*********\n", result.getRawFitness(), result.getGenotype());
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.MINUTES);
  }

  private double fitness(Genotype<SeatGene> genotype) {
    List<Double> meanDists = genotype.stream()
        .map(seatGenes -> {
          TeamChromosome chromosome = ((TeamChromosome) seatGenes);

          return chromosome.meanSeatDistance();
        })
        .collect(Collectors.toList());

    DoubleStatistics statistics = meanDists.stream().mapToDouble(v -> v)
        .collect(DoubleStatistics::new, DoubleStatistics::accept, DoubleStatistics::combine);

    Multiset<String> dups = HashMultiset.create();
    genotype.stream().flatMap(c -> c.stream()).map(SeatGene::getSeat).map(Seat::id).forEach(dups::add);

    int dupCount = 0;
    for (Entry<String> stringEntry : dups.entrySet()) {
      if (stringEntry.getCount() > 1) {
        dupCount += stringEntry.getCount();
      }
    }

    return statistics.getAverage() + statistics.getStandardDeviation() + Math.pow(dupCount, 2);
  }

  public static void main(String[] args) throws Exception {
    List<Seat> seats = new ArrayList<>();
    Reader seatMapReader = new FileReader("data/seatmap.csv");
    for (CSVRecord record : CSVFormat.DEFAULT.withHeader().parse(seatMapReader)) {
      seats.add(SeatIF.fromCsvRecord(record));
    }

    new SeatSolver(seats).run();
  }

}
