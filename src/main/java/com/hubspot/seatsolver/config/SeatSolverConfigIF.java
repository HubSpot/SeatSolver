package com.hubspot.seatsolver.config;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.immutables.value.Value.Default;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.hubspot.seatsolver.model.SeatIF;

import io.jenetics.Alterer;
import io.jenetics.EnumGene;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
public interface SeatSolverConfigIF {

  DataLoader dataLoader();

  List<Alterer<EnumGene<SeatIF>, Double>> alterers();

  Optional<Integer> populationFilterParallelism();

  @Default
  default Executor executor() {
    return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  }
}
