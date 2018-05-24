package com.hubspot.seatsolver.genetic;

import java.util.BitSet;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.hubspot.seatsolver.model.SeatCore;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;

public class EmptySeatChromosome extends AbstractSeatChromosome {
  private final ISeq<SeatCore> allSeats;

  public EmptySeatChromosome(ISeq<SeatCore> allSeats,
                             BitSet availableSeatIndices) {
    super(buildSeq(allSeats, availableSeatIndices));
    this.allSeats = allSeats;
  }

  private EmptySeatChromosome(ISeq<? extends EnumGene<SeatCore>> genes,
                              ISeq<SeatCore> allSeats) {
    super(genes);
    this.allSeats = allSeats;
  }

  public static Optional<EmptySeatChromosome> create(ISeq<SeatCore> allSeats,
                                BitSet availableSeatIndices) {
    ISeq<EnumGene<SeatCore>> genes = buildSeq(allSeats, availableSeatIndices);
    if (genes.size() == 0) {
      return Optional.empty();
    }

    return Optional.of(new EmptySeatChromosome(genes, allSeats));
  }

  private static ISeq<EnumGene<SeatCore>> buildSeq(ISeq<SeatCore> allSeats,
                                                   BitSet availableSeatIndices) {
    MSeq<EnumGene<SeatCore>> result = MSeq.ofLength(availableSeatIndices.cardinality());

    int idx = 0;
    for (int i = availableSeatIndices.nextSetBit(0); i >= 0; i = availableSeatIndices.nextSetBit(i + 1)) {
      if (i == Integer.MAX_VALUE) {
        break;
      }
      int current = idx++;
      result.set(current, EnumGene.<SeatCore>of(i, allSeats));
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
    return new EmptySeatChromosome(allSeats, new BitSet(0));
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
