package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import com.hubspot.seatsolver.model.Seat;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;

public class EmptySeatChromosome extends AbstractSeatChromosome {
  private final List<Seat> allSeats;

  public EmptySeatChromosome(Collection<Seat> seats, List<Seat> allSeats) {
    super(
        ISeq.of(
            seats.stream()
                .map(seat -> EnumGene.of(allSeats.indexOf(seat), ISeq.of(allSeats)))
                .collect(Collectors.toList())
        )
    );

    this.allSeats = allSeats;
  }

  public EmptySeatChromosome(ISeq<? extends EnumGene<Seat>> genes, List<Seat> allSeats) {
    super(genes);
    this.allSeats = allSeats;
  }

  @Override
  public AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<Seat>> genes) {
    return new EmptySeatChromosome(genes, allSeats);
  }

  @Override
  public Chromosome<EnumGene<Seat>> newInstance(ISeq<EnumGene<Seat>> genes) {
    return newSeatChromosome(genes);
  }

  @Override
  public Chromosome<EnumGene<Seat>> newInstance() {
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
