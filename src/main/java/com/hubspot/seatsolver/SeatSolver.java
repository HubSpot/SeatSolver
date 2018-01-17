package com.hubspot.seatsolver;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.hubspot.seatsolver.config.SeatSolverConfig;
import com.hubspot.seatsolver.genetic.EmptySeatChromosome;
import com.hubspot.seatsolver.genetic.SeatGenotypeFactory;
import com.hubspot.seatsolver.genetic.SeatGenotypeValidator;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.model.SeatIF;
import com.hubspot.seatsolver.model.SeatIF;
import com.hubspot.seatsolver.model.TeamIF;
import com.hubspot.seatsolver.utils.DoubleStatistics;
import com.hubspot.seatsolver.utils.GenotypeVisualizer;
import com.hubspot.seatsolver.utils.GenotypeWriter;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.Alterer;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.ForkJoinPopulationFilter;
import io.jenetics.engine.Limits;

public class SeatSolver {
  private static final Logger LOG = LoggerFactory.getLogger(SeatSolver.class);

  private final SeatSolverConfig config;
  private final List<? extends SeatIF> seats;
  private final List<? extends TeamIF> teams;
  private final SeatGenotypeFactory genotypeFactory;
  private final SeatGenotypeValidator genotypeValidator;
  private final GenotypeWriter genotypeWriter;

  @Inject
  public SeatSolver(SeatSolverConfig config,
                    List<? extends SeatIF> seats,
                    List<? extends TeamIF> teams,
                    SeatGenotypeFactory genotypeFactory,
                    SeatGenotypeValidator genotypeValidator,
                    GenotypeWriter genotypeWriter) {
    this.config = config;
    this.seats = seats;
    this.teams = teams;
    this.genotypeFactory = genotypeFactory;
    this.genotypeValidator = genotypeValidator;
    this.genotypeWriter = genotypeWriter;
  }

  @SuppressWarnings("unchecked")
  public Phenotype<EnumGene<SeatIF>, Double> run() throws Exception {

    long run =  System.currentTimeMillis();
    LOG.info("Building engine - Run {}", run);

    ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
    if (config.populationFilterParallelism().isPresent()) {
      forkJoinPool = new ForkJoinPool(config.populationFilterParallelism().get());
    }

    if (config.alterers().size() < 1) {
      throw new IllegalArgumentException("Must specify at least one alterer!");
    }

    Alterer<EnumGene<SeatIF>, Double> first = config.alterers().get(0);
    Alterer<EnumGene<SeatIF>, Double>[] alterers = config.alterers().size() > 1 ?
        config.alterers().subList(1, config.alterers().size()).toArray(new Alterer[]{}) :
        new Alterer[]{};

    Engine<EnumGene<SeatIF>, Double> engine = Engine.builder(this::fitness, this.genotypeFactory)
        .individualCreationRetries(100000)
        .minimizing()
        .genotypeValidator(this.genotypeValidator::validateGenotype)
        .populationSize(1500)
        .survivorsSize(100)
        .populationFilter(new ForkJoinPopulationFilter<>(forkJoinPool, 42))
        .executor(config.executor())
        .maximalPhenotypeAge(100)
        .alterers(first, alterers)
        .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.info("Starting evolution");
    EvolutionStatistics statistics = EvolutionStatistics.ofNumber();

    AtomicReference<EvolutionResult<EnumGene<SeatIF>, Double>> currentResult = new AtomicReference<>(null);
    Runtime.getRuntime().addShutdownHook(
        new Thread(() -> {
          EvolutionResult<EnumGene<SeatIF>, Double> result = currentResult.get();
          if (result != null) {
            writeGenotype(result, run);
          }
        })
    );

    Phenotype<EnumGene<SeatIF>, Double> result = engine.stream()
        //.limit(Limits.byFitnessConvergence(20, 200, .000000000001))
        .limit(Limits.byExecutionTime(Duration.of(8, ChronoUnit.HOURS)))
        .limit(100000)
        .peek(r -> {
          statistics.accept(r);

          if (r.getTotalGenerations() % 100 == 0 || r.getTotalGenerations() == 1) {
            writeGenotype(r, run);
          }

          LOG.info(
              "Generation {} ({} ms/gen):\n  Invalid: {}\n  Killed: {}\n  Worst: {}\n  Best: {}",
              r.getGeneration(),
              stopwatch.elapsed(TimeUnit.MILLISECONDS) / r.getTotalGenerations(),
              r.getInvalidCount(),
              r.getKillCount(),
              r.getWorstFitness(),
              r.getBestFitness()
          );
          LOG.debug("Got intermediate result genotype: {}",
              r.getBestPhenotype());
        })
        .collect(EvolutionResult.toBestPhenotype());

    LOG.info("Finished evolving in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    System.out.println(statistics);

    boolean isValidSolution = this.genotypeValidator.validateGenotype(result.getGenotype());
    LOG.info("\n\n************\nValid? {}\nFitness: {}\nGenotype:\n{}\n************\n", isValidSolution, result.getRawFitness(), result.getGenotype());

    genotypeWriter.write(result.getGenotype(), "out/solution-" + run + ".json");
    GenotypeVisualizer.outputGraphViz(result.getGenotype(), "out/out-" + run + ".dot");

    return result;
  }

  private void writeGenotype(EvolutionResult<EnumGene<SeatIF>, Double> result, long run) {
    try {
      GenotypeVisualizer.outputGraphViz(
          result.getBestPhenotype().getGenotype(),
          String.format("out/run-%d-gen-%06d.dot", run, result.getTotalGenerations())
      );
      genotypeWriter.write(
          result.getBestPhenotype().getGenotype(),
          String.format("out/run-%d-gen-%06d.json", run, result.getTotalGenerations())
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private double fitness(Genotype<EnumGene<SeatIF>> genotype) {

    Map<String, TeamChromosome> chromosomeByTeamIF = genotype.stream()
        .filter(c -> !(c instanceof EmptySeatChromosome))
        .map(c -> ((TeamChromosome) c))
        .collect(Collectors.toMap(c -> c.getTeam().id(), c -> c));

    DoubleStatistics intraTeamStats = new DoubleStatistics();
    DoubleStatistics adjacencyStats = new DoubleStatistics();

    genotype.stream()
        .filter(c -> !(c instanceof EmptySeatChromosome))
        .forEach(genes -> {
          TeamChromosome chromosome = ((TeamChromosome) genes);
          intraTeamStats.accept(chromosome.meanWeightedSeatDistance());
          adjacencyDists(chromosome, chromosomeByTeamIF).forEach(adjacencyStats);
        });

    double intraTeamScaled = intraTeamStats.getSum() * intraTeamStats.getStandardDeviation();
    double adjacencyScaled = adjacencyStats.getSum() * adjacencyStats.getStandardDeviation();
    return (intraTeamScaled / 2) + adjacencyScaled;
  }

  private DoubleStream adjacencyDists(TeamChromosome chromosome, Map<String, TeamChromosome> chromosomeByTeamIF) {
    return chromosome.getTeam().wantsAdjacent().stream()
        .mapToDouble(adj -> {
          TeamChromosome other = chromosomeByTeamIF.get(adj.id());
          if (other == null) {
            return ((double) 0);
          }

          return Math.abs(PointUtils.distance(chromosome.centroid(), other.centroid())) * adj.effectiveWeight();
        })
        .filter(d -> d > 0);
  }
}
