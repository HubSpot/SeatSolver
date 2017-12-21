package com.hubspot.seatsolver.hubspot;


import java.util.List;
import java.util.Map;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonDeserialize(as = HubspotData.class)
@JsonSerialize(as = HubspotData.class)
public interface HubspotDataIF {
  @JsonProperty("team_data")
  Map<String, List<String>> teamData();

  @JsonProperty("floor_data")
  Map<String, List<HubspotSeat>> floorData();
}
