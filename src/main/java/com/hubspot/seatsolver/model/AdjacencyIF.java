package com.hubspot.seatsolver.model;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
public interface AdjacencyIF {
  String id();
  double weight();
}
