package com.hubspot.seatsolver;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;

import io.jenetics.Genotype;
import io.jenetics.util.Factory;

class SeatGenotypeFactory implements Factory<Genotype<SeatGene>> {
  private static final Logger LOG = LoggerFactory.getLogger(SeatGenotypeFactory.class);

  private final List<Seat> seats;
  private final SeatGrid grid;
  private final List<Team> teams;

  public SeatGenotypeFactory(List<Seat> seats,
                             SeatGrid grid,
                             List<Team> teams) {
    this.seats = seats;
    this.grid = grid;
    this.teams = teams;
  }

  @Override
  public Genotype<SeatGene> newInstance() {
    // This is a very naive algorithm, we pick a random unused seat, start there and then find the adjacent seats and make chromosome from that
    // We also randomize the direction of movement, and the previous seat from which movement starts
    // We will allow invalid solutions by simply picking an unused seat if we are boxed in
    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.debug("Starting new genotype generation");
    Set<Seat> availableSeats = Sets.newHashSet(seats);

    Collections.shuffle(teams); // Random generation order every time
    List<TeamChromosome> chromosomes = teams.stream()
        //.sorted(Comparator.comparing(Team::numMembers).reversed())
        .map(team -> chromosomeForTeam(team, availableSeats))
        .collect(Collectors.toList());

    LOG.debug("Finished new genotype generation in {}ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    return Genotype.of(chromosomes);
  }

  private TeamChromosome chromosomeForTeam(Team team, Set<Seat> remaining) {
    List<Seat> selected = TeamChromosome.selectSeatBlock(grid, Lists.newArrayList(remaining), team.numMembers());
    remaining.removeAll(selected);

    LOG.trace("Selected {} for team {}, remaining: {}", selected, team, remaining);

    return new TeamChromosome(grid, seats, selected, team);
  }
}
