package com.hubspot.seatsolver.model;

import org.apache.commons.csv.CSVRecord;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
@JsonDeserialize(as = SeatIF.class)
@JsonSerialize(as = SeatIF.class)
public interface SeatIF extends PointBase {
  String id();

  static SeatIF fromCsvRecord(CSVRecord record) {
    return Seat.builder()
        .x(Integer.parseInt(record.get("x")))
        .y(Integer.parseInt(record.get("y")))
        .id(record.get("id"))
        .build();
  }
}
