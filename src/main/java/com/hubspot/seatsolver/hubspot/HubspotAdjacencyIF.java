package com.hubspot.seatsolver.hubspot;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonSerialize(as = HubspotAdjacency.class)
@JsonDeserialize(as = HubspotAdjacency.class)
public interface HubspotAdjacencyIF {
  String type();
  String target();
}
