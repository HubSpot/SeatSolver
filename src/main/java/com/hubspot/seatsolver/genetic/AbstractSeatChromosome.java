package com.hubspot.seatsolver.genetic;

import com.hubspot.seatsolver.model.Seat;

import io.jenetics.AbstractChromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;

public abstract class AbstractSeatChromosome extends AbstractChromosome<EnumGene<Seat>> {

  public AbstractSeatChromosome(ISeq<? extends EnumGene<Seat>> genes) {
    super(genes);
  }

  public abstract String getIdentifier();

  public abstract AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<Seat>> genes);
}
