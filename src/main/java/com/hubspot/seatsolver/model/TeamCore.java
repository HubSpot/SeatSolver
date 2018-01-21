package com.hubspot.seatsolver.model;

import java.util.List;

public interface TeamCore {
  String id();
  int numMembers();
  List<Adjacency> wantsAdjacent();
}
