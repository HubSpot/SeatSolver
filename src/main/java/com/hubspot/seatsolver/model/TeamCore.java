package com.hubspot.seatsolver.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TeamCore {
  String id();
  int numMembers();
  List<Adjacency> wantsAdjacent();
  Map<String, Double> effectiveWeightsByTeamId();
  Optional<SeatCore> wantsSeatProximity();
}
