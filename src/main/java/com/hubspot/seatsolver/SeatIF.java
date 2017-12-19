package com.hubspot.seatsolver;

import org.apache.commons.csv.CSVRecord;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

@Immutable
@Style(
    typeAbstract = {"*IF"},
    typeImmutable = "*"
)
public interface SeatIF {
  String id();
  int x();
  int y();

  static Seat fromCsvRecord(CSVRecord record) {
    return Seat.builder()
        .x(Integer.parseInt(record.get("x")))
        .y(Integer.parseInt(record.get("y")))
        .id(record.get("id"))
        .build();
  }
}
