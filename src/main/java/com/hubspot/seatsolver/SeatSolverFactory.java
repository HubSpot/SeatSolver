package com.hubspot.seatsolver;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.hubspot.seatsolver.config.SeatSolverConfig;

public final class SeatSolverFactory {

  private SeatSolverFactory() {
  }

  public static SeatSolver create(SeatSolverConfig config) {
    Injector i = Guice.createInjector(new SeatSolverModule(config));

    return i.getInstance(SeatSolver.class);
  }
}
