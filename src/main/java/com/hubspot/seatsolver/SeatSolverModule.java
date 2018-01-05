package com.hubspot.seatsolver;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.hubspot.seatsolver.config.SeatSolverConfig;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;

import io.jenetics.util.ISeq;

public class SeatSolverModule extends AbstractModule {

  private final SeatSolverConfig config;

  public SeatSolverModule(SeatSolverConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(SeatSolverConfig.class).toInstance(config);

    List<Seat> seats = ImmutableList.copyOf(config.dataLoader().getSeats());
    List<Team> teams = ImmutableList.copyOf(config.dataLoader().getTeams());

    bind(new TypeLiteral<List<Seat>>(){}).toInstance(seats);
    bind(new TypeLiteral<List<Team>>(){}).toInstance(teams);
    bind(new TypeLiteral<ISeq<Seat>>(){}).toInstance(ISeq.of(seats));
    bind(new TypeLiteral<ISeq<Team>>(){}).toInstance(ISeq.of(teams));

    ObjectMapper objectMapper = new ObjectMapper()
        .registerModules(new GuavaModule(), new Jdk8Module());
    bind(ObjectMapper.class).toInstance(objectMapper);
  }

}
