package com.hubspot.seatsolver.hubspot;

import org.immutables.value.Value.Derived;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hubspot.seatsolver.model.PointBase;
import com.hubspot.seatsolver.model.Seat;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonSerialize(as = HubspotSeat.class)
@JsonDeserialize(as = HubspotSeat.class)
public interface HubspotSeatIF extends PointBase {
  String name();
  int id();

  @Derived
  default Seat toSeat() {
    return Seat.builder().from(this).id(name()).build();
  }
}
