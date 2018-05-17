package com.hubspot.seatsolver.model;


import java.util.BitSet;
import java.util.List;

public class TeamSelectionResult {
  private final BitSet usedSeatIndexes;
  private final List<SeatCore> usedSeats;

  public TeamSelectionResult(BitSet usedSeatIndexes,
                             List<SeatCore> usedSeats) {
    this.usedSeatIndexes = usedSeatIndexes;
    this.usedSeats = usedSeats;
  }

  public BitSet getUsedSeatIndexes() {
    return usedSeatIndexes;
  }

  public List<SeatCore> getUsedSeats() {
    return usedSeats;
  }
}
