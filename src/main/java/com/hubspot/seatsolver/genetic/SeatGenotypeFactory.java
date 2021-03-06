package com.hubspot.seatsolver.genetic;

import java.util.BitSet;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.model.TeamCore;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.Genotype;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;

@Singleton
public class SeatGenotypeFactory implements Factory<Genotype<EnumGene<SeatCore>>> {
  private static final Logger LOG = LoggerFactory.getLogger(SeatGenotypeFactory.class);

  private final ISeq<SeatCore> seats;
  private final Map<SeatCore, Integer> seatIndex;
  private final List<TeamCore> teams;
  private final SeatGrid grid;

  @Inject
  public SeatGenotypeFactory(ISeq<SeatCore> seats,
                             List<TeamCore> teams,
                             SeatGrid grid) {
    this.seats = seats;
    this.seatIndex = new IdentityHashMap<>(seats.size());
    for (int i = 0; i < seats.size(); ++i) {
      seatIndex.put(seats.get(i), i);
    }
    this.teams = teams;
    this.grid = grid;
  }

  @Override
  public Genotype<EnumGene<SeatCore>> newInstance() {
    // This is a very naive algorithm, we pick a random unused seat, start there and then find the adjacent seats and make chromosome from that
    // We also randomize the direction of movement, and the previous seat from which movement starts
    // We will allow invalid solutions by simply picking an unused seat if we are boxed in
    Stopwatch stopwatch = Stopwatch.createStarted();
    LOG.debug("Starting new genotype generation");

    BitSet availableSeats = new BitSet(seats.size());
    availableSeats.set(0, seats.size());

    List<Chromosome<EnumGene<SeatCore>>> chromosomes = teams.stream()
        .sorted(Comparator.comparing(TeamCore::numMembers).reversed())
        .map(team -> chromosomeForTeamCore(team, availableSeats))
        .collect(Collectors.toList());

    chromosomes.add(new EmptySeatChromosome(seats, availableSeats));

    LOG.debug("Finished new genotype generation in {}ns", stopwatch.elapsed(TimeUnit.NANOSECONDS));
    return Genotype.of(chromosomes);
  }

  private TeamChromosome chromosomeForTeamCore(TeamCore team,
                                               BitSet availableSeats) {
    BitSet selected = TeamChromosome.selectSeatBlock(
        grid,
        seats,
        seatIndex,
        availableSeats,
        team.numMembers()
    );

    // Remove from available set
    availableSeats.andNot(selected);

    return new TeamChromosome(
        grid,
        seats,
        seatIndex,
        selected,
        team);
  }
}
