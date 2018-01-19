package com.hubspot.seatsolver.genetic;

import com.hubspot.seatsolver.model.SeatCore;

import io.jenetics.AbstractChromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;

public abstract class AbstractSeatChromosome extends AbstractChromosome<EnumGene<SeatCore>> {

  public AbstractSeatChromosome(ISeq<? extends EnumGene<SeatCore>> genes) {
    super(genes);
  }

  public abstract String getIdentifier();

  public abstract AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<SeatCore>> genes);
}
