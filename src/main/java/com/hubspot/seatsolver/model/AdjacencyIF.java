package com.hubspot.seatsolver.model;

import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableMap;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonSerialize(as = Adjacency.class)
@JsonDeserialize(as = Adjacency.class)
public interface AdjacencyIF {

  String id();
  String name();
  double weight();

  ImmutableMap<String, Double> WEIGHT_BY_TYPE = ImmutableMap.of(
      "levenshtein", 0.2
  );

  @Value.Auxiliary
  default double effectiveWeight() {
    return WEIGHT_BY_TYPE.getOrDefault(name(), 1.0) * weight();
  }
}
