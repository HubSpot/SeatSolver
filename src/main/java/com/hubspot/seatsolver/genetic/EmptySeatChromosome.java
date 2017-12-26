package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import com.hubspot.seatsolver.model.Seat;

import io.jenetics.AbstractChromosome;
import io.jenetics.Chromosome;
import io.jenetics.util.ISeq;

public class EmptySeatChromosome extends AbstractChromosome<SeatGene> {
  private final List<Seat> allSeats;

  public EmptySeatChromosome(Collection<Seat> seats, List<Seat> allSeats) {
    super(
        ISeq.of(
            seats.stream().map(seat -> new SeatGene(allSeats, seat)).collect(Collectors.toList())
        )
    );

    this.allSeats = allSeats;
  }

  public EmptySeatChromosome(ISeq<? extends SeatGene> genes, List<Seat> allSeats) {
    super(genes);
    this.allSeats = allSeats;
  }

  @Override
  public Chromosome<SeatGene> newInstance(ISeq<SeatGene> genes) {
    return new EmptySeatChromosome(genes, allSeats);
  }

  @Override
  public Chromosome<SeatGene> newInstance() {
    return new EmptySeatChromosome(new ArrayList<>(), allSeats);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("_genes", _genes)
        .toString();
  }
}
