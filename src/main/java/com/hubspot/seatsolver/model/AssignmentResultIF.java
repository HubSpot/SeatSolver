package com.hubspot.seatsolver.model;

import java.util.List;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@Value.Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonSerialize(as = AssignmentResult.class)
@JsonDeserialize(as = AssignmentResult.class)
public interface AssignmentResultIF {
  List<TeamAssignment> teamAssignments();
  double fitness();
}
