package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import com.hubspot.seatsolver.model.SeatCore;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;

public class EmptySeatChromosome extends AbstractSeatChromosome {
  private final ISeq<? extends SeatCore> allSeats;

  public EmptySeatChromosome(Collection<SeatCore> seats, ISeq<? extends SeatCore> allSeats) {
    super(
        ISeq.of(
            seats.stream()
                .map(seat -> EnumGene.of(allSeats.indexOf(seat), allSeats))
                .collect(Collectors.toList())
        )
    );

    this.allSeats = allSeats;
  }

  public EmptySeatChromosome(ISeq<? extends EnumGene<SeatCore>> genes, ISeq<? extends SeatCore> allSeats) {
    super(genes);
    this.allSeats = allSeats;
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
