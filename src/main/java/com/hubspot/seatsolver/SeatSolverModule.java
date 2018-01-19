package com.hubspot.seatsolver;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.hubspot.seatsolver.config.SeatSolverConfig;
import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.model.TeamCore;

import io.jenetics.util.ISeq;

public class SeatSolverModule extends AbstractModule {

  private final SeatSolverConfig config;

  public SeatSolverModule(SeatSolverConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(SeatSolverConfig.class).toInstance(config);

    List<SeatCore> seats = ImmutableList.copyOf(config.dataLoader().getSeats());
    List<TeamCore> teams = ImmutableList.copyOf(config.dataLoader().getTeams());

    bind(new TypeLiteral<List<SeatCore>>(){}).toInstance(seats);
    bind(new TypeLiteral<List<TeamCore>>(){}).toInstance(teams);
    bind(new TypeLiteral<ISeq<SeatCore>>(){}).toInstance(ISeq.of(seats));
    bind(new TypeLiteral<ISeq<TeamCore>>(){}).toInstance(ISeq.of(teams));

    ObjectMapper objectMapper = new ObjectMapper()
        .registerModules(new GuavaModule(), new Jdk8Module());
    bind(ObjectMapper.class).toInstance(objectMapper);
  }

}
