package com.hubspot.seatsolver.model;

import java.util.List;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonSerialize(as = TeamIF.class)
@JsonDeserialize(as = TeamIF.class)
public interface TeamIF {
  String id();
  int numMembers();
  List<Adjacency> wantsAdjacent();
}
