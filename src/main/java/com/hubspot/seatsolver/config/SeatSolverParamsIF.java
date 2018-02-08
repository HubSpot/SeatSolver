package com.hubspot.seatsolver.config;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
public interface SeatSolverParamsIF {
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
