package com.hubspot.seatsolver.genetic;

import com.hubspot.seatsolver.model.SeatIF;

import io.jenetics.AbstractChromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;

public abstract class AbstractSeatChromosome extends AbstractChromosome<EnumGene<SeatIF>> {

  public AbstractSeatChromosome(ISeq<? extends EnumGene<SeatIF>> genes) {
    super(genes);
  }

  public abstract String getIdentifier();

  public abstract AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<SeatIF>> genes);
}
