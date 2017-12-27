package com.hubspot.seatsolver.model;

import java.util.List;
import java.util.Optional;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonSerialize(as = TeamAssignment.class)
@JsonDeserialize(as = TeamAssignment.class)
public interface TeamAssignmentIF {
  Optional<Team> getTeam();
  List<Seat> getSeats();
}
