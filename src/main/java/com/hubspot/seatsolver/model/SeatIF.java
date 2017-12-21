package com.hubspot.seatsolver.model;

import org.apache.commons.csv.CSVRecord;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
public interface SeatIF extends PointBase {
  String id();

  static Seat fromCsvRecord(CSVRecord record) {
    return Seat.builder()
        .x(Integer.parseInt(record.get("x")))
        .y(Integer.parseInt(record.get("y")))
        .id(record.get("id"))
        .build();
  }
}
