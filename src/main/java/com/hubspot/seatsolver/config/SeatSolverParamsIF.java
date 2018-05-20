package com.hubspot.seatsolver.config;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@Value.Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonSerialize(as = SeatSolverParams.class)
@JsonDeserialize(as = SeatSolverParams.class)
public interface SeatSolverParamsIF {
  @Value.Default
  default double nearSeatSwapProbability() {
    return 0.05;
  }

  @Value.Default
  default double intraTeamScoreWeight() {
    return 100.;
  }

  @Value.Default
  default double interTeamScoreWeight() {
    return 1.;
  }

  @Value.Default
  default double intraTeamSquarenessWeight() {
    return 10.;
  }

  @Value.Default
  default double intraTeamPercentile() {
    return -1;
  }

  @Value.Default
  default int maxAdjacentSeatDistance() {
    return 40;
  }
}
