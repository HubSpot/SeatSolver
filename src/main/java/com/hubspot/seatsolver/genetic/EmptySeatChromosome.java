package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.Collection;

import com.google.common.base.MoreObjects;
import com.hubspot.seatsolver.model.SeatCore;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;

public class EmptySeatChromosome extends AbstractSeatChromosome {
  private final ISeq<SeatCore> allSeats;

  public EmptySeatChromosome(Collection<SeatCore> seats, ISeq<SeatCore> allSeats) {
    super(buildSeq(seats, allSeats));
    this.allSeats = allSeats;
  }

  public EmptySeatChromosome(ISeq<? extends EnumGene<SeatCore>> genes, ISeq<SeatCore> allSeats) {
    super(genes);
    this.allSeats = allSeats;
  }

  private static ISeq<? extends EnumGene<SeatCore>> buildSeq(Collection<SeatCore> seats, ISeq<SeatCore> allSeats) {
    MSeq<EnumGene<SeatCore>> result = MSeq.ofLength(seats.size());
    int i = 0;
    for (SeatCore seat : seats) {
      result.set(i, EnumGene.of(allSeats.indexOf(seat), allSeats));
      i++;
    }
    return result.toISeq();
  }

  @Override
  public AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<SeatCore>> genes) {
    return new EmptySeatChromosome(genes, allSeats);
  }

  @Override
  public Chromosome<EnumGene<SeatCore>> newInstance(ISeq<EnumGene<SeatCore>> genes) {
    return newSeatChromosome(genes);
  }

  @Override
  public Chromosome<EnumGene<SeatCore>> newInstance() {
    return new EmptySeatChromosome(new ArrayList<>(), allSeats);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("_genes", _genes)
        .toString();
  }

  @Override
  public String getIdentifier() {
    return "EMPTY";
  }

}
