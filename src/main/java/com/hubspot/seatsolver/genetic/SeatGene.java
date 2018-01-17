package com.hubspot.seatsolver.genetic;

import java.util.List;

import com.google.common.base.Objects;
import com.hubspot.seatsolver.model.SeatIF;

import io.jenetics.Gene;
import io.jenetics.util.RandomRegistry;

public class SeatGene implements Gene<SeatIF, SeatGene> {
  private final List<? extends SeatIF> allSeats;
  private final SeatIF seat;

  public SeatGene(List<? extends SeatIF> allSeats, SeatIF seat) {
    this.allSeats = allSeats;
    this.seat = seat;
  }

  @Override
  public SeatIF getAllele() {
    return seat;
  }

  @Override
  public SeatGene newInstance() {
    int selection = RandomRegistry.getRandom().nextInt(allSeats.size());
    return newInstance(allSeats.get(selection));
  }

  @Override
  public SeatGene newInstance(SeatIF value) {
    return new SeatGene(allSeats, seat);
  }

  @Override
  public boolean isValid() {
    return false;
  }

  public SeatIF getSeatIF() {
    return getAllele();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("seat", seat)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SeatGene seatGene = (SeatGene) o;
    return java.util.Objects.equals(getSeatIF(), seatGene.getSeatIF());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(getSeatIF());
  }
}
