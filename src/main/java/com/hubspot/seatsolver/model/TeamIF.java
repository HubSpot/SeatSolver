package com.hubspot.seatsolver.model;

import java.util.List;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
public interface TeamIF {
  String id();
  int numMembers();
  List<String> wantsAdjacent();
}
