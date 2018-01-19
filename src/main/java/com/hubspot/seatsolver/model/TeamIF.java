package com.hubspot.seatsolver.model;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonSerialize(as = TeamCore.class)
@JsonDeserialize(as = TeamCore.class)
public interface TeamIF extends TeamCore {
}
