package com.hubspot.seatsolver;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.SeatGenotypeValidator;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.hubspot.HubspotDataLoader;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;
import com.hubspot.seatsolver.utils.GenotypeVisualizer;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.Genotype;
import io.jenetics.MultiPointCrossover;
import io.jenetics.Mutator;
import io.jenetics.Phenotype;
import io.jenetics.RouletteWheelSelector;
import io.jenetics.SwapMutator;
import io.jenetics.TournamentSelector;
import io.jenetics.UniformCrossover;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.engine.Limits;

public class SeatSolver {
  private static final Logger LOG = LoggerFactory.getLogger(SeatSolver.class);

  private final List<Seat> seatList;
  private final SeatGrid seatGrid;
  private final List<Team> teams;

  public SeatSolver(List<Seat> seatList, List<Team> teams) {
    this.seatList = seatList;
    this.seatGrid = new SeatGrid(seatList);

    this.teams = teams;
  }

  public void run() throws Exception {
    LOG.info("Building engine");

    SeatGenotypeFactory factory = new SeatGenotypeFactory(seatList, seatGrid, teams);
    SeatGenotypeValidator validator = new SeatGenotypeValidator(seatGrid);

    ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactoryBuilder().setDaemon(true).setNameFormat("seat-solver-evolve-%d").build());

    Engine<SeatGene, Double> engine = Engine.builder(this::fitness, factory)
        .executor(executorService)
        .individualCreationRetries(100000)
        .minimizing()
        .genotypeValidator(validator::validateGenotype)
        .populationSize(50)
        .survivorsSelector(new TournamentSelector<>(4))
        .offspringSelector(new RouletteWheelSelector<>())
        .alterers(
            new SwapMutator<>(.4),
            new MultiPointCrossover<>(.4, 8),
            new UniformCrossover<>(.3),
            new Mutator<>(.2)
        )
        .build();

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.info("Starting evolution");
    EvolutionStatistics statistics = EvolutionStatistics.ofNumber();

    AtomicReference<Genotype<SeatGene>> firstGenotype = new AtomicReference<>(null);

    Phenotype<SeatGene, Double> result = engine.stream()
        .limit(Limits.byFitnessConvergence(20, 200, .000000000001))
        .limit(Limits.byExecutionTime(Duration.of(600, ChronoUnit.MILLIS)))
        .limit(100000)
        .peek(anyGeneDoubleEvolutionResult -> {
          statistics.accept(anyGeneDoubleEvolutionResult);
          firstGenotype.compareAndSet(null, anyGeneDoubleEvolutionResult.getBestPhenotype().getGenotype());

          LOG.info("Got intermediate result with score: {}", anyGeneDoubleEvolutionResult.getBestPhenotype().getRawFitness());
          LOG.debug("Got intermediate result genotype: {}", anyGeneDoubleEvolutionResult.getBestPhenotype());
        })
        .collect(EvolutionResult.toBestPhenotype());

    LOG.info("Finished evolving in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    System.out.println(statistics);

    boolean isValidSolution = validator.validateGenotype(result.getGenotype());
    LOG.info("\n\n************\nValid? {}\nFitness: {}\nGenotype:\n{}\n************\n", isValidSolution, result.getRawFitness(), result.getGenotype());
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.MINUTES);

    new GenotypeVisualizer(result.getGenotype(), seatList).outputGraphViz("out/out.dot");
    new GenotypeVisualizer(firstGenotype.get(), seatList).outputGraphViz("out/first.dot");
  }

  private double fitness(Genotype<SeatGene> genotype) {
    // Minimize distance between team members
    List<Double> intraTeamScores = genotype.stream()
        .mapToDouble(seatGenes -> {
          TeamChromosome chromosome = ((TeamChromosome) seatGenes);

          return chromosome.meanWeightedSeatDistance();
        })
        .boxed()
        .collect(Collectors.toList());

    double seventyFifthIntraTeam = percentile(intraTeamScores, 75);

    Map<String, TeamChromosome> chomosomeByTeam = genotype.stream()
        .map(c -> ((TeamChromosome) c))
        .collect(Collectors.toMap(c -> c.getTeam().id(), c -> c));

    // Minimize requested adjacent team distance
    List<Double> scores = genotype.stream()
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
        .boxed()
        .collect(Collectors.toList());

    double seventyFifthInterTeam = percentile(scores, 75);
    double ninetyFifthInterTeam = percentile(scores, 95);

    LOG.trace("Inter: {}", seventyFifthInterTeam);
    LOG.trace("Intra: {}", seventyFifthIntraTeam);
     return seventyFifthInterTeam + ninetyFifthInterTeam + seventyFifthIntraTeam;
  }

  public static void main(String[] args) throws Exception {
    LOG.info("Starting data load");
    HubspotDataLoader dataLoader = new HubspotDataLoader("data/data.json");
    dataLoader.load();

    new SeatSolver(dataLoader.getSeats(), dataLoader.getTeams()).run();
  }

  public static double percentile(List<Double> values, double percentile) {
    List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
    int idx = (int) Math.ceil((percentile / (double)100) * (double)sorted.size());
    return sorted.get(idx-1);
  }
}
