package com.hubspot.seatsolver;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
import com.hubspot.seatsolver.model.AssignmentResult;
import com.hubspot.seatsolver.model.PopulationResult;
import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.model.TeamCore;
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
  private final List<SeatCore> seats;
  private final List<TeamCore> teams;
  private final SeatGenotypeFactory genotypeFactory;
  private final SeatGenotypeValidator genotypeValidator;
  private final GenotypeWriter genotypeWriter;

  @Inject
  public SeatSolver(SeatSolverConfig config,
                    List<SeatCore> seats,
                    List<TeamCore> teams,
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
  public Phenotype<EnumGene<SeatCore>, Double> run() throws Exception {

    try {
      config.getOutputDirectory().mkdirs();
    } catch (Exception ignored) {
    }

    long run =  System.currentTimeMillis();
    LOG.info("Building engine - Run {}", run);

    ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
    if (config.populationFilterParallelism().isPresent()) {
      forkJoinPool = new ForkJoinPool(config.populationFilterParallelism().get());
    }

    if (config.alterers().isEmpty()) {
      throw new IllegalArgumentException("Must specify at least one alterer!");
    }

    Alterer<EnumGene<SeatCore>, Double> first = config.alterers().get(0);
    Alterer<EnumGene<SeatCore>, Double>[] alterers = config.alterers().size() > 1 ?
        config.alterers().subList(1, config.alterers().size()).toArray(new Alterer[]{}) :
        new Alterer[]{};

    Engine<EnumGene<SeatCore>, Double> engine = Engine.builder(this::fitness, this.genotypeFactory)
        .individualCreationRetries(100000)
        .minimizing()
        .genotypeValidator(this.genotypeValidator::validateGenotype)
        .populationSize(1500)
        .survivorsSize(100)
        .populationFilter(new ForkJoinPopulationFilter<>(forkJoinPool, 42))
        .executor(config.executor())
        .maximalPhenotypeAge(100)
        .alterers(first, alterers)
        .parallelPhenotypeGeneration(config.parallelPhenotypeGeneration())
        .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.info("Starting evolution");
    EvolutionStatistics statistics = EvolutionStatistics.ofNumber();

    AtomicReference<EvolutionResult<EnumGene<SeatCore>, Double>> currentResult = new AtomicReference<>(null);
    Runtime.getRuntime().addShutdownHook(
        new Thread(() -> {
          EvolutionResult<EnumGene<SeatCore>, Double> result = currentResult.get();
          if (result != null) {
            writeGenotype(result, run);
          }
        })
    );

    EvolutionResult<EnumGene<SeatCore>, Double> result = engine.stream()
        //.limit(Limits.byFitnessConvergence(20, 200, .000000000001))
        .limit(Limits.byExecutionTime(Duration.of(8, ChronoUnit.HOURS)))
        .limit(100000)
        .peek(r -> {
          statistics.accept(r);

          if (r.getTotalGenerations() % config.getGenerationWriteFrequency() == 0 || r.getTotalGenerations() == 1) {
            writeGenotype(r, run);
            config.solutionListener().ifPresent(
                listener -> listener.checkpointSolution(buildPopulationResult(r), r.getTotalGenerations())
            );
            LOG.info(
                "Generation {} ({} ms/gen):\n  Invalid: {}\n  Killed: {}\n  Worst: {}\n  Best: {}",
                r.getGeneration(),
                stopwatch.elapsed(TimeUnit.MILLISECONDS) / r.getTotalGenerations(),
                r.getInvalidCount(),
                r.getKillCount(),
                r.getWorstFitness(),
                r.getBestFitness()
            );
          } else {
            LOG.debug(
                "Generation {} ({} ms/gen):\n  Invalid: {}\n  Killed: {}\n  Worst: {}\n  Best: {}",
                r.getGeneration(),
                stopwatch.elapsed(TimeUnit.MILLISECONDS) / r.getTotalGenerations(),
                r.getInvalidCount(),
                r.getKillCount(),
                r.getWorstFitness(),
                r.getBestFitness()
            );
          }
          LOG.debug("Got intermediate result genotype: {}",
              r.getBestPhenotype());
        })
        .reduce((a, b) -> b)
        .orElse(null);

    LOG.info("Finished evolving in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    System.out.println(statistics);

    Phenotype<EnumGene<SeatCore>, Double> best = result.getBestPhenotype();

    boolean isValidSolution = this.genotypeValidator.validateGenotype(best.getGenotype());
    LOG.info("\n\n************\nValid? {}\nFitness: {}\nGenotype:\n{}\n************\n", isValidSolution, best.getRawFitness(), best.getGenotype());
    if (isValidSolution) {
      config.solutionListener().ifPresent(
          listener -> listener.completeSolution(buildPopulationResult(result))
      );
    }
    genotypeWriter.write(best.getGenotype(), getPath("solution-" + run + ".json"));
    GenotypeVisualizer.outputGraphViz(best.getGenotype(), getPath("out-" + run + ".dot"));

    return best;
  }

  private String getPath(String filename) {
    return new File(config.getOutputDirectory(), filename).getAbsolutePath();
  }

  private void writeGenotype(EvolutionResult<EnumGene<SeatCore>, Double> result, long run) {
    try {
      GenotypeVisualizer.outputGraphViz(
          result.getBestPhenotype().getGenotype(),
          getPath(String.format("run-%d-gen-%06d.dot", run, result.getTotalGenerations()))
      );
      genotypeWriter.write(
          result.getBestPhenotype().getGenotype(),
          getPath(String.format("run-%d-gen-%06d.json", run, result.getTotalGenerations()))
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private double fitness(Genotype<EnumGene<SeatCore>> genotype) {

    Map<String, TeamChromosome> chromosomeByTeamCore = genotype.stream()
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
          adjacencyDists(chromosome, chromosomeByTeamCore).forEach(adjacencyStats);
        });

    double intraTeamScaled;
    if (config.intraTeamPercentile().isPresent()) {
      intraTeamScaled = intraTeamStats.getApproxPerentile(config.intraTeamPercentile().get());
    } else {
      intraTeamScaled = intraTeamStats.getSum() * intraTeamStats.getStandardDeviation();
    }
    double adjacencyScaled = adjacencyStats.getSum() * adjacencyStats.getStandardDeviation();
    return intraTeamScaled + adjacencyScaled;
  }

  private DoubleStream adjacencyDists(TeamChromosome chromosome, Map<String, TeamChromosome> chromosomeByTeamCore) {
    return chromosome.getTeam().wantsAdjacent().stream()
        .mapToDouble(adj -> {
          TeamChromosome other = chromosomeByTeamCore.get(adj.id());
          if (other == null) {
            return ((double) 0);
          }

          return Math.abs(PointUtils.distance(chromosome.centroid(), other.centroid())) * adj.effectiveWeight();
        })
        .filter(d -> d > 0);
  }

  private PopulationResult buildPopulationResult(EvolutionResult<EnumGene<SeatCore>, Double> result) {
    List<AssignmentResult> top10Results = result.getPopulation().stream()
        .sorted(Comparator.<Phenotype<? ,Double>, Double>comparing(Phenotype::getFitness).reversed())
        .limit(10)
        .map(gene -> AssignmentResult.builder()
            .addAllTeamAssignments(genotypeWriter.buildAssignments(gene.getGenotype()))
            .fitness(gene.getRawFitness())
            .build())
        .collect(Collectors.toList());
    return PopulationResult.builder()
        .addAllTopTen(top10Results)
        .best(AssignmentResult.builder()
            .addAllTeamAssignments(genotypeWriter.buildAssignments(result.getBestPhenotype().getGenotype()))
            .fitness(result.getBestPhenotype().getRawFitness())
            .build())
        .build();
  }
}
