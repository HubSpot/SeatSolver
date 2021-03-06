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
@JsonDeserialize(as = Seat.class)
@JsonSerialize(as = Seat.class)
public interface SeatIF extends SeatCore {
  static SeatCore fromCsvRecord(CSVRecord record) {
    return Seat.builder()
        .x(Integer.parseInt(record.get("x")))
        .y(Integer.parseInt(record.get("y")))
        .id(record.get("id"))
        .build();
  }
}
