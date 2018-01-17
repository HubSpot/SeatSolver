package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import com.hubspot.seatsolver.model.SeatIF;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;

public class EmptySeatChromosome extends AbstractSeatChromosome {
  private final ISeq<? extends SeatIF> allSeats;

  public EmptySeatChromosome(Collection<SeatIF> seats, ISeq<? extends SeatIF> allSeats) {
    super(
        ISeq.of(
            seats.stream()
                .map(seat -> EnumGene.of(allSeats.indexOf(seat), allSeats))
                .collect(Collectors.toList())
        )
    );

    this.allSeats = allSeats;
  }

  public EmptySeatChromosome(ISeq<? extends EnumGene<SeatIF>> genes, ISeq<? extends SeatIF> allSeats) {
    super(genes);
    this.allSeats = allSeats;
  }

  @Override
  public AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<SeatIF>> genes) {
    return new EmptySeatChromosome(genes, allSeats);
  }

  @Override
  public Chromosome<EnumGene<SeatIF>> newInstance(ISeq<EnumGene<SeatIF>> genes) {
    return newSeatChromosome(genes);
  }

  @Override
  public Chromosome<EnumGene<SeatIF>> newInstance() {
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
