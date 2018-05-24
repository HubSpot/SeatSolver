package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Adjacency;
import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.model.TeamCore;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;

@Singleton
public class GreedySeatGenotypeFactory implements Factory<Genotype<EnumGene<SeatCore>>> {
  private static final Logger LOG = LoggerFactory.getLogger(GreedySeatGenotypeFactory.class);
  private static final int MAX_TEAM_TRIES = 10;

  private final ISeq<SeatCore> seats;
  private final Map<SeatCore, Integer> seatIndex;
  private final List<TeamCore> teams;
  private final SeatGrid grid;

  private final List<SeatCore> seatsByAdjacencyCount;
  private final Map<String, TeamCore> teamsById;

  @Inject
  public GreedySeatGenotypeFactory(ISeq<SeatCore> seats,
                                   List<TeamCore> teams,
                                   SeatGrid grid) {
    this.seats = seats;
    this.seatIndex = new IdentityHashMap<>(seats.size());
    for (int i = 0; i < seats.size(); ++i) {
      seatIndex.put(seats.get(i), i);
    }
    this.teams = teams;
    this.grid = grid;
    this.seatsByAdjacencyCount = seats.stream()
        .sorted(Comparator.comparing(seatCore -> grid.getAdjacent(seatCore).size()))
        .collect(Collectors.toList());
    this.teamsById = Maps.uniqueIndex(teams, TeamCore::id);
  }

  @Override
  public Genotype<EnumGene<SeatCore>> newInstance() {
    // Start with largest N teams
    // Put those teams in corners (hardest seats to use later)
    // Assign high adjacent weight teams next to them
    // Do this recursively until all teams are assigned

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.debug("Starting new genotype generation");

    BitSet availableSeats = new BitSet(seats.size());
    availableSeats.set(0, seats.size());

    List<Chromosome<EnumGene<SeatCore>>> chromosomes = assignTeams(availableSeats);
    chromosomes.add(new EmptySeatChromosome(seats, availableSeats));

    LOG.debug("Finished new genotype generation in {}ns", stopwatch.elapsed(TimeUnit.NANOSECONDS));
    return Genotype.of(chromosomes);
  }

  private List<Chromosome<EnumGene<SeatCore>>> assignTeams(BitSet availableSeats) {
    // TODO: make this number configurable
    List<TeamCore> startingTeams = teams.stream()
        .sorted(Comparator.comparing(TeamCore::numMembers).reversed())
        .limit(30)
        .sorted(Comparator.comparing(ignored -> ThreadLocalRandom.current().nextBoolean()))
        .collect(Collectors.toList());

    List<TeamChromosome> finalChromosomes = new ArrayList<>();
    Set<String> placedTeamIds = new HashSet<>();
    for (TeamCore team : startingTeams) {
      if (placedTeamIds.contains(team.id())) {
        continue;
      }

      List<TeamChromosome> chromosomes = assignStartingTeam(team, availableSeats, placedTeamIds);
      finalChromosomes.addAll(chromosomes);
      placedTeamIds.addAll(chromosomes.stream().map(TeamChromosome::getTeam).map(TeamCore::id).collect(Collectors.toSet()));
    }

    // now place any remaining teams in random order
    for (TeamCore team : teams) {
      if (!placedTeamIds.contains(team.id())) {
        BitSet selected = TeamChromosome.selectSeatBlock(grid, seats, seatIndex, availableSeats, team.numMembers());

        availableSeats.andNot(selected);

        finalChromosomes.add(new TeamChromosome(
            grid,
            seats,
            seatIndex,
            selected,
            team));
      }
    }

    return new ArrayList<>(finalChromosomes);
  }

  private List<TeamChromosome> assignStartingTeam(TeamCore startingTeam,
                                                  BitSet availableSeats,
                                                  Set<String> placedTeamIds) {
    for (int i = 0; i < MAX_TEAM_TRIES; i++) {
      SeatCore startingSeat = seatsByAdjacencyCount.stream()
          .filter(seatCore -> availableSeats.get(seatIndex.get(seatCore)))
          .limit(10)
          .sorted(Comparator.comparing(ignored -> ThreadLocalRandom.current().nextBoolean()))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("No seats available. This should not be possible"));

      int startIdx = seatIndex.get(startingSeat);

      BitSet selected = TeamChromosome.selectBlock(startIdx, grid, seats, seatIndex, availableSeats, startingTeam.numMembers());
      if (selected.cardinality() != startingTeam.numMembers()) {
        continue;
      }

      // Remove from available set
      availableSeats.andNot(selected);

      TeamChromosome startChromosome = new TeamChromosome(
          grid,
          seats,
          seatIndex,
          selected,
          startingTeam);

      List<TeamChromosome> adjacentChromosomes = startingTeam.wantsAdjacent().stream()
          .map(Adjacency::id)
          .sorted(Comparator.comparing(teamId -> startingTeam.effectiveWeightsByTeamId().getOrDefault(teamId, 1.)))
          .filter(teamId -> !placedTeamIds.contains(teamId))
          .limit(4)
          .map(teamId -> chromosomeForTeamCore(selected, teamsById.get(teamId), availableSeats))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());

      adjacentChromosomes.add(startChromosome);

      return adjacentChromosomes;
    }

    return Collections.emptyList();
  }

  private Optional<TeamChromosome> chromosomeForTeamCore(BitSet adjacentTo,
                                                         TeamCore team,
                                                         BitSet availableSeats) {
    OptionalInt maybeAdjacentIdx = TeamChromosome.selectAdjacent(seats, seatIndex, adjacentTo, availableSeats, grid);
    if (!maybeAdjacentIdx.isPresent()) {
      return Optional.empty();
    }

    int startIdx = maybeAdjacentIdx.getAsInt();
    BitSet selected = TeamChromosome.selectBlock(
        startIdx,
        grid,
        seats,
        seatIndex,
        availableSeats,
        team.numMembers()
    );

    if (selected.cardinality() != team.numMembers()) {
      return Optional.empty();
    }

    // Remove from available set
    availableSeats.andNot(selected);

    return Optional.of(new TeamChromosome(
        grid,
        seats,
        seatIndex,
        selected,
        team));
  }
}
