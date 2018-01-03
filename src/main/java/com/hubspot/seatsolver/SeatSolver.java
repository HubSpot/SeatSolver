package com.hubspot.seatsolver;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.hubspot.seatsolver.genetic.EmptySeatChromosome;
import com.hubspot.seatsolver.genetic.SeatGenotypeFactory;
import com.hubspot.seatsolver.genetic.SeatGenotypeValidator;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.genetic.alter.EmptySeatSwapMutator;
import com.hubspot.seatsolver.genetic.alter.MultiTeamSwapMutator.MultiTeamSwapMutatorFactory;
import com.hubspot.seatsolver.genetic.alter.SeatSwapMutator.SeatSwapMutatorFactory;
import com.hubspot.seatsolver.genetic.alter.TeamSwapMutator;
import com.hubspot.seatsolver.hubspot.HubspotDataLoader;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;
import com.hubspot.seatsolver.utils.DoubleStatistics;
import com.hubspot.seatsolver.utils.GenotypeVisualizer;
import com.hubspot.seatsolver.utils.GenotypeWriter;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.PartiallyMatchedCrossover;
import io.jenetics.Phenotype;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;

public class SeatSolver {
  private static final Logger LOG = LoggerFactory.getLogger(SeatSolver.class);

  private final List<Seat> seats;
  private final List<Team> teams;
  private final SeatGenotypeFactory genotypeFactory;
  private final SeatGenotypeValidator genotypeValidator;
  private final GenotypeWriter genotypeWriter;
  private final SeatSwapMutatorFactory swapMutatorFactory;
  private final MultiTeamSwapMutatorFactory multiTeamSwapMutatorFactory;

  @Inject
  public SeatSolver(List<Seat> seats,
                    List<Team> teams,
                    SeatGenotypeFactory genotypeFactory,
                    SeatGenotypeValidator genotypeValidator,
                    GenotypeWriter genotypeWriter,
                    SeatSwapMutatorFactory swapMutatorFactory,
                    MultiTeamSwapMutatorFactory multiTeamSwapMutatorFactory) {
    this.seats = seats;
    this.teams = teams;
    this.genotypeFactory = genotypeFactory;
    this.genotypeValidator = genotypeValidator;
    this.genotypeWriter = genotypeWriter;
    this.swapMutatorFactory = swapMutatorFactory;
    this.multiTeamSwapMutatorFactory = multiTeamSwapMutatorFactory;
  }

  public void run() throws Exception {


    LOG.info("Building engine");

    Engine<EnumGene<Seat>, Double> engine = Engine.builder(this::fitness, this.genotypeFactory)
        .individualCreationRetries(100000)
        .minimizing()
        .genotypeValidator(this.genotypeValidator::validateGenotype)
        .populationSize(500)
        .survivorsSize(25)
        .maximalPhenotypeAge(100)
        .alterers(
            new PartiallyMatchedCrossover<>(.15),
            //new Mutator<>(.05),
            multiTeamSwapMutatorFactory.create(.2, 10),
            new TeamSwapMutator(.1, 10),
            new EmptySeatSwapMutator(.2)
        )
        .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.info("Starting evolution");
    EvolutionStatistics statistics = EvolutionStatistics.ofNumber();

    AtomicReference<EvolutionResult<EnumGene<Seat>, Double>> currentResult = new AtomicReference<>(null);
    Runtime.getRuntime().addShutdownHook(
        new Thread(() -> {
          EvolutionResult<EnumGene<Seat>, Double> result = currentResult.get();
          if (result != null) {
            writeGenotype(result);
          }
        })
    );

    Phenotype<EnumGene<Seat>, Double> result = engine.stream()
        //.limit(Limits.byFitnessConvergence(20, 200, .000000000001))
        .limit(Limits.byExecutionTime(Duration.of(8, ChronoUnit.HOURS)))
        .limit(100000)
        .peek(r -> {
          statistics.accept(r);

          if (r.getTotalGenerations() % 100 == 0 || r.getTotalGenerations() == 1) {
            writeGenotype(r);
          }

          LOG.info(
              "Generation {}:\n  Invalid: {}\n  Killed: {}\n  Worst: {}\n  Best: {}",
              r.getGeneration(),
              r.getInvalidCount(),
              r.getKillCount(),
              r.getWorstFitness(),
              r.getBestFitness()
          );
          LOG.debug("Got intermediate result genotype: {}", r.getBestPhenotype());
        })
        .collect(EvolutionResult.toBestPhenotype());

    LOG.info("Finished evolving in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    System.out.println(statistics);

    boolean isValidSolution = this.genotypeValidator.validateGenotype(result.getGenotype());
    LOG.info("\n\n************\nValid? {}\nFitness: {}\nGenotype:\n{}\n************\n", isValidSolution, result.getRawFitness(), result.getGenotype());

    genotypeWriter.write(result.getGenotype(), "out/solution.json");
    GenotypeVisualizer.outputGraphViz(result.getGenotype(), "out/out.dot");
  }

  private void writeGenotype(EvolutionResult<EnumGene<Seat>, Double> result) {
    try {
      GenotypeVisualizer.outputGraphViz(
          result.getBestPhenotype().getGenotype(),
          String.format("out/gen-%d.dot", result.getTotalGenerations())
      );
      genotypeWriter.write(
          result.getBestPhenotype().getGenotype(),
          String.format("out/gen-%d.json", result.getTotalGenerations())
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private double fitness(Genotype<EnumGene<Seat>> genotype) {

    Map<String, TeamChromosome> chromosomeByTeam = genotype.stream()
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
          adjacencyDists(chromosome, chromosomeByTeam).forEach(adjacencyStats);
        });

    double intraTeamScaled = intraTeamStats.getSum() * intraTeamStats.getStandardDeviation();
    double adjacencyScaled = adjacencyStats.getSum() * adjacencyStats.getStandardDeviation();
    return (intraTeamScaled / 2) + adjacencyScaled;
  }

  private DoubleStream adjacencyDists(TeamChromosome chromosome, Map<String, TeamChromosome> chromosomeByTeam) {
    return chromosome.getTeam().wantsAdjacent().stream()
        .mapToDouble(adj -> {
          TeamChromosome other = chromosomeByTeam.get(adj.id());
          if (other == null) {
            return ((double) 0);
          }

          return Math.abs(PointUtils.distance(chromosome.centroid(), other.centroid())) * adj.weight();
        })
        .filter(d -> d > 0);
  }

  public static void main(String[] args) throws Exception {
    LOG.info("Starting data load");
    HubspotDataLoader dataLoader = new HubspotDataLoader("data/data.json");
    dataLoader.load();

    LOG.info("Creating injector");
    Injector i = Guice.createInjector(new SeatSolverModule(dataLoader));

    LOG.info("Running optimizer");
    i.getInstance(SeatSolver.class).run();;

    LOG.info("Optimization complete");
  }
}
