package com.hubspot.seatsolver.genetic;

import java.util.List;

import com.google.common.base.Objects;
import com.hubspot.seatsolver.model.Seat;

import io.jenetics.Gene;
import io.jenetics.util.RandomRegistry;

public class SeatGene implements Gene<Seat, SeatGene> {
  private final List<Seat> allSeats;
  private final Seat seat;

  public SeatGene(List<Seat> allSeats, Seat seat) {
    this.allSeats = allSeats;
    this.seat = seat;
  }

  @Override
  public Seat getAllele() {
    return seat;
  }

  @Override
  public SeatGene newInstance() {
    int selection = RandomRegistry.getRandom().nextInt(allSeats.size());
    return newInstance(allSeats.get(selection));
  }

  @Override
  public SeatGene newInstance(Seat value) {
    return new SeatGene(allSeats, seat);
  }

  @Override
  public boolean isValid() {
    return false;
  }

  public Seat getSeat() {
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
    return java.util.Objects.equals(getSeat(), seatGene.getSeat());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(getSeat());
  }
}
