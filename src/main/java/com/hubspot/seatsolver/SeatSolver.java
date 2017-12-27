package com.hubspot.seatsolver;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.hubspot.seatsolver.genetic.EmptySeatChromosome;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.SeatGenotypeFactory;
import com.hubspot.seatsolver.genetic.SeatGenotypeValidator;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.hubspot.HubspotDataLoader;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;
import com.hubspot.seatsolver.utils.DoubleStatistics;
import com.hubspot.seatsolver.utils.GenotypeVisualizer;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.Genotype;
import io.jenetics.MultiPointCrossover;
import io.jenetics.Phenotype;
import io.jenetics.SwapMutator;
import io.jenetics.UniformCrossover;
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

  @Inject
  public SeatSolver(List<Seat> seats,
                    List<Team> teams,
                    SeatGenotypeFactory genotypeFactory,
                    SeatGenotypeValidator genotypeValidator) {
    this.seats = seats;
    this.teams = teams;
    this.genotypeFactory = genotypeFactory;
    this.genotypeValidator = genotypeValidator;
  }

  public void run() throws Exception {

    LOG.info("Building engine");

    Engine<SeatGene, Double> engine = Engine.builder(this::fitness, this.genotypeFactory)
        .individualCreationRetries(100000)
        .minimizing()
        .genotypeValidator(this.genotypeValidator::validateGenotype)
        .populationSize(200)
        .survivorsSize(40)
        .maximalPhenotypeAge(20)
        .alterers(
            new SwapMutator<>(.2),
            new MultiPointCrossover<>(.3, 4),
            new UniformCrossover<>(.4, .3)
        )
        .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.info("Starting evolution");
    EvolutionStatistics statistics = EvolutionStatistics.ofNumber();

    AtomicBoolean firstGenOuput = new AtomicBoolean(false);

    Phenotype<SeatGene, Double> result = engine.stream()
        //.limit(Limits.byFitnessConvergence(20, 200, .000000000001))
        .limit(Limits.byExecutionTime(Duration.of(600, ChronoUnit.HOURS)))
        .limit(100000)
        .peek(r -> {
          statistics.accept(r);
          if (firstGenOuput.compareAndSet(false, true)) {
            try {
              GenotypeVisualizer.outputGraphViz(r.getBestPhenotype().getGenotype(),"out/first.dot");
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
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

    GenotypeVisualizer.outputGraphViz(result.getGenotype(),"out/out.dot");
  }

  private double fitness(Genotype<SeatGene> genotype) {
    // Minimize distance between team members
    DoubleStatistics intraTeamStats = genotype.stream()
        .filter(c -> !(c instanceof EmptySeatChromosome))
        .mapToDouble(seatGenes -> {
          TeamChromosome chromosome = ((TeamChromosome) seatGenes);

          return chromosome.meanWeightedSeatDistance();
        })
        .collect(DoubleStatistics::new, DoubleStatistics::accept, DoubleStatistics::combine);

    Map<String, TeamChromosome> chomosomeByTeam = genotype.stream()
        .filter(c -> !(c instanceof EmptySeatChromosome))
        .map(c -> ((TeamChromosome) c))
        .collect(Collectors.toMap(c -> c.getTeam().id(), c -> c));

    // Minimize requested adjacent team distance
    DoubleStatistics adjacencyStats = genotype.stream()
        .filter(c -> !(c instanceof EmptySeatChromosome))
        .flatMapToDouble(seatGenes -> {
          TeamChromosome chromosome = ((TeamChromosome) seatGenes);

          return chromosome.getTeam().wantsAdjacent().stream()
              .mapToDouble(adj -> {
                TeamChromosome other = chomosomeByTeam.get(adj.id());
                if (other == null) {
                  return ((double) 0);
                }

                return Math.abs(PointUtils.distance(chromosome.centroid(), other.centroid())) * adj.weight();
              });
        })
        .collect(DoubleStatistics::new, DoubleStatistics::accept, DoubleStatistics::combine);

    double intraTeamScaled = intraTeamStats.getSum() * intraTeamStats.getStandardDeviation();
    double adjacencyScaled = adjacencyStats.getSum() * adjacencyStats.getStandardDeviation();
    return (intraTeamScaled / 2) + adjacencyScaled;
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
