package com.hubspot.seatsolver;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.hubspot.seatsolver.config.DataLoader;
import com.hubspot.seatsolver.genetic.alter.SeatSwapMutator.SeatSwapMutatorFactory;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;

import io.jenetics.util.ISeq;

public class SeatSolverModule extends AbstractModule {

  private final List<Seat> seats;
  private final List<Team> teams;

  public SeatSolverModule(DataLoader dataLoader) {
    this.seats = ImmutableList.copyOf(dataLoader.getSeats());
    this.teams = ImmutableList.copyOf(dataLoader.getTeams());
  }

  @Override
  protected void configure() {
    bind(new TypeLiteral<List<Seat>>(){}).toInstance(seats);
    bind(new TypeLiteral<List<Team>>(){}).toInstance(teams);
    bind(new TypeLiteral<ISeq<Seat>>(){}).toInstance(ISeq.of(seats));
    bind(new TypeLiteral<ISeq<Team>>(){}).toInstance(ISeq.of(teams));

    ObjectMapper objectMapper = new ObjectMapper()
        .registerModules(new GuavaModule(), new Jdk8Module());
    bind(ObjectMapper.class).toInstance(objectMapper);

    new FactoryModuleBuilder()
        .build(SeatSwapMutatorFactory.class)
        .configure(binder());
  }

}
