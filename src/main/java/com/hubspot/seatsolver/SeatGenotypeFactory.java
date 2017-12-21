package com.hubspot.seatsolver;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;

import io.jenetics.Genotype;
import io.jenetics.util.Factory;
import io.jenetics.util.RandomRegistry;

class SeatGenotypeFactory implements Factory<Genotype<SeatGene>> {
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

    Set<Seat> availableSeats = Sets.newHashSet(seats);
    List<TeamChromosome> chromosomes = teams.stream()
        .map(team -> chromosomeForTeam(team, availableSeats))
        .collect(Collectors.toList());

    return Genotype.of(chromosomes);
  }

  private TeamChromosome chromosomeForTeam(Team team, Set<Seat> remaining) {
    List<Seat> selected = TeamChromosome.selectSeatBlock(grid, Lists.newArrayList(remaining), team.numMembers());
    remaining.remove(selected);

    return new TeamChromosome(grid, seats, selected);
  }

  private Seat createRandomSeatGene() {
    int selected = RandomRegistry.getRandom().nextInt(seats.size());
    return seats.get(selected);
  }
}
