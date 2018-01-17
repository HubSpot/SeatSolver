package com.hubspot.seatsolver.genetic;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.SeatIF;
import com.hubspot.seatsolver.model.TeamIF;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;

@Singleton
public class SeatGenotypeFactory implements Factory<Genotype<EnumGene<SeatIF>>> {
  private static final Logger LOG = LoggerFactory.getLogger(SeatGenotypeFactory.class);

  private final ISeq<? extends SeatIF> seats;
  private final Set<SeatIF> seatSet;
  private final List<? extends TeamIF> teams;
  private final SeatGrid grid;

  @Inject
  public SeatGenotypeFactory(ISeq<? extends SeatIF> seats,
                             List<? extends TeamIF> teams,
                             SeatGrid grid) {
    this.seats = seats;
    this.seatSet = ImmutableSet.copyOf(seats);
    this.teams = teams;
    this.grid = grid;
  }

  @Override
  public Genotype<EnumGene<SeatIF>> newInstance() {
    // This is a very naive algorithm, we pick a random unused seat, start there and then find the adjacent seats and make chromosome from that
    // We also randomize the direction of movement, and the previous seat from which movement starts
    // We will allow invalid solutions by simply picking an unused seat if we are boxed in
    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.debug("Starting new genotype generation");
    Set<SeatIF> availableSeats = Sets.newHashSet(seats);

    List<Chromosome<EnumGene<SeatIF>>> chromosomes = teams.stream()
        .sorted(Comparator.comparing(TeamIF::numMembers).reversed())
        .map(team -> chromosomeForTeamIF(team, availableSeats))
        .collect(Collectors.toList());

    chromosomes.add(new EmptySeatChromosome(availableSeats, seats));

    LOG.debug("Finished new genotype generation in {}ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    return Genotype.of(chromosomes);
  }

  private TeamChromosome chromosomeForTeamIF(TeamIF team, Set<SeatIF> remaining) {
    List<? extends SeatIF> selected = TeamChromosome.selectSeatBlock(grid, ISeq.of(remaining), remaining, team.numMembers());
    remaining.removeAll(selected);

    LOG.trace("Selected {} for team {}, remaining: {}", selected, team, remaining);

    return new TeamChromosome(grid, seats, seatSet, selected, team);
  }
}
